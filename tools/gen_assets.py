"""Generates MineGolf textures, models, blockstates, loot tables, tags, recipes and lang.

Run from the repo root: python tools/gen_assets.py
Textures are simple procedural 16x16 pixel art with a fixed seed, so output is stable.
"""
import json
import os
import random

from PIL import Image

NS = "minegolf"
RES = "src/main/resources"
ASSETS = f"{RES}/assets/{NS}"
DATA = f"{RES}/data/{NS}"


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def shade(rgb, amount):
    return tuple(max(0, min(255, c + amount)) for c in rgb)


# ---------------------------------------------------------------- textures

def noise_tile(base, spread, seed, stripes=None):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade(base, rnd.randint(-spread, spread))
            if stripes and (x // stripes) % 2 == 0:
                c = shade(c, 10)
            img.putpixel((x, y), c + (255,))
    return img


def side_tile(top_rgb, seed):
    """Dirt with a turf edge on top, like a grass block side."""
    rnd = random.Random(seed)
    dirt = (134, 96, 67)
    img = Image.new("RGBA", (16, 16))
    for x in range(16):
        depth = 3 + rnd.randint(0, 2)
        for y in range(16):
            c = shade(top_rgb, rnd.randint(-12, 12)) if y < depth else shade(dirt, rnd.randint(-14, 14))
            img.putpixel((x, y), c + (255,))
    return img


def cup_top():
    img = noise_tile((96, 188, 72), 8, 11)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 3.2:
                img.putpixel((x, y), (20, 20, 20, 255))
            elif d < 4.0:
                img.putpixel((x, y), (235, 235, 235, 255))
    return img


def tee_box():
    img = noise_tile((80, 160, 70), 6, 21)
    for y in (3, 4, 11, 12):
        for x in (3, 4, 11, 12):
            img.putpixel((x, y), (245, 245, 245, 255))
    return img


def flag_cloth():
    img = Image.new("RGBA", (16, 16), (210, 30, 30, 255))
    rnd = random.Random(31)
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade((210, 30, 30), rnd.randint(-15, 10)) + (255,))
    return img


def flag_pole():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), (240, 240, 240, 255) if x % 4 else (200, 200, 200, 255))
    return img


def ball_item():
    """Greyscale so the dye tint colors it."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 5.5:
                light = 255 - int(d * 9)
                if (x + y) % 3 == 0 and d < 4.5:
                    light -= 25
                img.putpixel((x, y), (light, light, light, 255))
            elif d < 6.3:
                img.putpixel((x, y), (150, 150, 150, 255))
    return img


def club_item(head_rgb, head_shape):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    grip = (40, 40, 40, 255)
    shaft = (190, 190, 200, 255)
    # diagonal shaft from top right to bottom left
    for i in range(2, 13):
        x, y = 14 - i, i
        img.putpixel((x, y), grip if i < 6 else shaft)
        if i < 6:
            img.putpixel((x + 1, y), grip)
    for (x, y) in head_shape:
        img.putpixel((x, y), head_rgb + (255,))
    return img


DRIVER_HEAD = [(x, y) for x in range(0, 5) for y in range(12, 16) if not (x == 0 and y in (12, 15))]
IRON_HEAD = [(x, y) for x in range(0, 5) for y in range(13, 15)] + [(0, 15), (1, 15), (2, 15)]
WEDGE_HEAD = [(x, y) for x in range(0, 4) for y in range(12, 15) if x <= y - 11] + [(0, 15), (1, 15), (2, 15), (3, 15)]
PUTTER_HEAD = [(x, 14) for x in range(0, 6)] + [(x, 15) for x in range(0, 6)]


def bag_item():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    body = (120, 70, 40)
    for y in range(5, 16):
        for x in range(4, 12):
            c = shade(body, -20 if x in (4, 11) else 0)
            img.putpixel((x, y), c + (255,))
    for y in range(8, 10):
        for x in range(4, 12):
            img.putpixel((x, y), (230, 230, 230, 255))
    # club heads sticking out
    for x, c in ((5, (139, 90, 43)), (7, (200, 200, 210)), (9, (200, 200, 210)), (10, (150, 100, 60))):
        for y in range(1, 5):
            img.putpixel((x, y), (190, 190, 200, 255))
        img.putpixel((x, 0), c + (255,))
    return img


def book_item():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(2, 15):
        for x in range(3, 13):
            img.putpixel((x, y), (40, 130, 60, 255) if x > 3 else (25, 90, 40, 255))
    for y in range(3, 14):
        img.putpixel((12, y), (240, 235, 220, 255))
    # little flag on the cover
    for y in range(5, 12):
        img.putpixel((7, y), (240, 240, 240, 255))
    for y in range(5, 8):
        for x in range(8, 11):
            img.putpixel((x, y), (210, 30, 30, 255))
    return img


def flag_item():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(1, 16):
        img.putpixel((5, y), (240, 240, 240, 255))
    for y in range(1, 7):
        for x in range(6, 13):
            img.putpixel((x, y), (210, 30, 30, 255))
    return img


GREEN_RGB = (96, 188, 72)
FAIRWAY_RGB = (90, 165, 60)
ROUGH_RGB = (58, 112, 40)


def icon():
    """32x32 mod icon: green with cup and flag, ball in front, sky behind."""
    rnd = random.Random(11)
    img = Image.new("RGBA", (32, 32))
    for y in range(32):
        for x in range(32):
            if y < 17:
                c = shade((120, 190, 240), (17 - y) * -3 + 20)
            elif y < 19:
                c = shade(ROUGH_RGB, rnd.randint(-10, 10))
            else:
                c = shade(GREEN_RGB if (x // 4 + y // 4) % 2 else FAIRWAY_RGB, rnd.randint(-5, 5))
            img.putpixel((x, y), c + (255,))
    for (x, y) in [(5, 4), (6, 4), (7, 4), (4, 5), (5, 5), (6, 5), (7, 5), (8, 5), (6, 3), (10, 6), (11, 6), (12, 6)]:
        img.putpixel((x, y), (250, 250, 255, 255))
    # cup with flag
    for x in range(20, 26):
        img.putpixel((x, 22), (30, 30, 30, 255))
        img.putpixel((x, 23), (20, 20, 20, 255))
    for y in range(3, 23):
        img.putpixel((22, y), (240, 240, 240, 255))
    for y in range(3, 10):
        for x in range(23, 23 + 8 - abs(y - 6)):
            img.putpixel((x, y), (210 - (x - 23) * 6, 30, 30, 255))
    # ball with shadow
    for y in range(20, 32):
        for x in range(2, 16):
            d = ((x - 8.5) ** 2 + (y - 25.5) ** 2) ** 0.5
            if d < 4.6:
                light = 255 - int(d * 12)
                if (x + y) % 3 == 0 and d < 3.5:
                    light -= 22
                img.putpixel((x, y), (light, light, light, 255))
            elif ((x - 10) ** 2 / 30 + (y - 30) ** 2 / 2) < 1 and y > 27:
                img.putpixel((x, y), shade(img.getpixel((x, y))[:3], -40) + (255,))
    return img


def textures():
    b = f"{ASSETS}/textures/block"
    i = f"{ASSETS}/textures/item"
    save(noise_tile(FAIRWAY_RGB, 8, 1, stripes=4), f"{b}/fairway_top.png")
    save(side_tile(FAIRWAY_RGB, 2), f"{b}/fairway_side.png")
    save(noise_tile(ROUGH_RGB, 22, 3), f"{b}/rough_top.png")
    save(side_tile(ROUGH_RGB, 4), f"{b}/rough_side.png")
    save(noise_tile(GREEN_RGB, 5, 5), f"{b}/green_top.png")
    save(side_tile(GREEN_RGB, 6), f"{b}/green_side.png")
    save(noise_tile((232, 220, 168), 10, 7), f"{b}/bunker_sand.png")
    save(cup_top(), f"{b}/cup_top.png")
    save(tee_box(), f"{b}/tee_box.png")
    save(flag_cloth(), f"{b}/flag_cloth.png")
    save(flag_pole(), f"{b}/flag_pole.png")
    save(ball_item(), f"{i}/golf_ball.png")
    save(club_item((139, 90, 43), DRIVER_HEAD), f"{i}/driver.png")
    save(club_item((200, 200, 210), IRON_HEAD), f"{i}/iron.png")
    save(club_item((170, 170, 185), WEDGE_HEAD), f"{i}/wedge.png")
    save(club_item((205, 120, 70), PUTTER_HEAD), f"{i}/putter.png")
    save(bag_item(), f"{i}/golf_bag.png")
    save(book_item(), f"{i}/course_book.png")
    save(flag_item(), f"{i}/flag.png")
    save(icon().resize((128, 128), Image.NEAREST), f"{ASSETS}/icon.png")
    save(icon().resize((512, 512), Image.NEAREST), "docs/publish/icon-512.png")


# ---------------------------------------------------------------- models

TURF = ["fairway", "rough", "green"]
ITEMS = ["golf_ball", "driver", "iron", "wedge", "putter", "golf_bag", "course_book"]
BLOCKS = TURF + ["bunker_sand", "cup", "tee_box", "flag"]


def models():
    for t in TURF:
        write_json(f"{ASSETS}/models/block/{t}.json", {
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {"top": f"{NS}:block/{t}_top", "side": f"{NS}:block/{t}_side", "bottom": "minecraft:block/dirt"}})
    write_json(f"{ASSETS}/models/block/cup.json", {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {"top": f"{NS}:block/cup_top", "side": f"{NS}:block/green_side", "bottom": "minecraft:block/dirt"}})
    write_json(f"{ASSETS}/models/block/bunker_sand.json", {
        "parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/bunker_sand"}})
    write_json(f"{ASSETS}/models/block/tee_box.json", {
        "parent": "minecraft:block/carpet", "textures": {"wool": f"{NS}:block/tee_box"}})

    pole = {"from": [7.5, 0, 7.5], "to": [8.5, 32, 8.5], "faces": {
        f: {"uv": [7, 0, 8, 16], "texture": "#pole"} for f in ("north", "south", "east", "west")}}
    pole["faces"]["up"] = {"uv": [7, 7, 8, 8], "texture": "#pole"}
    pole["faces"]["down"] = {"uv": [7, 7, 8, 8], "texture": "#pole", "cullface": "down"}
    cloth = {"from": [8.5, 23, 8], "to": [17.5, 31, 8], "faces": {
        "north": {"uv": [0, 0, 9, 8], "texture": "#cloth"},
        "south": {"uv": [0, 0, 9, 8], "texture": "#cloth"}}}
    write_json(f"{ASSETS}/models/block/flag.json", {
        "ambientocclusion": False,
        "textures": {"particle": f"{NS}:block/flag_cloth", "pole": f"{NS}:block/flag_pole", "cloth": f"{NS}:block/flag_cloth"},
        "elements": [pole, cloth]})

    for b in BLOCKS:
        write_json(f"{ASSETS}/blockstates/{b}.json", {"variants": {"": {"model": f"{NS}:block/{b}"}}})

    for it in ITEMS + ["flag"]:
        parent = "minecraft:item/handheld" if it in ("driver", "iron", "wedge", "putter") else "minecraft:item/generated"
        write_json(f"{ASSETS}/models/item/{it}.json", {"parent": parent, "textures": {"layer0": f"{NS}:item/{it}"}})

    for it in ITEMS:
        model = {"type": "minecraft:model", "model": f"{NS}:item/{it}"}
        if it == "golf_ball":
            model["tints"] = [{"type": "minecraft:dye", "default": -1}]
        write_json(f"{ASSETS}/items/{it}.json", {"model": model})
    for b in BLOCKS:
        model = f"{NS}:item/flag" if b == "flag" else f"{NS}:block/{b}"
        write_json(f"{ASSETS}/items/{b}.json", {"model": {"type": "minecraft:model", "model": model}})


# ---------------------------------------------------------------- data

def loot_tables():
    for b in BLOCKS:
        write_json(f"{DATA}/loot_table/blocks/{b}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                       "entries": [{"type": "minecraft:item", "name": f"{NS}:{b}"}]}],
            "random_sequence": f"{NS}:blocks/{b}"})


def tags():
    surf = f"{DATA}/tags/block/surface"
    write_json(f"{surf}/green.json", {"values": [f"{NS}:green", f"{NS}:cup"]})
    write_json(f"{surf}/fairway.json", {"values": [f"{NS}:fairway", f"{NS}:tee_box"]})
    write_json(f"{surf}/rough.json", {"values": [f"{NS}:rough"]})
    write_json(f"{surf}/bunker.json", {"values": [f"{NS}:bunker_sand", "#minecraft:sand"]})
    write_json(f"{surf}/ice.json", {"values": ["#minecraft:ice"]})
    write_json(f"{RES}/data/minecraft/tags/block/mineable/shovel.json", {
        "replace": False, "values": [f"{NS}:{b}" for b in TURF + ["cup", "bunker_sand"]]})
    write_json(f"{DATA}/tags/item/clubs.json", {"values": [f"{NS}:{c}" for c in ("driver", "iron", "wedge", "putter")]})


def shaped(name, pattern, key, result, count=1, category="equipment"):
    write_json(f"{DATA}/recipe/{name}.json", {
        "type": "minecraft:crafting_shaped", "category": category, "key": key, "pattern": pattern,
        "result": {"id": f"{NS}:{result}", "count": count}})


def shapeless(name, ingredients, result, count=1, category="misc"):
    write_json(f"{DATA}/recipe/{name}.json", {
        "type": "minecraft:crafting_shapeless", "category": category, "ingredients": ingredients,
        "result": {"id": f"{NS}:{result}", "count": count}})


def recipes():
    shapeless("golf_ball", ["minecraft:bone_meal", "minecraft:slime_ball", "minecraft:bone_meal"], "golf_ball", 2)
    write_json(f"{DATA}/recipe/golf_ball_dyed.json", {
        "type": "minecraft:crafting_dye", "dye": "#minecraft:dyes", "group": "dyed_golf_ball",
        "result": {"id": f"{NS}:golf_ball"}, "target": f"{NS}:golf_ball"})
    stick = "minecraft:stick"
    shaped("driver", [" S", " S", "PP"], {"S": stick, "P": "#minecraft:planks"}, "driver")
    shaped("iron", [" S", " S", "II"], {"S": stick, "I": "minecraft:iron_ingot"}, "iron")
    shaped("wedge", [" S", " S", "I "], {"S": stick, "I": "minecraft:iron_ingot"}, "wedge")
    shaped("putter", [" S", " S", "CC"], {"S": stick, "C": "minecraft:copper_ingot"}, "putter")
    shaped("golf_bag", ["L-L", "L L", "LLL"], {"L": "minecraft:leather", "-": "minecraft:string"}, "golf_bag")
    shapeless("course_book", ["minecraft:book", "minecraft:green_dye"], "course_book")
    shaped("tee_box", ["CCC", "PPP"], {"C": "minecraft:green_carpet", "P": "#minecraft:planks"}, "tee_box", 6, "building")
    shaped("flag", ["W", "S", "S"], {"W": "minecraft:red_wool", "S": stick}, "flag", 1, "building")
    shapeless("fairway", ["minecraft:grass_block"] * 4 + ["minecraft:bone_meal"], "fairway", 4, "building")
    shapeless("rough", ["minecraft:grass_block"] * 4 + ["minecraft:short_grass"], "rough", 4, "building")
    shapeless("green", [f"{NS}:fairway"] * 4 + ["minecraft:bone_meal"], "green", 4, "building")
    shapeless("bunker_sand", ["minecraft:sand"] * 4 + ["minecraft:bone_meal"], "bunker_sand", 4, "building")
    shapeless("cup", [f"{NS}:green", "minecraft:iron_ingot"], "cup", 1, "building")


if __name__ == "__main__":
    textures()
    models()
    loot_tables()
    tags()
    recipes()
    print("generated")
