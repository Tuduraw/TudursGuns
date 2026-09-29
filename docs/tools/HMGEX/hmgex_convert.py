#!/usr/bin/env python3
"""Hand Made Guns & Vehicle : EX (1.7.10) pack -> Tudur's Guns converter.

Reads an HMG EX pack (a folder, or a .zip of one) and writes:

  <out>/tudursvehiclemod-addons/<pack>/   - drop into the game folder's tudursvehiclemod-addons/
      assets/<ns>/weapons/*.txt             weapon files (Tudur's Vehicle Mod format)
      assets/<ns>/models/obj/*.obj          models, re-axised and scaled to blocks (bullets only scaled)
      assets/<ns>/textures/vehicle/...      model textures, inventory icons, scope overlays
      assets/<ns>/sounds/*.ogg              sounds shipped in the pack (if any)
      data/<ns>/handheld/*.json             weapons
      data/<ns>/attachment/*.json           attachments (scopes, suppressors, grips, ...)
      data/<ns>/ammo/*.json                 magazines
      data/<ns>/gun_recipe/*.json           recipes of the gun crafting table
  <out>/datapacks/<pack>/                 - (--datapack-recipes only) the same recipes for the vanilla
                                            crafting table, as a data pack for a world's datapacks/
  <out>/conversion_report.md              - what was converted, approximated or dropped, per file

Only the Python 3.8+ standard library is used. See README.md next to this script.
"""

import argparse
import json
import os
import re
import shutil
import sys
import tempfile
import zipfile

# --------------------------------------------------------------------------- defaults

#: HMG EX's own built-in sounds (they live in the HMG jar, not in packs) -> Tudur's Guns sounds.
#: Override or extend with --sound-map.
DEFAULT_SOUND_MAP = {
    "fire": "tg_sample_rifle",
    "firerifle": "tg_rifle_shot",
    "firehg": "tg_revolver_shot",
    "heavyrifle": "tg_sniper_shot",
    "supu": "tg_suppressed_shot",
    "reload": "tg_reload_magazine",
    "cooking": "tg_reload_bolt",
    "a_cock": "tg_reload_bolt",
    "null": None,
}

#: 1.7.10 item/block names that changed (or became tags) in 1.21.
LEGACY_ITEMS = {
    "planks": "#minecraft:planks", "log": "#minecraft:logs", "log2": "#minecraft:logs",
    "wool": "#minecraft:wool", "sapling": "#minecraft:saplings", "leaves": "#minecraft:leaves",
    "leaves2": "#minecraft:leaves", "wooden_slab": "#minecraft:wooden_slabs", "carpet": "#minecraft:wool_carpets",
    "stone_slab": "minecraft:smooth_stone_slab", "stonebrick": "minecraft:stone_bricks",
    "stained_hardened_clay": "minecraft:terracotta", "hardened_clay": "minecraft:terracotta",
    "fence": "minecraft:oak_fence", "fence_gate": "minecraft:oak_fence_gate",
    "wooden_door": "minecraft:oak_door", "trapdoor": "minecraft:oak_trapdoor", "sign": "minecraft:oak_sign",
    "boat": "minecraft:oak_boat", "wooden_button": "minecraft:oak_button",
    "wooden_pressure_plate": "minecraft:oak_pressure_plate", "reeds": "minecraft:sugar_cane",
    "netherbrick": "minecraft:nether_brick", "grass": "minecraft:grass_block", "melon": "minecraft:melon_slice",
    "speckled_melon": "minecraft:glistering_melon_slice", "fish": "minecraft:cod", "cooked_fished": "minecraft:cooked_cod",
    "red_flower": "minecraft:poppy", "yellow_flower": "minecraft:dandelion", "web": "minecraft:cobweb",
    "snow_layer": "minecraft:snow", "snow": "minecraft:snow_block", "dye": "minecraft:black_dye",
    "golden_rail": "minecraft:powered_rail", "noteblock": "minecraft:note_block", "mob_spawner": "minecraft:spawner",
    "lit_pumpkin": "minecraft:jack_o_lantern", "stone_stairs": "minecraft:cobblestone_stairs",
    "wooden_axe": "minecraft:wooden_axe", "brick_block": "minecraft:bricks", "nether_brick": "minecraft:nether_bricks",
    "quartz_block": "minecraft:quartz_block", "stained_glass": "minecraft:white_stained_glass",
    "stained_glass_pane": "minecraft:white_stained_glass_pane", "skull": "minecraft:skeleton_skull",
}

#: Gun registration keywords (HMG "登録").
REGISTRATIONS = {"hg", "ar", "sg", "sgf", "sr", "amr", "lmg", "rl", "gl", "bow", "unified_guns"}

#: Keys that belong to the part being defined (AddParts / AddChildParts ... BackParts).
PART_KEYS = {
    "addpartsrotationcenterandrotationamount", "renderonnormal", "addpartsrotationdefoffset",
    "addpartsonadsoffsetandrotation", "addpartsonrecoiloffsetandrotation", "addpartsoncockoffsetandrotation",
    "addpartsonreloadoffsetandrotation", "addpartsrenderasbulletinf", "addrecoilmotionkey", "addcockmotionkey",
    "addreloadmotionkey", "addbulletpositions", "addyawinfokey", "addpitchinfokey",
    "needdraw_current_magazine_id_list", "needdraw_select_magazine_id_list",
}

#: Bare-word part flags.
PART_FLAGS = {
    "isbullet", "attachpart", "scope", "dot", "sight", "grip", "gripcover", "sword", "swordbase", "undersg",
    "undergl", "muzzlepart", "light", "lasersight", "gripbase", "undergunbase", "overbarrelbase", "muzzulebase",
    "sightbase", "turretbase", "carryinghandle", "underonly", "underonly_not",
}

#: Attachment registration keywords -> our kind.
ATTACHMENT_KINDS = {
    "model_sight": "sight", "reddot": "dot", "scope": "scope", "suppressor": "suppressor",
    "laser": "laser", "model_laser": "laser", "light": "light", "model_light": "light",
    "grip": "grip", "model_grip": "grip", "magazine": "magazine", "custommagazine": "magazine",
    "simplematerial": "material",
}

#: Why keys aren't carried over (for the report): (reason, keys). None = used silently.
_UNSUPPORTED_GROUPS = [
    ("弾のノックバックは前提MODの弾に設定項目がないため未対応", ["knockback"]),
    ("跳弾は前提MODの Bound が DelayFuse 付きの弾でしか働かないため、通常弾では再現できない",
     ["canbounce", "bouncerate", "bouncelimit"]),
    ("可変拡散(移動や連射で広がる拡散)は未対応。基本の拡散のみ変換", ["bulletspreaddiffusion"]),
    ("クロスヘアの表示切替は未対応", ["rendercross", "renderhmgcross"]),
    ("スコープの暗視は未対応(暗視ゴーグルの防具で代替)", ["nightvision"]),
    ("ズーム時の描画切替は未対応", ["zoomrendertype", "zoomrendertypetxture"]),
    ("マズルフラッシュの画像の変更は未対応(色付きの粒子で表示)", ["customflash"]),
    ("JavaScript のスクリプトは未対応", ["rendescript", "gunscript"]),
    ("GVC(ゲリラ・連邦軍)との連携は対象外", ["guerrila_cant_use_this", "dont_be_inside_root_chest",
                                          "soldier_cant_storage_this", "class"]),
    ("依託射撃・設置(タレット)は未対応。前提MODの固定設置物で代替可能",
     ["canfix", "needfix", "fixasentity", "onentity_rotationyawpoint", "onentity_rotationpitchpoint",
      "onentity_barrelpoint", "onentity_yoffset", "onentity_playerposoffset", "restrictturretmovespeed",
      "turretanglelimit", "turretboxsize", "onturretscale", "turrethp"]),
    ("クリエイティブタブは Tudur's Guns のタブにまとめる", ["tabname"]),
    ("銃は1個ずつ(スタック不可)", ["maxstacksize"]),
    ("使い切りの銃は未対応(HMG側でも機能不全)", ["isoneuse"]),
    ("非推奨項目のため省略", ["reloadmotion"]),
    ("マガジン消費速度は未対応", ["magazineitemcount"]),
    ("コック時の左手連動・全体の持ち上げは未対応(パーツのモーションで代替)", ["cockedlefthand", "allcocked"]),
    ("ブロックへのロックオンは未対応", ["canlockblock"]),
    ("アンダーバレルへの銃の取り付けは未対応(擲弾発射器はアタッチメントで作れる)",
     ["undergunoffset", "undergunrotation", "onundergunoffset", "onundergunrotation", "use_undergun'smodel",
      "underbarrelweapon"]),
    ("RightType は未対応", ["righttype"]),
    ("一人称・三人称の位置と大きさは、模型の長さとサンプル銃の値から自動計算(要調整)",
     ["modelequipped", "thirdmodelequipped", "modelequippedrotation", "inworldscale"]),
    ("照準(ADS)位置は模型から推定(要調整)",
     ["modelhigh", "modelwidthx", "modelwidthz", "adsoffsetx", "adsoffsety", "adsoffsetz",
      "modelrotationx", "modelrotationy", "modelrotationz"]),
    ("腕の位置は Tudur's Guns の既定を使用(要調整)",
     ["modelarmrotationr", "modelarmoffsetr", "modelarmrotationl", "modelarmoffsetl", "armoffsetscale"]),
    ("銃ごとのスコープ画像は、既定のサイトがスコープの場合(Zoom が1より大きい)のみ変換", ["scopetexture"]),
    ("発射レートの切替は未対応(最初の値のみ)", ["rates"]),
    ("複数のマガジンは最初の1種類のみ", ["multimagazine"]),
    ("ノーマルレンダー(Mat22/25/31/32)の動きは未対応。パーツレンダー(AddParts)の動きのみ変換",
     ["reloadmat31", "mat31point", "mat31rotation", "mat32point", "mat32rotation", "mat22", "mat22point",
      "mat22rotation", "mat25point", "mat25rotation"]),
    ("弾の加速は未対応", ["acceleration"]),
    ("リロード音の音量は未対応(常に 1.0)", ["gunsoundreloadlv"]),
    ("スプリント時の回転中心は Tudur's Guns の既定を使用", ["sprintingpoint"]),
]
UNSUPPORTED_REASONS = {key: reason for reason, keys in _UNSUPPORTED_GROUPS for key in keys}

#: Gun keys the converter uses (the rest are reported as unknown).
USED_GUN_KEYS = {
    "name", "bulletpower", "bulletspeed", "explosion", "blockdestory", "bulletgravity",
    "induction_precision", "bulletfuse", "bulletspread", "ads_spread_coefficient", "recoil", "recoil_sneaking",
    "reloadtime", "remainingbullet", "attacking", "motion", "zoom", "cycle", "bursts", "texture", "gunsound",
    "soundspeed", "gunsoundlv", "gunsoundreload", "gunsoundcooking", "magazine",
    "objmodel", "objtexture", "modelscala", "modelarm", "sprintingrotation", "cockingtime",
    "perfireround", "muzzlejump", "attachrestriction", "allowattach", "sightsetpoint", "sightattachrotation",
    "lightsetpoint", "lightsetangle", "muzzlesetpoint", "gripsetpoint", "gripsetangle", "canlock", "canlockentity", "guntype",
    "automatic", "canobj", "bulletnameall", "bulletnamenormal",
    "cartridge", "cartridgetype", "cartcount", "dropcartridgeendcocked", "bulletnamecart",
    "dropmagazine", "magtype", "magcount", "bulletnamemag", "muzzleflash",
}

#: HMG CartridgeType / MagType -> Tudur's Guns built-in thrown-out model.
EJECT_TYPES = {1: "rifle", 2: "pistol", 3: "shotgun", 4: "large", 5: "magazine"}


# --------------------------------------------------------------------------- small helpers

def read_text(path):
    raw = open(path, "rb").read()
    for encoding in ("utf-8-sig", "cp932"):
        try:
            return raw.decode(encoding)
        except UnicodeDecodeError:
            pass
    return raw.decode("latin-1")


def parse_lines(text):
    """(line number, key, [values]) for every meaningful line of an HMG txt file."""
    for number, line in enumerate(text.splitlines(), 1):
        line = line.strip().lstrip("﻿")
        if not line or line.startswith("//") or line.startswith("#"):
            continue
        comment = line.find("//")
        if comment >= 0:
            line = line[:comment].strip()
        parts = [p.strip() for p in line.split(",")]
        key = parts[0]
        if not key:
            continue
        yield number, key, parts[1:]


def num(value, default=0.0):
    try:
        return float(re.sub(r"[fFdD]$", "", value.strip()))
    except (ValueError, AttributeError):
        return default


def boolean(value):
    return str(value).strip().lower() in ("true", "1", "yes")


def slug(name):
    s = re.sub(r"[^a-z0-9_]+", "_", name.strip().lower()).strip("_")
    return s or "unnamed"


def round_list(values, digits=4):
    return [round(v, digits) + 0.0 for v in values]


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def write_text(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


# --------------------------------------------------------------------------- the pack

class Pack:
    """An HMG EX pack folder: guns/, attachment/, bullets/, addpackrecipe/, addTab/, assets/<domain>/."""

    def __init__(self, root):
        self.root = root
        self.name = os.path.basename(os.path.normpath(root))

    def folder(self, name):
        for entry in os.listdir(self.root):
            if entry.lower() == name.lower() and os.path.isdir(os.path.join(self.root, entry)):
                return os.path.join(self.root, entry)
        return None

    def txt_files(self, folder_name):
        folder = self.folder(folder_name)
        if not folder:
            return []
        return sorted(os.path.join(folder, f) for f in os.listdir(folder) if f.lower().endswith(".txt"))

    def asset_dirs(self):
        assets = self.folder("assets")
        if not assets:
            return []
        return [os.path.join(assets, d) for d in sorted(os.listdir(assets)) if os.path.isdir(os.path.join(assets, d))]

    def find_asset(self, *relative_options):
        """First existing file under any assets/<domain>/ matching one of the relative paths
        (case-insensitive, 'texture' and 'textures' both accepted)."""
        for domain in self.asset_dirs():
            for relative in relative_options:
                for variant in (relative, relative.replace("textures/", "texture/", 1)):
                    found = find_case_insensitive(domain, variant)
                    if found:
                        return found
        return None


def find_case_insensitive(base, relative):
    current = base
    for piece in relative.replace("\\", "/").split("/"):
        if not os.path.isdir(current):
            return None
        match = None
        for entry in os.listdir(current):
            if entry.lower() == piece.lower():
                match = entry
                break
        if match is None:
            return None
        current = os.path.join(current, match)
    return current if os.path.isfile(current) else None


def locate_pack_root(path, workdir):
    """A folder, or a zip (extracted into workdir); descends into a single wrapping folder."""
    if os.path.isfile(path) and path.lower().endswith(".zip"):
        target = os.path.join(workdir, "pack")
        with zipfile.ZipFile(path) as archive:
            for info in archive.infolist():
                name = info.filename
                if not (info.flag_bits & 0x800):
                    try:
                        name = name.encode("cp437").decode("cp932")
                    except (UnicodeEncodeError, UnicodeDecodeError):
                        pass
                destination = os.path.join(target, name)
                if info.is_dir():
                    os.makedirs(destination, exist_ok=True)
                    continue
                os.makedirs(os.path.dirname(destination), exist_ok=True)
                with archive.open(info) as source, open(destination, "wb") as dest:
                    shutil.copyfileobj(source, dest)
        path = target
    markers = {"guns", "attachment", "bullets", "addpackrecipe", "assets", "addtab"}
    current = path
    for _ in range(4):
        entries = [e for e in os.listdir(current) if not e.startswith(".") and e != "__MACOSX"]
        if any(e.lower() in markers for e in entries):
            return current
        folders = [e for e in entries if os.path.isdir(os.path.join(current, e))]
        if len(folders) != 1:
            break
        current = os.path.join(current, folders[0])
    raise SystemExit("HMG のパックフォルダが見つかりません(guns/ や assets/ を含むフォルダを指定してください): " + path)


# --------------------------------------------------------------------------- gun files

class Props:
    """key -> [values] of an HMG txt file, read with defaults."""

    def __init__(self, path):
        self.path = path
        self.file = os.path.basename(path)
        self.props = {}
        self.internal_name = None

    def get(self, key, index=0, default=None):
        values = self.props.get(key.lower())
        if not values or index >= len(values) or values[index] == "":
            return default
        return values[index]

    def f(self, key, default=0.0):
        value = self.get(key)
        return default if value is None else num(value, default)

    def b(self, key, default=False):
        value = self.get(key)
        return default if value is None else boolean(value)

    def has(self, key):
        return key.lower() in self.props


class Part:
    def __init__(self, name, parent):
        self.name = name
        self.parent = parent
        self.pivot = (0.0, 0.0, 0.0)
        self.def_offset = None
        self.ads = None
        self.recoil = None
        self.cock = None
        self.reload = None
        self.bullet_inf = None
        self.recoil_keys = []
        self.cock_keys = []
        self.reload_keys = []
        self.bullet_positions = []
        self.flags = set()
        self.unsupported = set()


class Gun(Props):
    def __init__(self, path):
        super().__init__(path)
        self.registration = None
        self.all_parts = []
        self.warnings = []


def parse_motion_keys(values):
    """[start, 6 floats, end, 6 floats] -> ((start, pose), (end, pose)), or None (e.g. the visibility form)."""
    numbers = [num(v) for v in values if v != ""]
    if len(numbers) != 14:
        return None
    return (numbers[0], numbers[1:7]), (numbers[7], numbers[8:14])


def six(values):
    numbers = [num(v) for v in values[:6]] + [0.0] * 6
    return numbers[:6]


def parse_gun(path):
    gun = Gun(path)
    stack = []
    current = None
    for number, key, values in parse_lines(read_text(path)):
        lower = key.lower()
        if lower == "addparts":
            current = Part(values[0] if values else "part", None)
            gun.all_parts.append(current)
            stack = [current]
            continue
        if lower == "addchildparts":
            if current is None:
                gun.warnings.append(f"{number}行目: AddChildParts の前に AddParts がありません")
                continue
            child = Part(values[0] if values else "part", current)
            gun.all_parts.append(child)
            stack.append(child)
            current = child
            continue
        if lower == "backparts":
            if stack:
                stack.pop()
            current = stack[-1] if stack else None
            continue
        if lower in PART_KEYS or (lower in PART_FLAGS and not values):
            if current is None:
                gun.warnings.append(f"{number}行目: パーツの外に {key} があります(無視)")
                continue
            apply_part_key(current, lower, values)
            continue
        if lower in REGISTRATIONS:
            gun.registration = lower
            gun.internal_name = ",".join(values).strip() or os.path.splitext(gun.file)[0]
            continue
        if lower == "blletspread":
            gun.warnings.append(f"{number}行目: BlletSpread は BulletSpread の誤記とみなしました")
            lower = "bulletspread"
        gun.props[lower] = values
    if gun.registration is None:
        gun.registration = "unified_guns"
        gun.internal_name = os.path.splitext(gun.file)[0]
        gun.warnings.append("登録行(HG,xxx など)がないため、ファイル名で登録しました")
    return gun


def apply_part_key(part, key, values):
    if key == "addpartsrotationcenterandrotationamount":
        v = six(values)
        part.pivot = tuple(v[:3])
    elif key == "renderonnormal":
        part.flags.add("renderonnormal")
    elif key == "addpartsrotationdefoffset":
        part.def_offset = six(values)
    elif key == "addpartsonadsoffsetandrotation":
        part.ads = six(values)
    elif key == "addpartsonrecoiloffsetandrotation":
        part.recoil = six(values)
    elif key == "addpartsoncockoffsetandrotation":
        part.cock = six(values)
    elif key == "addpartsonreloadoffsetandrotation":
        part.reload = six(values)
    elif key == "addpartsrenderasbulletinf":
        part.bullet_inf = six(values)
    elif key in ("addrecoilmotionkey", "addcockmotionkey", "addreloadmotionkey", "addbulletpositions"):
        keys = parse_motion_keys(values)
        if keys is None:
            part.unsupported.add(key + "(描画可否の形式)")
            return
        {"addrecoilmotionkey": part.recoil_keys, "addcockmotionkey": part.cock_keys,
         "addreloadmotionkey": part.reload_keys, "addbulletpositions": part.bullet_positions}[key].append(keys)
    elif key in PART_FLAGS:
        part.flags.add(key)
    else:
        part.unsupported.add(key)


# --------------------------------------------------------------------------- attachments

class Attachment(Props):
    def __init__(self, path):
        super().__init__(path)
        self.kind = None


def parse_attachment(path):
    att = Attachment(path)
    for _, key, values in parse_lines(read_text(path)):
        lower = key.lower()
        if lower in ATTACHMENT_KINDS:
            att.kind = ATTACHMENT_KINDS[lower]
            att.internal_name = ",".join(values).strip() or os.path.splitext(att.file)[0]
        else:
            att.props[lower] = values
    return att


def parse_bullet_type(path):
    """bullets/*.txt: a bullet/cartridge/magazine model type (ObjModel, ObjTexture, Objscale, Name)."""
    props = {}
    name = None
    for _, key, values in parse_lines(read_text(path)):
        if key.lower() == "name":
            name = ",".join(values).strip()
        else:
            props[key.lower()] = values
    return name or os.path.splitext(os.path.basename(path))[0], props


# --------------------------------------------------------------------------- OBJ conversion

class Frame:
    """HMG model space -> Tudur's Guns model space: HMG models face +Z, ours -Z (a turn of 180
    degrees about Y), scaled to blocks and moved so the origin sits where our aiming expects it."""

    def __init__(self, origin, scale, turn=True):
        self.origin = origin
        self.scale = scale
        self.sign = -1.0 if turn else 1.0  # x and z; turn=False only centres and scales (bullet models)

    def point(self, p):
        ox, oy, oz = self.origin
        return (self.sign * (p[0] - ox) * self.scale, (p[1] - oy) * self.scale, self.sign * (p[2] - oz) * self.scale)

    def vector(self, v):
        return (self.sign * v[0] * self.scale, v[1] * self.scale, self.sign * v[2] * self.scale)

    @staticmethod
    def rotation(r):
        return (-r[0], r[1], -r[2])


def read_obj(path):
    """An OBJ's vertex positions."""
    vertices = []
    for line in read_text(path).splitlines():
        t = line.split()
        if t and t[0] == "v" and len(t) >= 4:
            vertices.append(tuple(float(x) for x in t[1:4]))
    return vertices


def bounds(vertices):
    if not vertices:
        return (0, 0, 0), (0, 0, 0)
    mins = tuple(min(v[i] for v in vertices) for i in range(3))
    maxs = tuple(max(v[i] for v in vertices) for i in range(3))
    return mins, maxs


def write_converted_obj(source, destination, frame, drop_groups=()):
    """Copies an OBJ with vertices/normals transformed; groups in drop_groups are left out; material
    lines are dropped (Tudur's Vehicle Mod uses one texture per model)."""
    drop = {g.lower() for g in drop_groups}
    out = ["# converted from Hand Made Guns EX by hmgex_convert.py"]
    skipping = False
    for line in read_text(source).splitlines():
        t = line.split()
        if not t:
            continue
        head = t[0]
        if head in ("o", "g"):
            skipping = " ".join(t[1:]).lower() in drop
            out.append(line.strip())
        elif head == "v" and len(t) >= 4:
            x, y, z = frame.point(tuple(float(v) for v in t[1:4]))
            out.append(f"v {x:.6f} {y:.6f} {z:.6f}")
        elif head == "vn" and len(t) >= 4:
            nx, ny, nz = (float(v) for v in t[1:4])
            out.append(f"vn {frame.sign * nx:.6f} {ny:.6f} {frame.sign * nz:.6f}")
        elif head == "vt":
            out.append(line.strip())
        elif head == "f":
            if not skipping:
                out.append(line.strip())
        elif head in ("mtllib", "usemtl", "s"):
            continue
        else:
            out.append(line.strip())
    write_text(destination, "\n".join(out) + "\n")


# --------------------------------------------------------------------------- templates

#: Our own sample weapons' placement, per size class (handgun / long gun / launcher), measured on
#: models of the given length (blocks, along Z) - scaled to the converted model's length.
TEMPLATES = {
    "handgun": {
        "length": 0.4, "origin_from_muzzle": 0.78, "muzzle_offset": [0.0, -0.05, 0.6],
        "aim": {"eye_distance": 0.3, "scale": 0.9, "hip_translation": [0.22, -0.22, -0.45], "hip_rotation": [0, 5, 0],
                "right_arm": {"translation": [-0.0222, -0.407, -0.2094], "rotation": [-85, 90, 0], "scale": [1.1111] * 3},
                "left_arm": {"translation": [0.1111, -0.4292, -0.2761], "rotation": [-85, 90, 0], "scale": [1.1111] * 3}},
        "display": {"thirdperson_righthand": ([0.5, 0.45, 0.5], None, 1.1), "thirdperson_lefthand": ([0.5, 0.45, 0.5], None, 1.1),
                    "firstperson_righthand": ([0.55, 0.35, 0.4], None, 1.2), "firstperson_lefthand": ([0.45, 0.35, 0.4], None, 1.2),
                    "gui": ([0.45, 0.5, 0.5], [0, 90, 0], 1.6), "ground": ([0.5, 0.3, 0.5], None, 1.0),
                    "fixed": ([0.45, 0.5, 0.5], [0, 90, 0], 1.6)},
    },
    "long": {
        "length": 1.5, "origin_from_muzzle": 0.63, "muzzle_offset": [0.0, -0.05, 0.95],
        "aim": {"eye_distance": 0.14, "scale": 0.55, "hip_translation": [0.26, -0.3, -0.4], "hip_rotation": [0, 3, 0],
                "right_arm": {"translation": [-0.2182, -0.8455, -0.5591], "rotation": [-85, 90, 0], "scale": [1.8182] * 3},
                "left_arm": {"translation": [0.2545, -0.7727, -1.3591], "rotation": [-85, 90, 0], "scale": [1.8182] * 3}},
        "display": {"thirdperson_righthand": ([0.5, 0.4, 0.5], None, 0.8), "thirdperson_lefthand": ([0.5, 0.4, 0.5], None, 0.8),
                    "firstperson_righthand": ([0.55, 0.3, 0.3], None, 0.9), "firstperson_lefthand": ([0.45, 0.3, 0.3], None, 0.9),
                    "gui": ([0.5, 0.5, 0.45], [0, 90, 0], 0.6), "ground": ([0.5, 0.3, 0.5], None, 0.45),
                    "fixed": ([0.5, 0.5, 0.45], [0, 90, 0], 0.6)},
    },
    "launcher": {
        "length": 1.5, "origin_from_muzzle": 0.6, "muzzle_offset": [0.0, 0.0, 1.0],
        "aim": {"eye_distance": 0.15, "scale": 0.55, "hip_translation": [0.28, -0.22, -0.35], "hip_rotation": [0, 3, 0],
                "right_arm": {"translation": [-0.3282, -0.7691, -0.4182], "rotation": [-85, 90, 0], "scale": [1.8182] * 3},
                "left_arm": {"translation": [0.1445, -0.6964, -1.1818], "rotation": [-85, 90, 0], "scale": [1.8182] * 3}},
        "display": {"thirdperson_righthand": ([0.5, 0.5, 0.5], None, 0.7), "thirdperson_lefthand": ([0.5, 0.5, 0.5], None, 0.7),
                    "firstperson_righthand": ([0.55, 0.4, 0.3], None, 0.8), "firstperson_lefthand": ([0.45, 0.4, 0.3], None, 0.8),
                    "gui": ([0.5, 0.45, 0.5], [0, 90, 0], 0.55), "ground": ([0.5, 0.3, 0.5], None, 0.45),
                    "fixed": ([0.5, 0.45, 0.5], [0, 90, 0], 0.55)},
    },
}


def size_class(gun):
    if gun.registration in ("rl", "gl") or gun.f("guntype", 0) in (2, 3):
        return "launcher"
    if gun.registration == "hg":
        return "handgun"
    return "long"


# --------------------------------------------------------------------------- converter

class Converter:

    def __init__(self, pack, out, args):
        self.pack = pack
        self.out = out
        self.args = args
        self.ns = args.namespace or slug(pack.name)
        self.addon = os.path.join(out, "tudursvehiclemod-addons", pack.name)
        self.datapack = os.path.join(out, "datapacks", pack.name)
        self.assets = os.path.join(self.addon, "assets", self.ns)
        self.data = os.path.join(self.addon, "data", self.ns)
        self.sound_map = dict(DEFAULT_SOUND_MAP)
        if args.sound_map:
            with open(args.sound_map, encoding="utf-8") as f:
                self.sound_map.update({k.lower(): v for k, v in json.load(f).items()})
        self.pack_sounds = self.read_pack_sounds()
        self.report = []
        self.copied = set()
        self.item_ids = {}      # HMG internal name (lower) -> ("weapon"|"attachment"|"ammo", our id, rounds)
        self.attachments = []
        self.magazines = {}     # HMG magazine internal name (lower) -> our ammo id
        self.bullet_types = {}  # HMG bullet/cart/mag type name (lower) -> props
        self.bullets = {}       # HMG bullet type name (lower) -> converted bullet_<slug> model
        self.recipe_count = 0

    # ----------------------------------------------------------------- assets

    def read_pack_sounds(self):
        """event name (lower) -> (domain folder, sound path) from each assets/<domain>/sounds.json."""
        sounds = {}
        for domain in self.pack.asset_dirs():
            path = find_case_insensitive(domain, "sounds.json")
            if not path:
                continue
            try:
                data = json.loads(read_text(path))
            except ValueError:
                continue
            for event, entry in data.items():
                files = entry.get("sounds", []) if isinstance(entry, dict) else []
                if files:
                    first = files[0]["name"] if isinstance(files[0], dict) else files[0]
                    sounds[event.lower()] = (domain, first.split(":")[-1])
        return sounds

    def copy_asset(self, source, relative):
        destination = os.path.join(self.assets, relative)
        if destination not in self.copied:
            os.makedirs(os.path.dirname(destination), exist_ok=True)
            shutil.copyfile(source, destination)
            self.copied.add(destination)
        return f"{self.ns}:{relative.replace(os.sep, '/')}"

    def sound(self, name, notes):
        """An HMG sound name -> a Tudur's Guns sound name (copying the .ogg if the pack has it), or None."""
        if not name:
            return None
        event = re.sub(r"^[a-z0-9_]+[.:]", "", name.strip(), flags=re.I)
        key = event.lower()
        if key in self.pack_sounds:
            domain, sound_path = self.pack_sounds[key]
            source = find_case_insensitive(domain, "sounds/" + sound_path + ".ogg")
            if source:
                sound_name = slug(f"{self.ns}_{event}")
                self.copy_asset(source, f"sounds/{sound_name}.ogg")
                return sound_name
        if key in self.sound_map:
            return self.sound_map[key]
        notes.append(f"音 `{name}` は対応表にないため省略(--sound-map で指定可能)")
        return None

    def texture(self, name, notes, subfolder=""):
        """textures/model/<name> (or items/, misc/) -> our textures/vehicle/..."""
        if not name:
            return None
        file_name = name if name.lower().endswith(".png") else name + ".png"
        folders = {"": ["textures/model/"], "icons/": ["textures/items/"], "scopes/": ["textures/misc/", "textures/model/"]}[subfolder]
        source = self.pack.find_asset(*[folder + file_name for folder in folders])
        if not source:
            notes.append(f"テクスチャ `{file_name}` が見つかりません")
            return None
        return self.copy_asset(source, f"textures/vehicle/{subfolder}{slug(os.path.splitext(file_name)[0])}.png")

    # ----------------------------------------------------------------- guns

    def convert_gun(self, gun):
        notes = list(gun.warnings)
        converted, approximated, dropped = [], [], []
        gun_id = slug(gun.internal_name)
        weapon_name = f"{self.ns}_{gun_id}"
        display_name = gun.get("name") or gun.internal_name
        size = size_class(gun)
        template = TEMPLATES[size]

        # ---- model
        model_id = texture_id = None
        frame = None
        model_length = template["length"]
        sight = [0.0, 0.1, 0.0]
        drop_groups = [p.name for p in gun.all_parts if p.flags & {"turretbase", "underonly"}]
        model_name = gun.get("objmodel")
        if model_name:
            source = self.pack.find_asset("textures/model/" + model_name, "textures/model/" + model_name + ".obj")
            if source:
                vertices = read_obj(source)
                mins, maxs = bounds(vertices)
                length = maxs[2] - mins[2]
                height = maxs[1] - mins[1]
                scale = gun.f("modelscala", 1.0) * self.args.scale_factor
                model_length = max(0.05, length * scale)
                # Origin: the grip area - a fixed fraction back from the muzzle (HMG muzzles face +Z).
                origin = ((mins[0] + maxs[0]) / 2, mins[1] + 0.55 * height, maxs[2] - template["origin_from_muzzle"] * length)
                frame = Frame(origin, scale)
                write_converted_obj(source, os.path.join(self.assets, "models/obj", gun_id + ".obj"), frame, drop_groups)
                model_id = f"{self.ns}:models/obj/{gun_id}.obj"
                sight = estimate_sight(vertices, frame, model_length)
                converted.append("ObjModel / ModelScala → 模型(軸を反転し、ブロック単位に変換)")
                if drop_groups:
                    notes.append("設置時専用・アンダーバレル専用のパーツを模型から除外: " + ", ".join(drop_groups))
            else:
                notes.append(f"模型 `{model_name}` が見つかりません")
        texture_id = self.texture(gun.get("objtexture"), notes)

        # ---- weapon file
        launcher = size == "launcher"
        explosion = gun.f("explosion", 0.0)
        lines = [f"; Converted from Hand Made Guns EX pack \"{self.pack.name}\", {gun.file}, by hmgex_convert.py",
                 "", f"DisplayName = {display_name}"]
        can_lock = gun.b("canlock") or gun.b("canlockentity")
        if can_lock:
            lines.append("Type = ATMissile")
            lines.append("LockTime = 20")
            if gun.has("induction_precision"):
                lines.append(f"TurnRate = {gun.f('induction_precision', 1.0) * 5:.3g}")
            converted.append("Canlock → ATMissile(ロック時間は20tick固定。要調整)")
        elif launcher or explosion > 0:
            lines.append("Type = Rocket")
        else:
            lines.append("Type = MachineGun1")
        lines.append(f"Power = {gun.f('bulletpower', 4):g}")
        if explosion > 0:
            lines.append(f"Explosion = {explosion:g}")
            lines.append(f"ExplosionBlock = {explosion:g}" if gun.b("blockdestory") else "ExplosionBlock = 0")
        lines.append(f"Acceleration = {gun.f('bulletspeed', 4.0) * self.args.speed_scale:.4g}")
        lines.append(f"Gravity = {gun.f('bulletgravity', 0.0) * self.args.gravity_scale:.4g}")
        lines.append(f"Accuracy = {gun.f('bulletspread', 1.0) * self.args.spread_scale:.4g}")
        rounds = int(gun.f("remainingbullet", 1))
        lines.append(f"Round = {rounds}")
        lines.append(f"ReloadTime = {int(gun.f('reloadtime', 40))}")
        cycle = int(gun.f("cycle", 2 if gun.b("automatic") else 1))
        cocking_time = int(gun.f("cockingtime", 0))
        delay = max(1, cycle, cocking_time)
        lines.append(f"Delay = {delay}")
        if gun.f("bulletfuse") > 0:
            lines.append(f"TimeFuse = {int(gun.f('bulletfuse'))}")
        fire_sound = self.sound(gun.get("gunsound"), notes)
        suppressed_sound = self.sound(gun.get("gunsound", 1), notes)
        if fire_sound:
            lines.append(f"Sound = {fire_sound}")
            lines.append(f"SoundVolume = {gun.f('gunsoundlv', 2.0):g}")
            lines.append(f"SoundPitch = {gun.f('soundspeed', 1.0):g}")
            lines.append("SoundPitchRandom = 0.05")
        bullet_model = self.bullet_model(gun, notes)
        if bullet_model:
            lines.append(f"ModelBullet = {bullet_model}")
        write_text(os.path.join(self.assets, "weapons", weapon_name + ".txt"), "\n".join(lines) + "\n")
        converted.append("BulletPower / BulletSpeed / BulletGravity / BulletSpread / RemainingBullet / ReloadTime / "
                         "Cycle・CockingTime(→ Delay) / Explosion / BlockDestory / bulletFuse / GunSound / SoundSpeed / GunSoundLV → 武器ファイル")
        approximated.append(f"弾速×{self.args.speed_scale}、重力×{self.args.gravity_scale}、拡散×{self.args.spread_scale} で換算")

        # ---- definition
        definition = {"weapon": weapon_name, "display_name": display_name}
        if model_id:
            definition["model"] = model_id
        if texture_id:
            definition["texture"] = texture_id
        definition["projectile_item"] = "minecraft:fire_charge" if launcher else "minecraft:iron_nugget"
        self.ammo(gun, definition, notes, launcher, rounds)
        bursts = [int(num(v)) for v in gun.props.get("bursts", []) if v != ""]
        if bursts and bursts[0] > 1:
            definition["fire_mode"] = "burst"
            definition["burst_count"] = bursts[0]
            converted.append(f"Bursts → 3点射など(burst_count {bursts[0]})")
        elif (bursts and bursts[0] == -1) or gun.b("automatic"):
            definition["fire_mode"] = "auto"
        else:
            definition["fire_mode"] = "semi"
        if len(bursts) > 1:
            approximated.append("Bursts の2つ目以降(射撃モードの切替)は未対応。最初の設定のみ")
        muzzle = list(template["muzzle_offset"])
        muzzle[2] = round(min(1.6, max(0.4, muzzle[2] * model_length / template["length"])), 3)
        definition["muzzle_offset"] = muzzle
        reload_sound = self.sound(gun.get("gunsoundreload"), notes)
        if reload_sound:
            definition["reload_sound"] = reload_sound
        factor = min(8.0, max(0.2, template["length"] / model_length))
        definition["display"] = {context: transform(t, r, s * factor) for context, (t, r, s) in template["display"].items()}
        approximated.append(f"表示位置・大きさはサンプル銃の値を模型の長さ({model_length:.2f}ブロック)に合わせて計算。要調整")

        aim = {"sight_position": round_list(sight), "eye_distance": template["aim"]["eye_distance"],
               "scale": template["aim"]["scale"], "hip_translation": template["aim"]["hip_translation"],
               "hip_rotation": template["aim"]["hip_rotation"], "raise_ticks": 4}
        if gun.b("modelarm", True):
            aim["right_arm"] = template["aim"]["right_arm"]
            aim["left_arm"] = template["aim"]["left_arm"]
        zoom = gun.f("zoom", 1.0)
        scope_texture = gun.get("scopetexture")
        if scope_texture and zoom > 1.0:
            overlay = self.texture(scope_texture, notes, "scopes/")
            scope = {"min": round(zoom, 3), "max": round(zoom, 3), "default": round(zoom, 3), "step": 1.0}
            if overlay:
                scope["overlay"] = overlay
            aim["scope"] = scope
            converted.append("Zoom + ScopeTexture(既定サイト)→ 内蔵スコープ")
        elif zoom > 1.0:
            aim["zoom"] = round(zoom, 3)
            converted.append("Zoom(既定サイト)→ 構え時の拡大")
        if gun.has("sprintingrotation"):
            r = [num(v) for v in gun.props["sprintingrotation"][:3]] + [0, 0, 0]
            aim["sprint_rotation"] = round_list(Frame.rotation(r[:3]))
            approximated.append("SprintingRotation → スプリント時の回転(回転中心は Tudur's Guns の既定)")
        definition["aim"] = aim

        motion = gun.f("motion", 1.0)
        if abs(motion - 1.0) > 1e-6:
            definition["movement_speed"] = round(max(-1.0, motion - 1.0), 4)
            converted.append("Motion → movement_speed")
        recoil = gun.f("recoil", 0.0) * self.args.recoil_scale
        if recoil > 0:
            definition["recoil"] = round(recoil, 3)
            if gun.has("recoil_sneaking"):
                definition["recoil_sneaking"] = round(gun.f("recoil_sneaking") * self.args.recoil_scale, 3)
            converted.append("Recoil / Recoil_sneaking → recoil")
        if gun.has("ads_spread_coefficient"):
            definition["ads_spread_multiplier"] = round(gun.f("ads_spread_coefficient", 1.0), 4)
            converted.append("ADS_Spread_coefficient → ads_spread_multiplier(構えキーで構えたとき)")
        pellets = int(gun.f("perfireround", 1))
        if pellets > 1:
            definition["pellets"] = pellets
            converted.append("PerFireRound → pellets")
        if gun.f("attacking", 0.0) > 0:
            definition["melee_damage"] = round(gun.f("attacking"), 3)
            converted.append("Attacking → melee_damage")
        icon = self.texture(gun.get("texture"), notes, "icons/")
        if icon:
            definition["icon"] = icon
            converted.append("Texture → インベントリのアイコン")

        effects = self.effects(gun, cocking_time, notes, converted)
        if effects:
            definition["effects"] = effects

        # ---- parts / motions
        cock_sound = self.sound(gun.get("gunsoundcooking"), notes)
        animation = None
        if frame is not None:
            animation = self.animation(gun, frame, rounds, cocking_time, reload_ticks=int(gun.f("reloadtime", 40)),
                                       cock_sound=cock_sound, notes=notes, converted=converted, approximated=approximated)
        elif cock_sound:
            # No model to move, but the cocking sound still plays after each shot.
            delay = self.args.cock_delay if cocking_time > 0 else 0
            animation = {"sequences": {"fire": {"tracks": [], "sounds": [{"tick": delay, "sound": cock_sound}]}}}
        if animation:
            definition["animation"] = animation

        # ---- attachment slots
        slots = self.attachment_slots(gun, frame, suppressed_sound, notes)
        if slots:
            definition["attachments"] = slots

        write_json(os.path.join(self.data, "handheld", gun_id + ".json"), definition)
        self.item_ids[gun.internal_name.lower()] = ("weapon", f"{self.ns}:{gun_id}", rounds)

        by_reason = {}
        for key in sorted(gun.props):
            if key in USED_GUN_KEYS:
                continue
            reason = UNSUPPORTED_REASONS.get(key, "不明な項目(未対応)")
            by_reason.setdefault(reason, []).append(key)
        for reason, keys in by_reason.items():
            dropped.append(", ".join(f"`{k}`" for k in keys) + f": {reason}")
        for part in gun.all_parts:
            for key in sorted(part.unsupported):
                dropped.append(f"パーツ `{part.name}` の `{key}`: 未対応")
            other_flags = part.flags - {"renderonnormal", "scope", "dot", "sight", "grip", "gripcover", "muzzlepart",
                                        "light", "lasersight", "gripbase", "muzzulebase", "sightbase", "turretbase",
                                        "underonly", "underonly_not", "carryinghandle"}
            for flag in sorted(other_flags):
                dropped.append(f"パーツ `{part.name}` の `{flag}`: 未対応(常に表示)")
        self.report.append(("銃", gun.file, f"{self.ns}:{gun_id}", converted, approximated, dropped, notes))

    def effects(self, gun, cocking_time, notes, converted):
        """MuzzleFlash, Cartridge (CartridgeType, CartCount, DropCartridgeEndCocked, BulletNameCart) and
        DropMagazine (MagType, MagCount, BulletNameMAG) -> "effects". Keys the gun doesn't have are left
        to Tudur's Guns' defaults for the weapon type."""
        effects = {}
        if gun.has("muzzleflash"):
            if not gun.b("muzzleflash", True):
                effects["muzzle_flash"] = False
            converted.append("MuzzleFlash → マズルフラッシュ")
        for flag, type_key, count_key, model_key, name, default_type in (
                ("cartridge", "cartridgetype", "cartcount", "bulletnamecart", "cartridge", 1),
                ("dropmagazine", "magtype", "magcount", "bulletnamemag", "magazine", 5)):
            if not gun.has(flag):
                continue
            label = {"cartridge": "Cartridge", "dropmagazine": "DropMagazine"}[flag]
            if not gun.b(flag):
                effects[name] = False
                converted.append(f"{label} → {name}: false")
                continue
            ejection = {"type": EJECT_TYPES.get(int(gun.f(type_key, default_type)), EJECT_TYPES[default_type])}
            if int(gun.f(count_key, 1)) > 1:
                ejection["count"] = int(gun.f(count_key, 1))
            if name == "cartridge" and gun.b("dropcartridgeendcocked") and cocking_time > 0:
                ejection["delay"] = int(round(self.args.cock_delay + cocking_time))
            model_name = gun.get(model_key)
            if model_name:
                bullet = self.bullet_asset(model_name, notes)
                if bullet:
                    ejection["model"] = f"{self.ns}:models/obj/bullet_{bullet}.obj"
                    ejection["texture"] = f"{self.ns}:textures/vehicle/bullet_{bullet}.png"
            effects[name] = ejection
            converted.append(f"{label} → {name}(薬莢・マガジンの排出)")
        return effects

    def bullet_model(self, gun, notes):
        for key in ("bulletnamenormal", "bulletnameall"):
            name = gun.get(key)
            if name:
                return self.bullet_asset(name, notes)
        return None

    def bullet_asset(self, name, notes):
        """A bullets/ type's model, converted to models/obj/bullet_<slug>.obj (texture alongside), once
        per pack. Returns the slug, or None."""
        if name.lower() in self.bullets:
            return self.bullets[name.lower()]
        props = self.bullet_types.get(name.lower())
        if not props:
            notes.append(f"弾頭種類 `{name}` が bullets/ にありません")
            return None
        model = props.get("objmodel", [None])[0]
        source = model and self.pack.find_asset("textures/model/" + model, "textures/model/" + model + ".obj")
        if not source:
            notes.append(f"弾頭の模型 `{model}` が見つかりません")
            return None
        bullet = slug(f"{self.ns}_{name}")
        scale = num(props.get("objscale", ["1"])[0], 1.0) * self.args.scale_factor
        # Bullet models face +Z in both mods: centred and scaled, not turned.
        mins, maxs = bounds(read_obj(source))
        centre = tuple((mins[i] + maxs[i]) / 2 for i in range(3))
        write_converted_obj(source, os.path.join(self.assets, "models/obj", f"bullet_{bullet}.obj"), Frame(centre, scale, turn=False))
        texture = props.get("objtexture", [None])[0]
        texture_source = texture and self.pack.find_asset("textures/model/" + texture, "textures/model/" + texture + ".png")
        if texture_source:
            self.copy_asset(texture_source, f"textures/vehicle/bullet_{bullet}.png")
        self.bullets[name.lower()] = bullet
        return bullet

    def ammo(self, gun, definition, notes, launcher, rounds):
        names = []
        if gun.get("magazine", 2):
            names.append(gun.get("magazine", 2))
        names += [v.split(":")[-1] for v in gun.props.get("multimagazine", []) if v]
        for name in names:
            ammo_id = self.magazines.get(name.lower())
            if ammo_id:
                definition["ammo"] = ammo_id
                return
        if names:
            notes.append(f"マガジン `{names[0]}` はパック内にない(HMG本体のアイテム)ため、既定の弾薬を使用")
        if self.args.no_ammo:
            return
        definition["ammo_item"] = self.args.default_launcher_ammo if launcher else self.args.default_ammo
        definition["rounds_per_ammo_item"] = 1

    def animation(self, gun, frame, rounds, cocking_time, reload_ticks, cock_sound, notes, converted, approximated):
        parts = {}
        fire_tracks, reload_tracks, ammo_poses = [], [], []
        counters = {}
        recoil_ticks = self.args.recoil_ticks
        cock_delay = self.args.cock_delay if cocking_time > 0 else 0
        for part in gun.all_parts:
            entry = {"groups": [part.name]}
            if part.parent is not None:
                entry["parent"] = part.parent.name
            entry["pivot"] = round_list(frame.point(part.pivot))
            if part.def_offset and any(part.def_offset):
                entry["translation"] = round_list(frame.vector(part.def_offset[:3]))
                entry["rotation"] = round_list(Frame.rotation(part.def_offset[3:]))
            if part.ads and any(part.ads):
                entry["aiming_translation"] = round_list(frame.vector(part.ads[:3]))
                entry["aiming_rotation"] = round_list(Frame.rotation(part.ads[3:]))
            parts[part.name] = entry

            def keyframes(keys, tick_scale, offset=0.0):
                frames = []
                for (start, start_pose), (end, end_pose) in keys:
                    for tick, pose in ((start, start_pose), (end, end_pose)):
                        kf = pose_keyframe(frame, offset + tick * tick_scale, pose)
                        if frames and abs(frames[-1]["tick"] - kf["tick"]) < 1e-6:
                            frames[-1] = kf
                        else:
                            frames.append(kf)
                frames.sort(key=lambda k: k["tick"])
                return frames

            if part.recoil_keys:
                fire_tracks.append({"part": part.name, "keyframes": keyframes(part.recoil_keys, recoil_ticks / 10.0)})
            elif part.recoil and any(part.recoil):
                fire_tracks.append({"part": part.name, "keyframes": [
                    pose_keyframe(frame, 0, [0] * 6), pose_keyframe(frame, 1, part.recoil),
                    pose_keyframe(frame, recoil_ticks, [0] * 6)]})
            if part.cock_keys:
                fire_tracks.append({"part": part.name, "keyframes": keyframes(part.cock_keys, 1.0, cock_delay)})
            elif part.cock and any(part.cock) and cocking_time > 0:
                fire_tracks.append({"part": part.name, "keyframes": [
                    pose_keyframe(frame, cock_delay, [0] * 6), pose_keyframe(frame, cock_delay + cocking_time * 0.3, part.cock),
                    pose_keyframe(frame, cock_delay + cocking_time * 0.7, part.cock),
                    pose_keyframe(frame, cock_delay + cocking_time, [0] * 6)]})
            if part.reload_keys:
                reload_tracks.append({"part": part.name, "keyframes": keyframes(part.reload_keys, 1.0)})
            elif part.reload and any(part.reload):
                r = float(reload_ticks)
                reload_tracks.append({"part": part.name, "keyframes": [
                    pose_keyframe(frame, 0, [0] * 6), pose_keyframe(frame, r * 0.15, part.reload),
                    pose_keyframe(frame, r * 0.85, part.reload), pose_keyframe(frame, r, [0] * 6)]})
            if part.bullet_inf and any(part.bullet_inf):
                counters[part.name + "_per_shot"] = {
                    "on": "fire", "add": 1, "modulo": max(0, rounds), "reset_on": ["reload"], "ticks": 2, "part": part.name,
                    "translation_per_step": round_list(frame.vector(part.bullet_inf[:3])),
                    "rotation_per_step": round_list(Frame.rotation(part.bullet_inf[3:]))}
            if part.bullet_positions:
                ammo_poses.append({"part": part.name, "keyframes": keyframes(part.bullet_positions, 1.0)})
        muzzle_jump = gun.f("muzzlejump", 0.0)
        if muzzle_jump:
            fire_tracks.append({"part": "root", "keyframes": [
                {"tick": 1, "rotation": [round(-muzzle_jump, 3), 0, 0], "easing": "ease_out"}, {"tick": 5}]})
            converted.append("MuzzleJump → 射撃時に銃全体が跳ねる動き")
        if not (parts or fire_tracks or cock_sound):
            return None
        animation = {"parts": parts}
        sequences = {}
        if fire_tracks or cock_sound:
            fire = {"tracks": fire_tracks}
            if cock_sound:
                fire["sounds"] = [{"tick": cock_delay, "sound": cock_sound}]
            sequences["fire"] = fire
        if reload_tracks:
            has_keys = any(p.reload_keys for p in gun.all_parts)
            sequences["reload"] = {"fit_to_event": not has_keys, "tracks": reload_tracks}
        if sequences:
            animation["sequences"] = sequences
        if counters:
            animation["counters"] = counters
        if ammo_poses:
            animation["ammo_poses"] = ammo_poses
        converted.append("パーツ(AddParts / AddChildParts)→ animation.parts(親子関係・回転中心・通常時/ADS時の位置)")
        converted.append("リコイル・コッキング・リロードのモーションキー/オフセット → animation.sequences")
        approximated.append(f"リコイルのキー(0〜10)は {recoil_ticks} tick、コッキングは射撃の {cock_delay} tick 後から開始として変換")
        return animation

    def attachment_slots(self, gun, frame, suppressed_sound, notes):
        if not self.attachments:
            return None
        allowed = None
        if gun.b("attachrestriction"):
            allowed = {v.lower() for v in gun.props.get("allowattach", []) if v}
        flagged = {}
        for part in gun.all_parts:
            for flag in part.flags:
                flagged.setdefault(flag, []).append(part.name)
        slots = {}
        mount_points = {
            "optic": ("sightsetpoint", "sightattachrotation"), "muzzle": ("muzzlesetpoint", None),
            "grip": ("gripsetpoint", "gripsetangle"), "rail": ("lightsetpoint", "lightsetangle")}
        for att in self.attachments:
            if att.kind in ("magazine", "material", None):
                continue
            if allowed is not None and att.internal_name.lower() not in allowed:
                continue
            slot = {"scope": "optic", "dot": "optic", "sight": "optic", "suppressor": "muzzle", "grip": "grip",
                    "laser": "rail", "light": "rail"}[att.kind]
            mount = {}
            point_key, rotation_key = mount_points[slot]
            if att.model_id and frame is not None and gun.has(point_key):
                point = [num(v) for v in gun.props[point_key][:3]]
                t = {"translation": round_list(frame.point(point)), "scale": [round(gun.f("modelscala", 1.0), 4)] * 3}
                if rotation_key and gun.has(rotation_key):
                    t["rotation"] = round_list(Frame.rotation([num(v) for v in gun.props[rotation_key][:3]]))
                mount["model"] = att.model_id
                mount["transform"] = t
            show = {"scope": ["scope", "sightbase"], "dot": ["dot", "sightbase"], "sight": ["sightbase"],
                    "suppressor": ["muzzlepart", "muzzulebase"], "grip": ["grip", "gripbase"],
                    "laser": ["lasersight"], "light": ["light"]}[att.kind]
            hide = {"scope": ["sight"], "dot": ["sight"], "sight": ["sight"], "grip": ["gripcover"]}.get(att.kind, [])
            show_groups = sorted({g for flag in show for g in flagged.get(flag, [])})
            hide_groups = sorted({g for flag in hide for g in flagged.get(flag, [])})
            if show_groups:
                mount["show_groups"] = show_groups
            if hide_groups:
                mount["hide_groups"] = hide_groups
            if att.kind == "suppressor" and suppressed_sound:
                mount["sound_override"] = suppressed_sound
            slots.setdefault(slot, {"accepts": {}})["accepts"][att.our_id] = mount
        return slots or None

    # ----------------------------------------------------------------- attachments / magazines

    def convert_attachment(self, att):
        notes, converted, approximated, dropped = [], [], [], []
        att_id = slug(att.internal_name or os.path.splitext(att.file)[0])
        name = att.get("name") or att.internal_name
        att.model_id = None
        att.our_id = f"{self.ns}:{att_id}"
        if att.kind == "material":
            dropped.append("素材アイテム(SimpleMaterial)は追加できないため省略。これを使うレシピも省略")
            self.report.append(("アタッチメント", att.file, "-", converted, approximated, dropped, notes))
            return
        model = att.get("objmodel")
        if model and (att.get("model") is None or boolean(att.get("model"))) and att.kind != "magazine":
            source = self.pack.find_asset("textures/model/" + model, "textures/model/" + model + ".obj")
            if source:
                frame = Frame((0.0, 0.0, 0.0), self.args.scale_factor)
                write_converted_obj(source, os.path.join(self.assets, "models/obj", "att_" + att_id + ".obj"), frame)
                att.model_id = f"{self.ns}:models/obj/att_{att_id}.obj"
                converted.append("ObjModel → 取り付け時の模型(銃の SightSetPoint などの位置に表示)")
        icon = self.texture(att.get("texture"), notes, "icons/")
        if att.kind == "magazine":
            ammo = {"display_name": name, "rounds": int(att.f("bulletround", 1)), "max_stack": int(att.f("stack", 16))}
            if icon:
                ammo["icon"] = icon
            write_json(os.path.join(self.data, "ammo", att_id + ".json"), ammo)
            self.magazines[att.internal_name.lower()] = f"{self.ns}:{att_id}"
            self.item_ids[att.internal_name.lower()] = ("ammo", f"{self.ns}:{att_id}", 0)
            converted.append("Magazine → 弾薬アイテム(data/<ns>/ammo)。BulletRound が1個あたりの弾数")
            for key in ("bullettype", "explosionlevel", "bulletmodelname", "autodestroy", "reloadtimeoption"):
                if key in att.props:
                    dropped.append(f"`{key}`: マガジンごとの弾頭設定は未対応")
            self.report.append(("マガジン", att.file, f"{self.ns}:{att_id}", converted, approximated, dropped, notes))
            return
        definition = {"display_name": name}
        if att.model_id:
            definition["model"] = att.model_id
            texture = self.texture(att.get("objtexture"), notes)
            if texture:
                definition["texture"] = texture
        if att.kind in ("scope", "dot", "sight"):
            zoom = max(1.0, att.f("zoom", 2.0 if att.kind == "scope" else 1.2))
            z = {"min": round(zoom, 3), "max": round(zoom, 3), "default": round(zoom, 3), "step": 1.0}
            overlay = self.texture(att.get("scopetexture"), notes, "scopes/")
            if overlay:
                z["overlay"] = overlay
            definition["zoom"] = z
            converted.append("Zoom / ScopeTexture → スコープ")
        elif att.kind == "suppressor":
            definition["sound_override"] = "tg_suppressed_shot"
            definition["sound_volume_multiplier"] = 0.3
            definition["muzzle_flash_multiplier"] = 0
            converted.append("サプレッサー → 発砲音の変更(銃ごとの GunSound の2つ目があればそれを使用)と音量 0.3倍")
        elif att.kind == "grip":
            anti_spread = att.f("antibure", att.f("antispread", 0.0))
            anti_recoil = att.f("reducerecoillevel", att.f("antirecoil", 0.0))
            if anti_spread:
                definition["accuracy_multiplier"] = round(max(0.0, 1.0 - anti_spread), 4)
            if anti_recoil:
                definition["recoil_multiplier"] = round(max(0.0, 1.0 - anti_recoil), 4)
            converted.append("AntiBure / AntiRecoil → 拡散・リコイルの倍率(ADS時の値は未対応)")
        else:
            dropped.append("レーザー・ライトは見た目のみ(機能なし)")
        write_json(os.path.join(self.data, "attachment", att_id + ".json"), definition)
        self.item_ids[att.internal_name.lower()] = ("attachment", att.our_id, 0)
        self.report.append(("アタッチメント", att.file, att.our_id, converted, approximated, dropped, notes))

    # ----------------------------------------------------------------- recipes

    def ingredient(self, value, notes):
        value = value.strip()
        parts = value.split(":")
        if len(parts) >= 2 and parts[0].lower() == "minecraft":
            name = parts[1].lower()
            if name in LEGACY_ITEMS:
                return LEGACY_ITEMS[name]
            if len(parts) > 2 and parts[2] not in ("", "0", "32767"):
                notes.append(f"`{value}` のメタデータ(色違いなど)は無視しました")
            return "minecraft:" + name
        name = parts[1] if len(parts) >= 2 else parts[0]
        known = self.item_ids.get(name.lower())
        if not known:
            notes.append(f"材料 `{value}` がパック内にない(HMG本体または他MODのアイテム)ためレシピを省略")
            return None
        kind, our_id, _ = known
        base, component = {"weapon": ("tudursguns:handheld_weapon", "tudursguns:weapon"),
                           "attachment": ("tudursguns:attachment", "tudursguns:attachment"),
                           "ammo": ("tudursguns:ammo", "tudursguns:ammo_type")}[kind]
        return {"fabric:type": "fabric:components", "base": base, "components": {component: our_id}}

    def result(self, value, notes):
        parts = value.split(":")
        name = parts[1] if len(parts) >= 2 else parts[0]
        count = int(num(parts[3], 1)) if len(parts) >= 4 else 1
        if parts[0].lower() == "minecraft":
            return {"id": self.ingredient(value, notes), "count": count}
        known = self.item_ids.get(name.lower())
        if not known:
            notes.append(f"完成品 `{value}` がパック内にないため省略")
            return None
        kind, our_id, rounds = known
        if kind == "weapon":
            components = {"tudursguns:weapon": our_id}
            if rounds > 0:
                components["tudursguns:ammo"] = rounds
            return {"id": "tudursguns:handheld_weapon", "count": count, "components": components}
        if kind == "attachment":
            return {"id": "tudursguns:attachment", "count": count, "components": {"tudursguns:attachment": our_id}}
        return {"id": "tudursguns:ammo", "count": count, "components": {"tudursguns:ammo_type": our_id}}

    def convert_recipes(self):
        for path in self.pack.txt_files("addpackrecipe"):
            notes = []
            converted = []
            dropped = []
            blocks, current = [], {}
            shapeless = False
            for _, key, values in parse_lines(read_text(path)):
                lower = key.lower()
                if lower in ("addrecipe", "addshapedrecipe"):
                    shapeless = False
                    continue
                if lower == "addshapelessrecipe":
                    shapeless = True
                    continue
                if lower.startswith("slot"):
                    current[int(num(lower[4:], 0))] = ",".join(values)
                elif lower == "craftitem":
                    current["result"] = ",".join(values)
                    current["shapeless"] = shapeless
                    blocks.append(current)
                    current = {}
            for index, block in enumerate(blocks, 1):
                result = self.result(block["result"], notes)
                grid, ok = {}, result is not None
                for slot in range(1, 10):
                    if slot not in block:
                        continue
                    ingredient = self.ingredient(block[slot], notes)
                    if ingredient is None:
                        ok = False
                        break
                    grid[slot] = ingredient
                if not ok:
                    dropped.append(f"{index}番目のレシピ(`{block['result']}`)")
                    continue
                recipe_id = slug(block["result"].split(":")[1] if ":" in block["result"] else block["result"])
                if block["shapeless"]:
                    recipe = {"type": "minecraft:crafting_shapeless", "category": "equipment",
                              "ingredients": [grid[s] for s in sorted(grid)], "result": result}
                else:
                    recipe = shaped_recipe(grid, result)
                write_json(os.path.join(self.data, "gun_recipe", f"{recipe_id}_{index}.json"),
                           gun_recipe([grid[s] for s in sorted(grid)], result))
                if self.args.datapack_recipes:
                    write_json(os.path.join(self.datapack, "data", self.ns, "recipe", f"{recipe_id}_{index}.json"), recipe)
                self.recipe_count += 1
                converted.append(f"{index}番目: `{block['result']}`")
            self.report.append(("レシピ", os.path.basename(path), "-", converted, [], dropped, notes))

    # ----------------------------------------------------------------- run

    def run(self):
        # A re-run replaces this pack's earlier output.
        for folder in (self.addon, self.datapack):
            if os.path.exists(folder):
                shutil.rmtree(folder)
        for path in self.pack.txt_files("bullets"):
            name, props = parse_bullet_type(path)
            self.bullet_types[name.lower()] = props
        for path in self.pack.txt_files("attachment"):
            att = parse_attachment(path)
            if att.kind is None:
                self.report.append(("アタッチメント", att.file, "-", [], [], ["登録行(SCOPE,xxx など)がないため省略"], []))
                continue
            self.attachments.append(att)
            self.convert_attachment(att)
        guns = [parse_gun(path) for path in self.pack.txt_files("guns")]
        for gun in guns:
            self.convert_gun(gun)
        self.convert_recipes()
        if self.recipe_count and self.args.datapack_recipes:
            write_json(os.path.join(self.datapack, "pack.mcmeta"), {"pack": {
                "description": f"Recipes converted from the Hand Made Guns EX pack {self.pack.name}",
                "pack_format": self.args.pack_format,
                "supported_formats": [48, 999], "min_format": 48, "max_format": 999}})
        if self.pack.folder("addscripts"):
            self.report.append(("フォルダ", "addscripts", "-", [], [], ["JavaScript のスクリプトは未対応のため省略"], []))
        if self.pack.folder("addtab"):
            self.report.append(("フォルダ", "addTab", "-", [], [], ["クリエイティブタブは Tudur's Guns のタブにまとめるため省略"], []))
        self.write_report(len(guns))

    def write_report(self, gun_count):
        lines = [f"# 変換レポート: {self.pack.name}", "",
                 f"- 名前空間: `{self.ns}`",
                 f"- 銃: {gun_count} 件 / アタッチメント・マガジン: {len(self.attachments)} 件 / レシピ: {self.recipe_count} 件",
                 f"- アドオン: `{os.path.relpath(self.addon, self.out)}`(ゲームフォルダの `tudursvehiclemod-addons/` に置く)"]
        if self.recipe_count:
            lines.append(f"- レシピ: 銃器製作台で作れます(`data/{self.ns}/gun_recipe/`)")
        if self.recipe_count and self.args.datapack_recipes:
            lines.append(f"- レシピのデータパック: `{os.path.relpath(self.datapack, self.out)}`(ワールドの `datapacks/` に置く)")
        lines += ["", "表示位置・照準位置・腕の位置・モーションの向きは推定です。ゲーム内で確認して、"
                  "`data/<名前空間>/handheld/*.json` の値を調整してください。", ""]
        for kind, file, our_id, converted, approximated, dropped, notes in self.report:
            lines.append(f"## {kind}: {file}" + (f" → `{our_id}`" if our_id != "-" else ""))
            for title, items in (("変換", converted), ("近似", approximated), ("未対応・省略", dropped), ("注意", notes)):
                if items:
                    lines.append(f"- **{title}**")
                    lines += [f"  - {item}" for item in dict.fromkeys(items)]
            lines.append("")
        write_text(os.path.join(self.out, "conversion_report.md"), "\n".join(lines))


# --------------------------------------------------------------------------- helpers using the frame

def pose_keyframe(frame, tick, pose):
    translation = frame.vector(pose[:3])
    rotation = Frame.rotation(pose[3:6])
    kf = {"tick": round(float(tick), 3)}
    if any(abs(v) > 1e-9 for v in translation):
        kf["translation"] = round_list(translation)
    if any(abs(v) > 1e-9 for v in rotation):
        kf["rotation"] = round_list(rotation)
    kf["easing"] = "linear"
    return kf


def transform(translation, rotation, scale):
    t = {"translation": list(translation)}
    if rotation:
        t["rotation"] = list(rotation)
    t["scale"] = [round(scale, 4)] * 3
    return t


def estimate_sight(vertices, frame, length):
    """The top of the model just behind the origin (the rear sight / top of the receiver), in the
    converted frame - where aiming lines up the view."""
    converted = [frame.point(v) for v in vertices]
    if not converted:
        return [0.0, 0.1, 0.0]
    xs = [v[0] for v in converted]
    width = max(xs) - min(xs) or 1.0
    best = None
    for x, y, z in converted:
        if -0.15 * length <= z <= 0.2 * length and abs(x) <= 0.3 * width:
            if best is None or y > best[1]:
                best = (x, y, z)
    if best is None:
        best = max(converted, key=lambda v: v[1])
    return [0.0, best[1], best[2]]


def shaped_recipe(grid, result):
    rows = [[grid.get(r * 3 + c + 1) for c in range(3)] for r in range(3)]
    used_rows = [r for r in range(3) if any(rows[r])]
    used_cols = [c for c in range(3) if any(rows[r][c] for r in range(3))]
    keys, pattern, letters = {}, [], "ABCDEFGHI"
    for r in range(used_rows[0], used_rows[-1] + 1):
        line = ""
        for c in range(used_cols[0], used_cols[-1] + 1):
            ingredient = rows[r][c]
            if ingredient is None:
                line += " "
                continue
            marker = json.dumps(ingredient, sort_keys=True)
            if marker not in keys:
                keys[marker] = (letters[len(keys)], ingredient)
            line += keys[marker][0]
        pattern.append(line)
    return {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern,
            "key": {letter: ingredient for letter, ingredient in keys.values()}, "result": result}


_REF_KINDS = {"tudursguns:weapon": ("weapon", "weapons"), "tudursguns:attachment": ("attachment", "attachments"),
              "tudursguns:ammo_type": ("ammo", "ammo")}


def item_ref(value):
    """A vanilla ingredient/result (an id, a "#tag", fabric:components, or a result object) as a
    gun_recipe ItemRef, and the recipe category it suggests."""
    if isinstance(value, str):
        return ({"tag": value[1:]} if value.startswith("#") else {"item": value}), None
    components = value.get("components", {})
    for component, (field, category) in _REF_KINDS.items():
        if component in components:
            return {field: components[component]}, category
    return {"item": value["id"]}, None


def gun_recipe(ingredients, result):
    """A gun crafting table recipe: the same ingredients as the grid, counted together."""
    counted = {}
    for ingredient in ingredients:
        ref, _ = item_ref(ingredient)
        key = json.dumps(ref, sort_keys=True)
        counted.setdefault(key, [ref, 0])[1] += 1
    refs = []
    for ref, count in counted.values():
        refs.append(dict(ref, count=count) if count > 1 else ref)
    result_ref, category = item_ref(result)
    if result.get("count", 1) > 1:
        result_ref["count"] = result["count"]
    recipe = {"result": result_ref, "ingredients": refs}
    if category:
        recipe["category"] = category
    return recipe


# --------------------------------------------------------------------------- main

def main(argv=None):
    parser = argparse.ArgumentParser(description="Hand Made Guns EX のパックを Tudur's Guns 用に変換します。")
    parser.add_argument("pack", help="HMG EX のパックフォルダ、またはその .zip")
    parser.add_argument("-o", "--out", default="hmgex_converted", help="出力先フォルダ(既定: hmgex_converted)")
    parser.add_argument("--namespace", help="出力の名前空間(既定: パック名を小文字にしたもの)")
    parser.add_argument("--scale-factor", type=float, default=0.16,
                        help="模型の大きさ: ModelScala にこれを掛けてブロック単位にする(既定 0.16)")
    parser.add_argument("--speed-scale", type=float, default=1.0, help="BulletSpeed に掛ける値(既定 1.0)")
    parser.add_argument("--gravity-scale", type=float, default=0.03, help="BulletGravity に掛ける値(既定 0.03)")
    parser.add_argument("--spread-scale", type=float, default=1.0, help="BulletSpread に掛ける値(既定 1.0)")
    parser.add_argument("--recoil-scale", type=float, default=1.0, help="Recoil に掛ける値(既定 1.0 = 度)")
    parser.add_argument("--recoil-ticks", type=float, default=4.0, help="リコイルのモーションキー0〜10 を何 tick にするか(既定 4)")
    parser.add_argument("--cock-delay", type=float, default=2.0, help="射撃からコッキング開始までの tick(既定 2)")
    parser.add_argument("--default-ammo", default="minecraft:iron_nugget",
                        help="パック内にマガジンがない銃の弾薬アイテム(既定 minecraft:iron_nugget)")
    parser.add_argument("--default-launcher-ammo", default="minecraft:fire_charge",
                        help="同じく、ロケット・グレネードランチャーの弾薬(既定 minecraft:fire_charge)")
    parser.add_argument("--no-ammo", action="store_true", help="パック内にマガジンがない銃は弾薬なし(無限)にする")
    parser.add_argument("--sound-map", help="HMG の音の名前 → Tudur's Guns の音の名前 の対応表(JSON)")
    parser.add_argument("--datapack-recipes", action="store_true",
                        help="銃器製作台のレシピに加えて、普通の作業台用のレシピをデータパックとしても出力する")
    parser.add_argument("--pack-format", type=int, default=94,
                        help="--datapack-recipes のデータパックの pack_format(既定 94)")
    args = parser.parse_args(argv)

    with tempfile.TemporaryDirectory() as workdir:
        root = locate_pack_root(os.path.abspath(args.pack), workdir)
        pack = Pack(root)
        out = os.path.abspath(args.out)
        converter = Converter(pack, out, args)
        converter.run()
    print(f"変換しました: {out}")
    print(f"  名前空間 {converter.ns}、レシピ {converter.recipe_count} 件。詳細は conversion_report.md を参照")
    return 0


if __name__ == "__main__":
    sys.exit(main())
