"""Builds Tudur's Guns' own ammunition items with meshkit: their OBJ models, textures, inventory
icons, ammo definitions (data/tudursguns/ammo/) and gun crafting recipes.

    /usr/bin/python3 tools/ammo/build_ammo.py

meshkit (https://github.com/Tuduraw/Meshkit) is found through MESHKIT_PATH, or next to this
repository (../tuduraw/meshkit or ../meshkit). It needs numpy and Pillow.

Weapons that fire much the same round share one item, to keep the number of ammo items small:

| ammo              | rounds          | used by                                         |
|-------------------|-----------------|-------------------------------------------------|
| rifle_clip        | 5               | infantry rifle, sniper rifle, sample rifle      |
| pistol_rounds     | 6               | revolver                                        |
| launcher_grenade  | 1               | underbarrel grenade launcher                    |
| rocket            | 1               | sample AT launcher                              |
| generic           | a full magazine | any weapon without ammo settings of its own     |

Coordinates: +Y up, origin at the centre / bottom of the object, 1 unit = 1 block.
Change the numbers here and run it again rather than editing the output.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
for candidate in (os.environ.get("MESHKIT_PATH"), os.path.join(ROOT, "..", "tuduraw", "meshkit"),
                  os.path.join(ROOT, "..", "meshkit")):
    if candidate and os.path.isdir(os.path.join(candidate, "meshkit")):
        sys.path.insert(0, os.path.abspath(candidate))
        break

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

import meshkit as mk  # noqa: E402
from meshkit import check, view  # noqa: E402
from meshkit.texture import texture_model  # noqa: E402

NS = "tudursguns"
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", NS)
DATA = os.path.join(ROOT, "src", "main", "resources", "data", NS)

BRASS = (190, 150, 60)
COPPER = (168, 96, 56)
STEEL = (96, 100, 106)
GUNMETAL = (52, 54, 58)
OD = (86, 98, 60)
DARK_OD = (60, 70, 42)
YELLOW = (200, 170, 50)
WHITE = (220, 220, 214)

report = []


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def export(m, name, colors):
    """Textures, checks and writes a model: models/obj/ammo_<name>.obj and textures/handheld/ammo/<name>.png."""
    texture_model(m, (64, 64), colors=colors, flat="all")
    issues = check.report(m).get("issues") or []
    if issues:
        raise SystemExit(f"{name}: mesh issues {issues}")
    s = m.summary()
    report.append(f"{name:18s} tris={s['tris']:4d} size={s['size']}")
    obj = os.path.join(ASSETS, "models", "obj", f"ammo_{name}.obj")
    os.makedirs(os.path.dirname(obj), exist_ok=True)
    mk.save(m, obj)
    png = os.path.join(ASSETS, "textures", "handheld", "ammo", name + ".png")
    os.makedirs(os.path.dirname(png), exist_ok=True)
    Image.fromarray(m.texture).save(png)
    return f"{NS}:models/obj/ammo_{name}.obj", f"{NS}:textures/handheld/ammo/{name}.png"


def icon(m, name):
    """A 32x32 inventory icon rendered from the model (textures/handheld/ammo/<name>_icon.png)."""
    key = (255, 0, 255)
    img = view.render_view(m, "iso", 192, 192, mode="texture", refs=False, bg=key, zoom=1.15)
    alpha = np.where(np.all(img.astype(np.int16) == key, axis=2), 0, 255).astype(np.uint8)
    rgba = Image.fromarray(np.dstack([img, alpha]), "RGBA")
    box = rgba.getbbox()
    if box:
        rgba = rgba.crop(box)
    side = max(rgba.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(rgba, ((side - rgba.size[0]) // 2, (side - rgba.size[1]) // 2))
    square = square.resize((32, 32), Image.LANCZOS)
    path = os.path.join(ASSETS, "textures", "handheld", "ammo", name + "_icon.png")
    square.save(path)
    return f"{NS}:textures/handheld/ammo/{name}_icon.png"


def T(translation=(0.5, 0.5, 0.5), rotation=(0, 0, 0), scale=1.0):
    return {"translation": list(translation), "rotation": list(rotation), "scale": [scale] * 3}


# Display transforms for a small object 0.2 blocks across (the sample addon's magazines use the same),
# rescaled to each model's size.
SMALL_DISPLAY = (0.2, {
    "gui": T(rotation=(20, -30, 0), scale=2.8), "ground": T((0.5, 0.3, 0.5), scale=1.8), "fixed": T(scale=2.8),
    "thirdperson_righthand": T((0.5, 0.35, 0.55), scale=1.4), "thirdperson_lefthand": T((0.5, 0.35, 0.55), scale=1.4),
    "firstperson_righthand": T((0.6, 0.45, 0.45), (0, -20, 0), 2.0),
    "firstperson_lefthand": T((0.4, 0.45, 0.45), (0, 20, 0), 2.0)})


def display(m):
    base_dim, preset = SMALL_DISPLAY
    lo, hi = m.bbox()
    k = base_dim / float(np.max(hi - lo))
    return {ctx: {**t, "scale": [round(v * k, 3) for v in t["scale"]]} for ctx, t in preset.items()}


def box(cx, cy, cz, sx, sy, sz, tag, M=None):
    return mk.box(cx, cy, cz, sx, sy, sz, tag=tag, M=M, pivot=(cx, cy, cz) if M is not None else None)


def tube_y(x, z, y0, y1, r, tag, n=10):
    return mk.cylinder((x, y0, z), (x, y1, z), r, n=n, tag=tag)


def item(i, count=1):
    return {"item": i, "count": count} if count != 1 else {"item": i}


def ammo(name, display_name, m, colors, rounds, max_stack, ingredients, count, full_magazine=False):
    model, tex = export(m, name, colors)
    definition = {"display_name": display_name}
    if full_magazine:
        definition["full_magazine"] = True
    else:
        definition["rounds"] = rounds
    definition.update({"max_stack": max_stack, "model": model, "texture": tex, "icon": icon(m, name),
                       "display": display(m)})
    write_json(os.path.join(DATA, "ammo", name + ".json"), definition)
    write_json(os.path.join(DATA, "gun_recipe", "ammo_" + name + ".json"),
               {"category": "ammo", "result": {"ammo": f"{NS}:{name}", "count": count}, "ingredients": ingredients})


def cartridge(m, x, z, length, r, case_tag="brass", tip_tag="tip"):
    """A rifle/pistol cartridge standing on y = 0: case, then a tapered bullet."""
    case = length * 0.68
    m.add("rounds", tube_y(x, z, 0.0, case, r, case_tag, n=8))
    m.add("rounds", mk.lathe((x, case, z), (0, 1, 0), [(0.0, r * 0.82), (length - case, 0.0)], n=8, tag=tip_tag))


# ============================================================================ the items
def rifle_clip():
    """Five rifle cartridges held by their rims in a stripper clip."""
    m = mk.Model("rifle_clip")
    m.add("clip", box(0, 0.003, 0, 0.11, 0.006, 0.022, "clip"))
    for i in range(5):
        cartridge(m, -0.044 + i * 0.022, 0.0, 0.085, 0.0095)
    ammo("rifle_clip", "Rifle Clip (5)", m, {"clip": STEEL, "brass": BRASS, "tip": COPPER}, 5, 16,
         [item("minecraft:iron_nugget", 3), item("minecraft:copper_ingot"), item("minecraft:gunpowder")], 2)


def pistol_rounds():
    """Six pistol cartridges in a speedloader (the revolver's six chambers in one go)."""
    m = mk.Model("pistol_rounds")
    m.add("loader", tube_y(0, 0, 0.0, 0.014, 0.034, "loader", n=12))
    m.add("loader", tube_y(0, 0, 0.014, 0.03, 0.009, "knob", n=8))
    for i in range(6):
        a = np.radians(i * 60.0)
        cartridge(m, float(np.cos(a) * 0.022), float(np.sin(a) * 0.022), 0.05, 0.0085)
    ammo("pistol_rounds", "Pistol Rounds (6)", m, {"loader": GUNMETAL, "knob": STEEL, "brass": BRASS, "tip": COPPER}, 6, 16,
         [item("minecraft:iron_nugget", 3), item("minecraft:gunpowder")], 2)


def launcher_grenade():
    """A 40 mm grenade: a short brass case under an olive drab ogive."""
    m = mk.Model("launcher_grenade")
    m.add("case", tube_y(0, 0, 0.0, 0.034, 0.021, "brass", n=12))
    m.add("case", tube_y(0, 0, 0.0, 0.004, 0.023, "brass", n=12))
    m.add("head", mk.lathe((0, 0.034, 0), (0, 1, 0), [(0.0, 0.021), (0.012, 0.02), (0.026, 0.013), (0.034, 0.004),
                                                       (0.036, 0.0)], n=12, tag="head"))
    m.add("head", tube_y(0, 0, 0.038, 0.042, 0.0212, "band", n=12))
    ammo("launcher_grenade", "40mm Grenade", m, {"brass": BRASS, "head": OD, "band": YELLOW}, 1, 8,
         [item("minecraft:iron_ingot"), item("minecraft:copper_ingot"), item("minecraft:gunpowder", 2)], 2)


def rocket():
    """A rocket for shoulder launchers: motor tube with fins, then an olive drab warhead."""
    m = mk.Model("rocket")
    m.add("motor", tube_y(0, 0, 0.03, 0.2, 0.018, "motor", n=12))
    m.add("motor", tube_y(0, 0, 0.0, 0.03, 0.012, "nozzle", n=10))
    for k in range(4):
        m.add("fins", box(0, 0.03, 0, 0.002, 0.05, 0.06, "fin", M=mk.rot_y(45 + k * 90)))
    m.add("warhead", mk.lathe((0, 0.2, 0), (0, 1, 0), [(0.0, 0.026), (0.07, 0.026), (0.12, 0.012), (0.14, 0.0)],
                              n=12, tag="warhead"))
    m.add("warhead", tube_y(0, 0, 0.19, 0.205, 0.0265, "band", n=12))
    ammo("rocket", "Rocket", m, {"motor": DARK_OD, "nozzle": GUNMETAL, "fin": DARK_OD, "warhead": OD, "band": YELLOW}, 1, 4,
         [item("minecraft:iron_ingot", 2), item("minecraft:gunpowder", 2), item("minecraft:tnt")], 1)


def generic():
    """An ammunition can: what weapons without ammo settings of their own reload from, a magazine per can."""
    m = mk.Model("generic")
    m.add("can", box(0, 0.045, 0, 0.16, 0.09, 0.075, "can"))
    m.add("can", box(0, 0.0935, 0, 0.166, 0.007, 0.081, "lid"))
    m.add("can", box(0, 0.045, 0.0385, 0.16, 0.018, 0.002, "stripe"))
    m.add("handle", box(0, 0.102, 0, 0.07, 0.008, 0.012, "handle"))
    m.add("latch", box(0.07, 0.08, 0, 0.012, 0.03, 0.084, "latch"))
    ammo("generic", "Universal Ammunition", m, {"can": OD, "lid": DARK_OD, "stripe": YELLOW, "handle": GUNMETAL,
                                                 "latch": STEEL}, 0, 16,
         [item("minecraft:iron_ingot"), item("minecraft:copper_ingot"), item("minecraft:gunpowder", 2)], 1,
         full_magazine=True)


if __name__ == "__main__":
    rifle_clip()
    pistol_rounds()
    launcher_grenade()
    rocket()
    generic()
    print("\n".join(report))
