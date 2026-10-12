"""Builds the Tudur's Guns sample addon pack (sample_addon/tgsample_pack) with meshkit.

    python sample_addon/tools/build_pack.py [--preview DIR]

meshkit (https://github.com/Tuduraw/Meshkit) is found through MESHKIT_PATH, or next to this
repository (../tuduraw/meshkit or ../meshkit). It needs numpy and Pillow.

Every model, texture, icon, definition, weapon file and recipe in the pack comes from this script:
change the numbers here and run it again rather than editing the output.

Coordinates (1 unit = 1 block):
- Weapons and attachments: barrel towards -Z, +Y up, origin at the grip (Tudur's Guns' convention).
- Throwables, mines, equipment, ammo: +Y up, origin at the centre / bottom of the object.
- Armor: in the space of the body part it is worn on (see Tudur's Guns README, "防具"): origin at the
  part's pivot, +Y up, +Z forward, -X the wearer's right, a skin pixel = 1/16. OBJ groups named after
  body parts (head, body, right_leg, ...) follow that part.
"""
import argparse
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
for candidate in (os.environ.get("MESHKIT_PATH"), os.path.join(HERE, "..", "..", "..", "tuduraw", "meshkit"),
                  os.path.join(HERE, "..", "..", "..", "meshkit")):
    if candidate and os.path.isdir(os.path.join(candidate, "meshkit")):
        sys.path.insert(0, os.path.abspath(candidate))
        break

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

import meshkit as mk  # noqa: E402
from meshkit import check, view  # noqa: E402
from meshkit.texture import texture_model, value_noise  # noqa: E402

NS = "tgsample"
PACK = os.path.normpath(os.path.join(HERE, "..", "tgsample_pack"))
ASSETS = os.path.join(PACK, "assets", NS)
DATA = os.path.join(PACK, "data", NS)
PX = 1.0 / 16.0

# ---------------------------------------------------------------------------- colours
GUNMETAL = (52, 54, 58)
STEEL = (96, 100, 106)
POLYMER = (40, 42, 40)
TAN = (150, 128, 90)
OD = (86, 98, 60)
DARK_OD = (60, 70, 42)
WOOD = (120, 78, 44)
BRASS = (190, 150, 60)
RED = (170, 36, 30)
RED_SMOKE = (190, 60, 50)
LENS = (70, 40, 40)
WHITE = (220, 220, 214)

previews = []
report_lines = []


# ---------------------------------------------------------------------------- output helpers
def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def write_text(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def export(m, name, size=(128, 128), colors=None, flat=None, paint=None):
    """Textures, checks and writes a model: models/obj/<name>.obj and textures/vehicle/<name>.png."""
    texture_model(m, size, colors=colors, flat=flat, paint=paint)
    rep = check.report(m)
    issues = rep.get("issues") or []
    floating = rep.get("floating") or []
    if issues:
        raise SystemExit(f"{name}: mesh issues {issues}")
    s = m.summary()
    report_lines.append(f"{name:22s} tris={s['tris']:4d} size={s['size']} floating={len(floating)}")
    obj = os.path.join(ASSETS, "models", "obj", name + ".obj")
    os.makedirs(os.path.dirname(obj), exist_ok=True)
    mk.save(m, obj)
    png = os.path.join(ASSETS, "textures", "vehicle", name + ".png")
    os.makedirs(os.path.dirname(png), exist_ok=True)
    Image.fromarray(m.texture).save(png)
    previews.append(m)
    return f"{NS}:models/obj/{name}.obj", f"{NS}:textures/vehicle/{name}.png"


def icon(m, name, view_name="iso"):
    """A 32x32 inventory icon rendered from the model (textures/vehicle/<name>.png); returns its id."""
    key = (255, 0, 255)
    img = view.render_view(m, view_name, 192, 192, mode="texture", refs=False, bg=key, zoom=1.15)
    rgb = img.astype(np.int16)
    alpha = np.where(np.all(rgb == key, axis=2), 0, 255).astype(np.uint8)
    rgba = Image.fromarray(np.dstack([img, alpha]), "RGBA")
    box = rgba.getbbox()
    if box:
        rgba = rgba.crop(box)
    side = max(rgba.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(rgba, ((side - rgba.size[0]) // 2, (side - rgba.size[1]) // 2))
    square = square.resize((32, 32), Image.LANCZOS)
    path = os.path.join(ASSETS, "textures", "vehicle", name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    square.save(path)
    return f"{NS}:textures/vehicle/{name}.png"


def max_dim(m):
    lo, hi = m.bbox()
    return float(np.max(hi - lo))


def scaled_display(preset, base_dim, m):
    """A Tudur's Guns sample's display transforms, rescaled from that sample's size to this model's."""
    k = base_dim / max_dim(m)
    out = {}
    for ctx, t in preset.items():
        t = dict(t)
        if "scale" in t:
            t["scale"] = [round(v * k, 3) for v in t["scale"]]
        out[ctx] = t
    return out


def centered_display(m):
    """Item display transforms that put the model's centre in the middle of the item space at a
    fixed size - for armor, whose models sit in body-part space rather than round an item origin."""
    lo, hi = m.bbox()
    centre = (lo + hi) / 2
    out = {}
    for ctx, rotation, size, at in (("gui", (20, -30, 0), 0.72, (0.5, 0.5, 0.5)),
                                    ("ground", (0, 0, 0), 0.45, (0.5, 0.35, 0.5)),
                                    ("fixed", (0, 0, 0), 0.8, (0.5, 0.5, 0.5)),
                                    ("thirdperson_righthand", (0, 0, 0), 0.5, (0.5, 0.4, 0.55)),
                                    ("thirdperson_lefthand", (0, 0, 0), 0.5, (0.5, 0.4, 0.55)),
                                    ("firstperson_righthand", (0, -20, 0), 0.6, (0.6, 0.45, 0.45)),
                                    ("firstperson_lefthand", (0, 20, 0), 0.6, (0.4, 0.45, 0.45))):
        scale = size / max_dim(m)
        # Tudur's Guns applies translate, then rotate X, Y, Z, then scale: solve for the translation
        # that lands the model's centre on `at`.
        rx, ry, rz = (math.radians(a) for a in rotation)
        Rx = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]])
        Ry = np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]])
        Rz = np.array([[math.cos(rz), -math.sin(rz), 0], [math.sin(rz), math.cos(rz), 0], [0, 0, 1]])
        moved = Rx @ Ry @ Rz @ (centre * scale)
        out[ctx] = {"translation": [round(float(a - b), 4) for a, b in zip(at, moved)],
                    "rotation": list(rotation), "scale": [round(scale, 4)] * 3}
    return out


def T(translation=(0.5, 0.5, 0.5), rotation=(0, 0, 0), scale=1.0):
    return {"translation": list(translation), "rotation": list(rotation), "scale": [scale, scale, scale]}


# Display transforms of Tudur's Guns' own samples (data/tudursguns/*), with the size of their model.
PISTOL_DISPLAY = (0.405, {
    "thirdperson_righthand": T((0.5, 0.45, 0.5), scale=1.1), "thirdperson_lefthand": T((0.5, 0.45, 0.5), scale=1.1),
    "firstperson_righthand": T((0.55, 0.35, 0.4), scale=1.2), "firstperson_lefthand": T((0.45, 0.35, 0.4), scale=1.2),
    "gui": T((0.45, 0.5, 0.5), (0, 90, 0), 1.6), "ground": T((0.5, 0.3, 0.5), scale=1.0),
    "fixed": T((0.45, 0.5, 0.5), (0, 90, 0), 1.6)})
RIFLE_DISPLAY = (1.35, {
    "thirdperson_righthand": T((0.5, 0.4, 0.5), scale=0.8), "thirdperson_lefthand": T((0.5, 0.4, 0.5), scale=0.8),
    "firstperson_righthand": T((0.55, 0.3, 0.3), scale=0.9), "firstperson_lefthand": T((0.45, 0.3, 0.3), scale=0.9),
    "gui": T((0.5, 0.5, 0.5), (0, 90, 0), 0.7), "ground": T((0.5, 0.3, 0.5), scale=0.5),
    "fixed": T((0.5, 0.5, 0.5), (0, 90, 0), 0.7)})
ATTACHMENT_DISPLAY = (0.32, {
    "gui": T((0.5, 0.5, 0.5), (20, 90, 0), 1.8), "ground": T((0.5, 0.3, 0.5), scale=1.26),
    "fixed": T((0.5, 0.5, 0.5), (0, 90, 0), 1.8),
    "thirdperson_righthand": T((0.5, 0.4, 0.5), scale=1.44), "thirdperson_lefthand": T((0.5, 0.4, 0.5), scale=1.44),
    "firstperson_righthand": T((0.6, 0.4, 0.4), scale=1.8), "firstperson_lefthand": T((0.4, 0.4, 0.4), scale=1.8)})
SMALL_DISPLAY = (0.2, {
    "gui": T(rotation=(20, -30, 0), scale=2.8), "ground": T((0.5, 0.3, 0.5), scale=1.8), "fixed": T(scale=2.8),
    "thirdperson_righthand": T((0.5, 0.35, 0.55), scale=1.4), "thirdperson_lefthand": T((0.5, 0.35, 0.55), scale=1.4),
    "firstperson_righthand": T((0.6, 0.45, 0.45), (0, -20, 0), 2.0),
    "firstperson_lefthand": T((0.4, 0.45, 0.45), (0, 20, 0), 2.0)})
THROWABLE_DISPLAY = (0.185, {
    "gui": T(rotation=(20, -30, 0), scale=3.2), "ground": T((0.5, 0.3, 0.5), scale=2.0), "fixed": T(scale=3.0),
    "thirdperson_righthand": T((0.5, 0.35, 0.55), scale=1.6), "thirdperson_lefthand": T((0.5, 0.35, 0.55), scale=1.6),
    "firstperson_righthand": T((0.6, 0.45, 0.45), (0, -20, 0), 2.2),
    "firstperson_lefthand": T((0.4, 0.45, 0.45), (0, 20, 0), 2.2)})

# First-person arms of Tudur's Guns' samples (revolver and sample rifle).
PISTOL_ARMS = {"right_arm": {"translation": [-0.0222, -0.407, -0.2094], "rotation": [-85, 90, 0], "scale": [1.1111] * 3},
               "left_arm": {"translation": [0.1111, -0.4292, -0.2761], "rotation": [-85, 90, 0], "scale": [1.1111] * 3}}
RIFLE_ARMS = {"right_arm": {"translation": [-0.2182, -0.8055, -0.4409], "rotation": [-85, 90, 0], "scale": [1.8182] * 3},
              "left_arm": {"translation": [0.2545, -0.7327, -1.2409], "rotation": [-85, 90, 0], "scale": [1.8182] * 3}}


def box(cx, cy, cz, sx, sy, sz, tag, M=None):
    return mk.box(cx, cy, cz, sx, sy, sz, tag=tag, M=M, pivot=(cx, cy, cz) if M is not None else None)


def tube_z(x, y, z0, z1, r, tag, n=10):
    return mk.cylinder((x, y, z0), (x, y, z1), r, n=n, tag=tag)


def tube_y(x, z, y0, y1, r, tag, n=10):
    return mk.cylinder((x, y0, z), (x, y1, z), r, n=n, tag=tag)


def weapon_txt(name, display, lines, comment):
    write_text(os.path.join(ASSETS, "weapons", name + ".txt"),
               f"; {display} - Tudur's Guns sample addon ({NS}). {comment}\n\nDisplayName = {display}\n"
               + "\n".join(lines) + "\n")


def recipe(name, category, result, ingredients):
    write_json(os.path.join(DATA, "gun_recipe", name + ".json"),
               {"category": category, "result": result, "ingredients": ingredients})


def item(i, count=1):
    return {"item": i, "count": count} if count != 1 else {"item": i}


# ============================================================================ handheld weapons
def compact_pistol():
    m = mk.Model("compact_pistol")
    m.add("frame", box(0, 0.015, -0.085, 0.044, 0.036, 0.25, "frame"))
    m.add("frame", box(0, -0.028, -0.05, 0.012, 0.008, 0.075, "frame"))            # trigger guard
    m.add("frame", box(0, -0.01, -0.087, 0.012, 0.042, 0.01, "frame"))
    m.add("frame", box(0, -0.065, 0.03, 0.042, 0.13, 0.055, "grip", M=mk.rot_x(-14)))
    m.add("slide", box(0, 0.056, -0.1, 0.048, 0.05, 0.31, "slide"))
    m.add("slide", box(0, 0.085, -0.235, 0.008, 0.014, 0.012, "slide"))           # front sight
    m.add("slide", box(0, 0.085, 0.04, 0.032, 0.014, 0.012, "slide"))             # rear sight
    m.add("barrel", tube_z(0, 0.056, -0.25, -0.268, 0.012, "steel"))
    m.add("magazine", box(0, -0.137, 0.048, 0.036, 0.02, 0.05, "frame", M=mk.rot_x(-14)))
    model, tex = export(m, "compact_pistol", flat="all",
                        colors={"frame": POLYMER, "grip": POLYMER, "slide": GUNMETAL, "steel": STEEL})
    weapon_txt("tgs_compact_pistol", "Compact Pistol", [
        "Type = MachineGun1", "", "Power = 6.0", "Explosion = 0.0", "Accuracy = 1.6", "Acceleration = 5.0",
        "Gravity = 0.015", "", "Round = 15", "ReloadTime = 40", "Delay = 4", "",
        "Sound = tg_revolver_shot", "SoundVolume = 1.6", "SoundPitch = 1.25", "SoundPitchRandom = 0.06",
        "ModelBullet = tg_pistol_round"], "Semi-automatic, 15-round magazine.")
    base_dim, preset = PISTOL_DISPLAY
    write_json(os.path.join(DATA, "handheld", "compact_pistol.json"), {
        "weapon": "tgs_compact_pistol", "display_name": "Compact Pistol", "model": model, "texture": tex,
        "projectile_item": "minecraft:iron_nugget", "ammo": f"{NS}:pistol_magazine", "fire_mode": "semi",
        "soldier_weight": 25, "recoil": 2.0, "muzzle_offset": [0.0, -0.05, 0.55],
        "reload_sound": "tg_reload_magazine",
        "display": scaled_display(preset, base_dim, m),
        "aim": {"sight_position": [0, 0.092, 0.04], "eye_distance": 0.3, "scale": 0.9,
                "hip_translation": [0.22, -0.22, -0.45], "hip_rotation": [0, 5, 0], **PISTOL_ARMS},
        "attachments": {
            "muzzle": {"accepts": {f"{NS}:compact_suppressor": {
                "model": f"{NS}:models/obj/compact_suppressor.obj", "texture": f"{NS}:textures/vehicle/compact_suppressor.png",
                "transform": {"translation": [0, 0.056, -0.262]}, "part": "root"}}},
            "optic": {"accepts": {f"{NS}:red_dot": {
                "model": f"{NS}:models/obj/red_dot.obj", "texture": f"{NS}:textures/vehicle/red_dot.png",
                "transform": {"translation": [0, 0.081, -0.07]}, "part": "slide",
                "sight_position": [0, 0.121, -0.07]}}}},
        "animation": {
            "parts": {"root": {"pivot": [0, -0.06, 0.03]},
                      "slide": {"groups": ["slide"], "pivot": [0, 0.056, -0.1]},
                      "magazine": {"groups": ["magazine"], "pivot": [0, -0.13, 0.05]}},
            "sequences": {
                "fire": {"tracks": [
                    {"part": "slide", "keyframes": [{"tick": 0, "translation": [0, 0, 0.06], "easing": "step"},
                                                    {"tick": 2, "translation": [0, 0, 0], "easing": "ease_out"}]},
                    {"part": "root", "keyframes": [{"tick": 1, "rotation": [12, 0, 0], "easing": "ease_out"}, {"tick": 5}]}]},
                "reload": {"fit_to_event": True, "tracks": [
                    {"part": "root", "keyframes": [{"tick": 4, "rotation": [0, 0, -25]}, {"tick": 34, "rotation": [0, 0, -25]},
                                                   {"tick": 40}]},
                    {"part": "magazine", "keyframes": [{"tick": 6}, {"tick": 10, "translation": [0, -0.25, 0.05]},
                                                       {"tick": 24, "translation": [0, -0.25, 0.05]},
                                                       {"tick": 30, "translation": [0, 0, 0], "easing": "ease_out"}]},
                    {"part": "slide", "keyframes": [{"tick": 32}, {"tick": 34, "translation": [0, 0, 0.06]},
                                                    {"tick": 36, "translation": [0, 0, 0], "easing": "linear"}]}]}}},
        "effects": {"cartridge": {"type": "pistol"}},
    })
    recipe("compact_pistol", "weapons", {"weapon": f"{NS}:compact_pistol"},
           [item("minecraft:iron_ingot", 3), item("minecraft:gunpowder"), item("minecraft:black_dye")])


def smg():
    m = mk.Model("smg")
    m.add("receiver", box(0, 0.02, -0.07, 0.058, 0.08, 0.42, "receiver"))
    m.add("receiver", box(0, 0.068, -0.08, 0.026, 0.014, 0.32, "steel"))            # top rail
    m.add("receiver", box(0, 0.012, -0.355, 0.054, 0.058, 0.17, "polymer"))         # handguard
    m.add("receiver", box(0, -0.07, 0.075, 0.04, 0.12, 0.05, "polymer", M=mk.rot_x(-12)))
    m.add("receiver", box(0, -0.028, 0.04, 0.012, 0.008, 0.08, "receiver"))         # trigger guard
    m.add("barrel", tube_z(0, 0.035, -0.42, -0.53, 0.013, "steel"))
    for x in (-0.018, 0.018):                                                        # stock rods
        m.add("stock", tube_z(x, 0.03, 0.12, 0.34, 0.008, "steel", n=6))
    m.add("stock", box(0, 0.0, 0.345, 0.05, 0.11, 0.025, "polymer"))
    m.add("magazine", box(0, -0.1, -0.11, 0.028, 0.17, 0.042, "steel", M=mk.rot_x(8)))
    m.add("bolt", box(0.036, 0.04, -0.04, 0.018, 0.014, 0.03, "steel"))
    m.add("iron_sight", box(0, 0.085, -0.225, 0.008, 0.022, 0.012, "steel"))
    m.add("iron_sight", box(0, 0.085, 0.07, 0.028, 0.022, 0.012, "steel"))
    model, tex = export(m, "smg", flat="all",
                        colors={"receiver": GUNMETAL, "polymer": POLYMER, "steel": STEEL})
    weapon_txt("tgs_smg", "Submachine Gun", [
        "Type = MachineGun1", "", "Power = 4.5", "Explosion = 0.0", "Accuracy = 2.4", "Acceleration = 6.0",
        "Gravity = 0.012", "", "Round = 30", "ReloadTime = 45", "Delay = 2", "",
        "Sound = tg_sample_rifle", "SoundVolume = 1.3", "SoundPitch = 1.15", "SoundPitchRandom = 0.05",
        "ModelBullet = tg_pistol_round"], "Automatic, 30-round magazine.")
    base_dim, preset = RIFLE_DISPLAY
    write_json(os.path.join(DATA, "handheld", "smg.json"), {
        "weapon": "tgs_smg", "display_name": "Submachine Gun", "model": model, "texture": tex,
        "projectile_item": "minecraft:iron_nugget", "ammo": f"{NS}:smg_magazine", "fire_mode": "auto",
        "soldier_weight": 25, "recoil": 1.2, "ads_spread_multiplier": 0.6, "muzzle_offset": [0.0, -0.05, 0.7],
        "reload_sound": "tg_reload_magazine",
        "display": scaled_display(preset, base_dim, m),
        "aim": {"sight_position": [0, 0.096, 0.07], "eye_distance": 0.15, "scale": 0.6,
                "hip_translation": [0.26, -0.3, -0.4], "hip_rotation": [0, 3, 0], **RIFLE_ARMS},
        "attachments": {
            "optic": {"accepts": {f"{NS}:red_dot": {
                "model": f"{NS}:models/obj/red_dot.obj", "texture": f"{NS}:textures/vehicle/red_dot.png",
                "transform": {"translation": [0, 0.075, -0.06]}, "hide_groups": ["iron_sight"],
                "sight_position": [0, 0.115, -0.06]}}},
            "muzzle": {"accepts": {f"{NS}:compact_suppressor": {
                "model": f"{NS}:models/obj/compact_suppressor.obj", "texture": f"{NS}:textures/vehicle/compact_suppressor.png",
                "transform": {"translation": [0, 0.035, -0.525]}}}},
            "grip": {"accepts": {f"{NS}:vertical_foregrip": {
                "model": f"{NS}:models/obj/vertical_foregrip.obj", "texture": f"{NS}:textures/vehicle/vertical_foregrip.png",
                "transform": {"translation": [0, -0.017, -0.36]}}}}},
        "animation": {
            "parts": {"root": {"pivot": [0, -0.05, 0.1]},
                      "bolt": {"groups": ["bolt"], "pivot": [0.036, 0.04, -0.04]},
                      "magazine": {"groups": ["magazine"], "pivot": [0, -0.1, -0.11]}},
            "sequences": {
                "fire": {"tracks": [
                    {"part": "bolt", "keyframes": [{"tick": 0, "translation": [0, 0, 0.05], "easing": "step"},
                                                   {"tick": 1, "translation": [0, 0, 0], "easing": "linear"}]},
                    {"part": "root", "keyframes": [{"tick": 1, "translation": [0, 0, 0.012], "rotation": [2, 0, 0],
                                                    "easing": "ease_out"}, {"tick": 3}]}]},
                "reload": {"fit_to_event": True, "tracks": [
                    {"part": "root", "keyframes": [{"tick": 4, "rotation": [0, 0, 20]}, {"tick": 34, "rotation": [0, 0, 20]},
                                                   {"tick": 40}]},
                    {"part": "magazine", "keyframes": [{"tick": 6}, {"tick": 10, "translation": [0, -0.3, 0]},
                                                       {"tick": 24, "translation": [0, -0.3, 0]},
                                                       {"tick": 30, "translation": [0, 0, 0], "easing": "ease_out"}]},
                    {"part": "bolt", "keyframes": [{"tick": 31}, {"tick": 34, "translation": [0, 0, 0.08]},
                                                   {"tick": 36, "translation": [0, 0, 0], "easing": "linear"}]}]}}},
        "effects": {"cartridge": {"type": "pistol"}},
    })
    recipe("smg", "weapons", {"weapon": f"{NS}:smg"},
           [item("minecraft:iron_ingot", 6), item("minecraft:gunpowder", 2), item("minecraft:black_dye")])


def pump_shotgun():
    m = mk.Model("pump_shotgun")
    m.add("receiver", box(0, 0.025, 0.0, 0.058, 0.085, 0.3, "receiver"))
    m.add("receiver", box(0, -0.028, 0.06, 0.012, 0.008, 0.08, "receiver"))          # trigger guard
    m.add("receiver", box(0, -0.02, 0.022, 0.012, 0.02, 0.01, "receiver"))
    m.add("barrel", tube_z(0, 0.048, -0.15, -0.86, 0.018, "steel"))
    m.add("barrel", tube_z(0, 0.002, -0.15, -0.72, 0.016, "steel"))                 # tube magazine
    m.add("barrel", box(0, 0.026, -0.71, 0.024, 0.05, 0.03, "steel"))               # barrel clamp
    stock = [(0.14, 0.065), (0.56, 0.005), (0.56, -0.135), (0.46, -0.135), (0.14, -0.03)]
    m.add("stock", mk.extrude_zy(stock, -0.024, 0.024, tag="wood"))
    m.add("pump", box(0, 0.0, -0.4, 0.056, 0.052, 0.2, "wood"))
    m.add("iron_sight", box(0, 0.071, -0.84, 0.008, 0.01, 0.01, "brass"))
    m.add("iron_sight", box(0, 0.074, 0.11, 0.026, 0.014, 0.012, "steel"))
    model, tex = export(m, "pump_shotgun", flat="all",
                        colors={"receiver": GUNMETAL, "steel": STEEL, "wood": WOOD, "brass": BRASS})
    weapon_txt("tgs_pump_shotgun", "Pump Shotgun", [
        "Type = MachineGun1", "", "; Power is per pellet (8 pellets, see the definition's \"pellets\").",
        "Power = 3.0", "Explosion = 0.0", "Accuracy = 6.0", "Acceleration = 4.5", "Gravity = 0.02", "",
        "Round = 6", "ReloadTime = 90", "Delay = 16", "",
        "Sound = tg_sniper_shot", "SoundVolume = 2.0", "SoundPitch = 0.85", "SoundPitchRandom = 0.06",
        "ModelBullet = tg_pistol_round"], "Pump action, six shells, eight pellets a shot.")
    base_dim, preset = RIFLE_DISPLAY
    write_json(os.path.join(DATA, "handheld", "pump_shotgun.json"), {
        "weapon": "tgs_pump_shotgun", "display_name": "Pump Shotgun", "model": model, "texture": tex,
        "projectile_item": "minecraft:iron_nugget", "ammo": f"{NS}:shotgun_shells", "fire_mode": "semi",
        "pellets": 8, "soldier_weight": 15, "recoil": 6.0, "muzzle_offset": [0.0, -0.05, 0.95],
        "movement_speed": -0.05, "reload_sound": "tg_reload_revolver",
        "display": scaled_display(preset, base_dim, m),
        "aim": {"sight_position": [0, 0.081, 0.11], "eye_distance": 0.14, "scale": 0.55,
                "hip_translation": [0.26, -0.3, -0.4], "hip_rotation": [0, 3, 0], **RIFLE_ARMS},
        "attachments": {
            "optic": {"accepts": {f"{NS}:red_dot": {
                "model": f"{NS}:models/obj/red_dot.obj", "texture": f"{NS}:textures/vehicle/red_dot.png",
                "transform": {"translation": [0, 0.067, 0.0]}, "hide_groups": ["iron_sight"],
                "sight_position": [0, 0.107, 0.0]}}}},
        "animation": {
            "parts": {"root": {"pivot": [0, -0.03, 0.15]},
                      "pump": {"groups": ["pump"], "pivot": [0, 0.0, -0.4]}},
            "sequences": {
                "fire": {"tracks": [
                    {"part": "root", "keyframes": [{"tick": 1, "translation": [0, 0, 0.04], "rotation": [9, 0, 0],
                                                    "easing": "ease_out"}, {"tick": 7}]},
                    {"part": "pump", "keyframes": [{"tick": 5}, {"tick": 8, "translation": [0, 0, 0.1]},
                                                   {"tick": 12, "translation": [0, 0, 0], "easing": "ease_in"}]}],
                    "sounds": [{"tick": 7, "sound": "tg_reload_bolt", "volume": 0.6, "pitch": 1.2}]},
                "reload": {"fit_to_event": True, "tracks": [
                    {"part": "root", "keyframes": [{"tick": 5, "rotation": [0, 0, -30]}, {"tick": 34, "rotation": [0, 0, -30]},
                                                   {"tick": 40}]},
                    {"part": "pump", "keyframes": [{"tick": 34}, {"tick": 36, "translation": [0, 0, 0.1]},
                                                   {"tick": 39, "translation": [0, 0, 0], "easing": "ease_in"}]}]}}},
        "effects": {"cartridge": {"type": "shotgun", "delay": 8}, "magazine": False,
                    "muzzle_flash": {"size": 1.8, "count": 5, "smoke": 4}},
    })
    recipe("pump_shotgun", "weapons", {"weapon": f"{NS}:pump_shotgun"},
           [item("minecraft:iron_ingot", 5), item("minecraft:gunpowder", 2), {"tag": "minecraft:planks", "count": 2}])


# ============================================================================ attachments
def attachment_def(name, display_name, m, model, tex, extra, ingredients):
    base_dim, preset = ATTACHMENT_DISPLAY
    write_json(os.path.join(DATA, "attachment", name + ".json"),
               {"display_name": display_name, "model": model, "texture": tex, **extra,
                "display": scaled_display(preset, base_dim, m)})
    recipe(name, "attachments", {"attachment": f"{NS}:{name}"}, ingredients)


def red_dot():
    m = mk.Model("red_dot")
    m.add("mount", box(0, 0.007, 0, 0.03, 0.014, 0.07, "steel"))
    m.add("body", tube_z(0, 0.036, 0.03, -0.035, 0.02, "body", n=12))
    m.add("body", box(0.024, 0.036, 0.0, 0.012, 0.016, 0.02, "body"))               # brightness knob
    m.add("lens", tube_z(0, 0.036, -0.03, -0.037, 0.015, "lens", n=12))
    model, tex = export(m, "red_dot", flat="all", colors={"steel": STEEL, "body": GUNMETAL, "lens": LENS})
    attachment_def("red_dot", "Red Dot Sight", m, model, tex, {"accuracy_multiplier": 0.9},
                   [item("minecraft:iron_ingot"), item("minecraft:glass_pane"), item("minecraft:redstone")])


def compact_suppressor():
    m = mk.Model("compact_suppressor")
    m.add("body", tube_z(0, 0, 0.005, -0.17, 0.022, "body", n=12))
    m.add("body", tube_z(0, 0, -0.165, -0.18, 0.018, "steel", n=12))
    model, tex = export(m, "compact_suppressor", flat="all", colors={"body": POLYMER, "steel": STEEL})
    attachment_def("compact_suppressor", "Compact Suppressor", m, model, tex,
                   {"sound_override": "tg_suppressed_shot", "sound_volume_multiplier": 0.3,
                    "muzzle_flash_multiplier": 0, "accuracy_multiplier": 1.05},
                   [item("minecraft:iron_ingot", 2), item("minecraft:white_wool")])


def vertical_foregrip():
    m = mk.Model("vertical_foregrip")
    m.add("clamp", box(0, 0.0, 0, 0.034, 0.014, 0.05, "steel"))
    m.add("grip", box(0, -0.055, 0.004, 0.026, 0.1, 0.03, "grip", M=mk.rot_x(-6)))
    model, tex = export(m, "vertical_foregrip", flat="all", colors={"steel": STEEL, "grip": POLYMER})
    attachment_def("vertical_foregrip", "Vertical Foregrip", m, model, tex,
                   {"recoil_multiplier": 0.65, "accuracy_multiplier": 0.9},
                   [item("minecraft:iron_ingot"), item("minecraft:black_dye")])


# ============================================================================ ammo
def ammo_def(name, display_name, m, model, tex, rounds, max_stack, ingredients, count):
    icon_id = icon(m, name + "_icon")
    base_dim, preset = SMALL_DISPLAY
    write_json(os.path.join(DATA, "ammo", name + ".json"),
               {"display_name": display_name, "rounds": rounds, "max_stack": max_stack, "model": model, "texture": tex,
                "icon": icon_id, "display": scaled_display(preset, base_dim, m)})
    recipe(name, "ammo", {"ammo": f"{NS}:{name}", "count": count}, ingredients)


def pistol_magazine():
    m = mk.Model("pistol_magazine")
    m.add("body", box(0, -0.06, 0, 0.03, 0.12, 0.04, "body"))
    m.add("body", box(0, -0.122, 0.002, 0.036, 0.012, 0.048, "base"))
    m.add("rounds", box(0, 0.003, 0.0, 0.016, 0.01, 0.03, "brass"))
    model, tex = export(m, "pistol_magazine", flat="all", colors={"body": GUNMETAL, "base": POLYMER, "brass": BRASS})
    ammo_def("pistol_magazine", "Pistol Magazine (15)", m, model, tex, 15, 16,
             [item("minecraft:iron_nugget", 6), item("minecraft:gunpowder")], 2)


def smg_magazine():
    m = mk.Model("smg_magazine")
    m.add("body", box(0, -0.05, 0, 0.028, 0.1, 0.042, "body"))
    m.add("body", box(0, -0.13, 0.008, 0.028, 0.08, 0.042, "body", M=mk.rot_x(8)))
    m.add("body", box(0, -0.172, 0.014, 0.034, 0.012, 0.05, "base", M=mk.rot_x(8)))
    m.add("rounds", box(0, 0.003, 0.0, 0.016, 0.01, 0.03, "brass"))
    model, tex = export(m, "smg_magazine", flat="all", colors={"body": STEEL, "base": POLYMER, "brass": BRASS})
    ammo_def("smg_magazine", "SMG Magazine (30)", m, model, tex, 30, 16,
             [item("minecraft:iron_nugget", 9), item("minecraft:gunpowder")], 2)


def shotgun_shells():
    m = mk.Model("shotgun_shells")
    for x in (-0.024, 0.0, 0.024):
        m.add("shells", tube_y(x, 0.0, 0.0, 0.016, 0.0125, "brass", n=10))
        m.add("shells", tube_y(x, 0.0, 0.012, 0.072, 0.0115, "hull", n=10))
    model, tex = export(m, "shotgun_shells", flat="all", colors={"brass": BRASS, "hull": RED})
    ammo_def("shotgun_shells", "Shotgun Shells", m, model, tex, 1, 32,
             [item("minecraft:iron_nugget", 2), item("minecraft:gunpowder"), item("minecraft:paper")], 12)


# ============================================================================ throwables
def throwable_def(name, m, model, tex, extra, ingredients, count=2):
    base_dim, preset = THROWABLE_DISPLAY
    write_json(os.path.join(DATA, "throwable", name + ".json"),
               {"weapon": "tgs_" + name, "model": model, "texture": tex, **extra,
                "display": scaled_display(preset, base_dim, m)})
    recipe(name, "throwables", {"throwable": f"{NS}:{name}", "count": count}, ingredients)


def impact_grenade():
    m = mk.Model("impact_grenade")
    m.add("body", mk.lathe((0, -0.06, 0), (0, 1, 0), [(0.0, 0.0), (0.006, 0.03), (0.09, 0.034), (0.11, 0.022),
                                                      (0.12, 0.0)], n=12, tag="body"))
    m.add("fuse", tube_y(0, 0, 0.05, 0.085, 0.014, "steel"))
    m.add("fuse", box(0.018, 0.07, 0, 0.012, 0.03, 0.01, "steel"))
    model, tex = export(m, "impact_grenade", flat="all", colors={"body": OD, "steel": STEEL})
    weapon_txt("tgs_impact_grenade", "Impact Grenade", [
        "Power = 2", "Explosion = 2.0", "ExplosionBlock = 0", "Gravity = 0.05", "Sound = tg_grenade_throw",
        "SoundVolume = 0.8", "SoundPitchRandom = 0.1"], "Goes off where it first hits.")
    throwable_def("impact_grenade", m, model, tex,
                  {"display_name": "Impact Grenade", "throw_velocity": 1.0, "underhand_velocity": 0.45,
                   "fuse_ticks": 200, "impact": True, "pin_sound": "tg_grenade_pin"},
                  [item("minecraft:iron_ingot", 2), item("minecraft:gunpowder"), item("minecraft:redstone")])


def stick_grenade():
    m = mk.Model("stick_grenade")
    m.add("head", tube_y(0, 0, 0.0, 0.1, 0.036, "head", n=12))
    m.add("head", tube_y(0, 0, 0.095, 0.105, 0.03, "steel", n=12))
    m.add("handle", tube_y(0, 0, -0.24, 0.005, 0.013, "wood", n=8))
    m.add("handle", tube_y(0, 0, -0.255, -0.235, 0.016, "steel", n=8))
    model, tex = export(m, "stick_grenade", flat="all", colors={"head": DARK_OD, "wood": WOOD, "steel": STEEL})
    weapon_txt("tgs_stick_grenade", "Stick Grenade", [
        "Power = 2", "Explosion = 2.6", "ExplosionBlock = 0", "Gravity = 0.05", "Bound = 0.25",
        "Sound = tg_grenade_throw", "SoundVolume = 0.8", "SoundPitch = 0.9", "SoundPitchRandom = 0.1"],
        "Long throw, timed fuse from the throw.")
    throwable_def("stick_grenade", m, model, tex,
                  {"display_name": "Stick Grenade", "throw_velocity": 1.45, "underhand_velocity": 0.55,
                   "fuse_ticks": 90, "cookable": False, "pin_sound": "tg_grenade_pin"},
                  [item("minecraft:iron_ingot", 2), item("minecraft:gunpowder"), item("minecraft:stick")])


def colored_smoke():
    m = mk.Model("colored_smoke")
    m.add("body", tube_y(0, 0, -0.06, 0.06, 0.03, "body", n=12))
    m.add("body", tube_y(0, 0, 0.055, 0.075, 0.022, "steel", n=12))
    m.add("body", box(0.033, 0.03, 0, 0.008, 0.07, 0.012, "steel"))                  # spoon
    model, tex = export(m, "colored_smoke", flat="all", colors={"body": RED_SMOKE, "steel": STEEL})
    weapon_txt("tgs_colored_smoke", "Red Smoke Grenade", [
        "Power = 1", "Explosion = 0", "Gravity = 0.05", "Bound = 0.35", "Sound = tg_grenade_throw",
        "SoundVolume = 0.8", "SoundPitchRandom = 0.1"], "Red smoke, for marking a spot.")
    throwable_def("colored_smoke", m, model, tex,
                  {"display_name": "Red Smoke Grenade", "throw_velocity": 1.0, "underhand_velocity": 0.45,
                   "fuse_ticks": 30, "cookable": False, "pin_sound": "tg_grenade_pin",
                   "effect": {"type": "smoke", "radius": 4, "duration_ticks": 500, "color": [0.85, 0.2, 0.18],
                              "sound": "tg_smoke_hiss"}},
                  [item("minecraft:iron_ingot"), item("minecraft:gunpowder"), item("minecraft:red_dye")])


# ============================================================================ mines
def mine_def(name, display_name, m, model, tex, extra, ingredients, count=1):
    base_dim, preset = SMALL_DISPLAY
    write_json(os.path.join(DATA, "mine", name + ".json"),
               {"weapon": "tgs_" + name, "display_name": display_name, "model": model, "texture": tex, **extra,
                "display": scaled_display(preset, base_dim, m)})
    recipe(name, "mines", {"mine": f"{NS}:{name}", "count": count}, ingredients)


def bounding_mine():
    m = mk.Model("bounding_mine")
    m.add("body", tube_y(0, 0, 0.0, 0.1, 0.05, "body", n=12))
    m.add("body", tube_y(0, 0, 0.095, 0.11, 0.035, "steel", n=12))
    for k in range(3):                                                               # trip prongs
        a = math.radians(k * 120)
        m.add("prongs", tube_y(0.012 * math.cos(a), 0.012 * math.sin(a), 0.105, 0.15, 0.004, "steel", n=6))
    model, tex = export(m, "bounding_mine", flat="all", colors={"body": DARK_OD, "steel": STEEL})
    weapon_txt("tgs_bounding_mine", "Bounding Mine", [
        "Power = 14", "Explosion = 2.6", "ExplosionBlock = 0"], "Proximity mine with a short delay.")
    mine_def("bounding_mine", "Bounding Mine", m, model, tex,
             {"trigger": "proximity", "trigger_radius": 1.4, "arming_ticks": 60, "trigger_delay_ticks": 12,
              "placement": "ground", "defuse_ticks": 80,
              "sounds": {"arm": "tg_mine_arm", "trigger": "tg_mine_click", "place": "tg_mine_place"}},
             [item("minecraft:iron_ingot", 3), item("minecraft:gunpowder", 2), item("minecraft:tripwire_hook")])


def demolition_charge():
    m = mk.Model("demolition_charge")
    m.add("block", box(0, 0.03, 0, 0.18, 0.06, 0.1, "block"))
    m.add("block", box(0, 0.062, 0, 0.185, 0.008, 0.02, "strap"))
    m.add("detonator", box(0.05, 0.072, 0.0, 0.03, 0.02, 0.04, "det"))
    m.add("detonator", tube_y(0.05, 0.0, 0.08, 0.1, 0.004, "steel", n=6))
    model, tex = export(m, "demolition_charge", flat="all",
                        colors={"block": (190, 180, 150), "strap": OD, "det": POLYMER, "steel": STEEL})
    weapon_txt("tgs_demolition_charge", "Demolition Charge", [
        "Power = 30", "Explosion = 3.5", "ExplosionBlock = 3.5"],
        "Remote charge for Tudur's Guns' detonator. Sticks to any face and to vehicles.")
    mine_def("demolition_charge", "Demolition Charge", m, model, tex,
             {"trigger": "remote", "trigger_radius": 0.0, "arming_ticks": 20, "placement": "anywhere",
              "defuse_ticks": 40, "sounds": {"arm": "tg_mine_arm", "place": "tg_mine_place"}},
             [item("minecraft:clay_ball", 2), item("minecraft:gunpowder", 3), item("minecraft:redstone")])


# ============================================================================ equipment
def equipment_def(name, m, model, tex, extra, ingredients, count=1):
    base_dim, preset = SMALL_DISPLAY
    write_json(os.path.join(DATA, "equipment", name + ".json"),
               {**extra, "model": model, "texture": tex, "display": scaled_display(preset, base_dim, m)})
    recipe(name, "equipment", {"equipment": f"{NS}:{name}", "count": count}, ingredients)


def field_medkit():
    m = mk.Model("field_medkit")
    m.add("pouch", box(0, 0.0, 0, 0.16, 0.1, 0.06, "pouch"))
    m.add("pouch", box(0, 0.055, 0.0, 0.165, 0.012, 0.064, "flap"))
    m.add("pouch", box(0, 0.0, -0.034, 0.12, 0.08, 0.012, "flap"))                  # back strap

    def cross(atlas, model):
        def fn(X, Y, Z, cur):
            mask = (Z > 0.029) & (((np.abs(X) < 0.012) & (np.abs(Y) < 0.035)) | ((np.abs(Y) < 0.012) & (np.abs(X) < 0.035)))
            rgb = np.where(mask[:, None], np.array(RED, np.float32), cur)
            return rgb, mask
        atlas.paint3d(fn, tags=["pouch"])
    model, tex = export(m, "field_medkit", (128, 128), colors={"pouch": OD}, flat={"flap": DARK_OD}, paint=cross)
    equipment_def("field_medkit", m, model, tex,
                  {"type": "first_aid", "display_name": "Field Medkit", "uses": 3, "use_ticks": 50, "heal": 10,
                   "regeneration_ticks": 120, "sound": "tg_first_aid"},
                  [item("minecraft:white_wool", 2), item("minecraft:golden_carrot"), item("minecraft:green_dye")])


def compact_binoculars():
    m = mk.Model("compact_binoculars")
    for x in (-0.035, 0.035):
        m.add("body", tube_z(x, 0, 0.05, -0.05, 0.026, "body", n=12))
        m.add("lens", tube_z(x, 0, -0.048, -0.055, 0.02, "lens", n=12))
        m.add("body", tube_z(x, 0, 0.045, 0.06, 0.018, "rubber", n=12))
    m.add("body", box(0, 0.0, 0.0, 0.05, 0.02, 0.05, "body"))
    model, tex = export(m, "compact_binoculars", flat="all", colors={"body": OD, "rubber": POLYMER, "lens": (40, 60, 70)})
    equipment_def("compact_binoculars", m, model, tex,
                  {"type": "binoculars", "display_name": "Compact Binoculars", "rangefinder": True,
                   "zoom": {"min": 2, "max": 4, "default": 3, "step": 1,
                            "overlay": "tudursguns:textures/misc/binoculars.png"}},
                  [item("minecraft:iron_ingot", 2), item("minecraft:glass_pane", 2), item("minecraft:redstone")])


# ============================================================================ armor
def armor_def(name, slot, display_name, m, extra, ingredients, colors, flat=None, paint=None):
    model, tex = export(m, name, (128, 128), colors=colors, flat=flat, paint=paint)
    write_json(os.path.join(DATA, "armor", name + ".json"),
               {"slot": slot, "display_name": display_name, **extra, "model": model,
                "texture": tex, "display": centered_display(m)})
    recipe(name, "armor", {"armor": f"{NS}:{name}"}, ingredients)


def camo(atlas, model):
    def fn(X, Y, Z, cur):
        # value_noise is 0..1, with features about `scale` blocks across
        n = value_noise(X + 0.6 * Y, Z - 0.4 * Y, 0.08, seed=5)
        return np.where((n > 0.6)[:, None], np.array(DARK_OD, np.float32),
                        np.where((n < 0.38)[:, None], np.array(TAN, np.float32), cur)), None
    atlas.paint3d(fn, tags=["cloth"])


def boonie_hat():
    p = PX
    m = mk.Model("boonie_hat")
    m.add("head", box(0, 7.8 * p, 0, 9.0 * p, 2.6 * p, 9.0 * p, "cloth"))               # crown
    m.add("head", box(0, 9.25 * p, 0, 8.0 * p, 0.6 * p, 8.0 * p, "cloth"))
    m.add("head", box(0, 6.75 * p, 0, 12.0 * p, 0.5 * p, 12.0 * p, "cloth"))             # brim
    m.add("head", box(0, 7.4 * p, 0, 9.2 * p, 0.6 * p, 9.2 * p, "band"))                 # band
    armor_def("boonie_hat", "head", "Boonie Hat", m,
              {"armor": 1, "durability": 120, "soldier_weight": 15, "effects": {"detection_multiplier": 0.85}},
              [item("minecraft:green_wool", 3), item("minecraft:string")],
              colors={"cloth": OD}, flat={"band": DARK_OD}, paint=camo)


def assault_vest():
    p = PX
    m = mk.Model("assault_vest")
    m.add("body", box(0, -4.6 * p, 2.7 * p, 7.6 * p, 8.0 * p, 1.0 * p, "cloth"))         # front panel
    m.add("body", box(0, -4.6 * p, -2.7 * p, 7.6 * p, 8.0 * p, 1.0 * p, "cloth"))        # back panel
    m.add("body", box(4.5 * p, -6.5 * p, 0, 0.8 * p, 4.0 * p, 5.6 * p, "cloth"))          # sides
    m.add("body", box(-4.5 * p, -6.5 * p, 0, 0.8 * p, 4.0 * p, 5.6 * p, "cloth"))
    m.add("body", box(0, -6.5 * p, 2.45 * p, 9.6 * p, 4.0 * p, 0.6 * p, "cloth"))         # waist band
    m.add("body", box(0, -6.5 * p, -2.45 * p, 9.6 * p, 4.0 * p, 0.6 * p, "cloth"))
    for x in (-2.3, 2.3):                                                                 # shoulder straps
        m.add("body", box(x * p, -0.3 * p, 0, 1.8 * p, 1.6 * p, 6.6 * p, "strap"))
    for x in (-2.4, 0.0, 2.4):                                                            # magazine pouches
        m.add("body", box(x * p, -6.4 * p, 3.7 * p, 2.0 * p, 3.0 * p, 1.2 * p, "pouch"))
    m.add("body", box(2.2 * p, -2.4 * p, 3.5 * p, 2.4 * p, 2.0 * p, 1.0 * p, "pouch"))    # radio pouch
    armor_def("assault_vest", "chest", "Assault Vest", m,
              {"armor": 4, "toughness": 1, "durability": 300, "soldier_weight": 12,
               "protection": {"ballistic": 0.25, "blast": 0.15}},
              [item("minecraft:leather", 4), item("minecraft:iron_ingot", 3), item("minecraft:green_dye")],
              colors={"cloth": OD}, flat={"strap": DARK_OD, "pouch": DARK_OD}, paint=camo)


def knee_pads():
    p = PX
    m = mk.Model("knee_pads")
    for leg in ("right_leg", "left_leg"):
        # leg space: x +-2 px around the hip pivot, y 0 (hip) .. -12 (foot), knee at about y -6
        m.add(leg, box(0, -6.0 * p, 2.45 * p, 4.4 * p, 3.4 * p, 0.9 * p, "pad"))
        m.add(leg, box(0, -6.0 * p, 2.95 * p, 3.0 * p, 2.2 * p, 0.4 * p, "cap"))
        m.add(leg, box(0, -6.0 * p, -2.15 * p, 4.4 * p, 1.0 * p, 0.3 * p, "strap"))
        for x in (-2.15, 2.15):
            m.add(leg, box(x * p, -6.0 * p, 0.15 * p, 0.3 * p, 1.0 * p, 4.6 * p, "strap"))
    armor_def("knee_pads", "legs", "Knee Pads", m,
              {"armor": 2, "durability": 200, "soldier_weight": 10, "knockback_resistance": 0.05},
              [item("minecraft:leather", 2), item("minecraft:iron_nugget", 4)],
              colors={"pad": OD}, flat={"cap": POLYMER, "strap": POLYMER})


# ============================================================================ main
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", help="folder for preview sheets (not part of the pack)")
    args = ap.parse_args()
    for builder in (compact_pistol, smg, pump_shotgun, red_dot, compact_suppressor, vertical_foregrip,
                    pistol_magazine, smg_magazine, shotgun_shells, impact_grenade, stick_grenade, colored_smoke,
                    bounding_mine, demolition_charge, field_medkit, compact_binoculars,
                    boonie_hat, assault_vest, knee_pads):
        builder()
    print("\n".join(report_lines))
    if args.preview:
        os.makedirs(args.preview, exist_ok=True)
        for m in previews:
            view.sheet(m, views=("iso", "left", "top"), W=360, H=240, mode="texture").save(
                os.path.join(args.preview, m.name + ".png"))
    print("pack written to", PACK)


if __name__ == "__main__":
    main()
