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
        json.dump(obj, f, indent=2, ensure_ascii=False)
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


def range_finder_item():
    """Grey laser rangefinder with a lens and a red trigger."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(5, 11):
        for x in range(2, 13):
            edge = y in (5, 10) or x in (2, 12)
            img.putpixel((x, y), (70, 74, 80, 255) if edge else (120, 126, 134, 255))
    for y in range(6, 10):
        for x in range(12, 15):
            img.putpixel((x, y), (40, 40, 46, 255))
    img.putpixel((13, 7), (120, 200, 255, 255))
    img.putpixel((13, 8), (80, 160, 230, 255))
    for x in range(3, 6):
        img.putpixel((x, 4), (90, 94, 100, 255))
    img.putpixel((8, 4), (210, 40, 40, 255))
    img.putpixel((9, 4), (210, 40, 40, 255))
    for y in range(11, 14):
        img.putpixel((5, y), (70, 74, 80, 255))
        img.putpixel((6, y), (90, 94, 100, 255))
    return img


def oob_stake_block():
    """White painted stake, a little darker at the bottom where it sits in the ground."""
    rnd = random.Random(21)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            base = (236, 236, 232) if y < 12 else (200, 196, 186)
            img.putpixel((x, y), shade(base, rnd.randint(-6, 4)) + (255,))
    return img


def oob_stake_item():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(1, 16):
        for x in (7, 8):
            c = (240, 240, 236) if y < 13 else (190, 186, 176)
            img.putpixel((x, y), shade(c, -25 if x == 8 else 0) + (255,))
    return img


CART_ZONES = {  # top-left of each 128x64 zone in the cart texture -> colour, matches GolfCartModel
    (0, 0): (236, 230, 208),     # body
    (128, 0): (46, 112, 64),     # seat
    (0, 64): (34, 34, 38),       # tyres, steering wheel
    (128, 64): (244, 244, 238),  # roof
    (0, 128): (122, 74, 42),     # bag
    (128, 128): (186, 188, 194), # posts, club heads
}


def golf_cart_texture():
    """256x256 entity texture made of single-colour zones with a little noise."""
    rnd = random.Random(31)
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    for (zx, zy), rgb in CART_ZONES.items():
        for y in range(zy, zy + 64):
            for x in range(zx, zx + 128):
                c = rgb
                if (zx, zy) == (128, 64) and (y - zy) % 6 < 2:
                    c = (60, 140, 80)  # green stripes on the roof
                img.putpixel((x, y), shade(c, rnd.randint(-6, 6)) + (255,))
    return img


def golf_cart_item():
    """Side view: cream body, green seat, white roof on posts, black wheels."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(1, 15):
        img.putpixel((x, 3), (244, 244, 238, 255))
        img.putpixel((x, 4), (60, 140, 80, 255))
    for y in range(5, 10):
        img.putpixel((2, y), (186, 188, 194, 255))
        img.putpixel((13, y), (186, 188, 194, 255))
    for x in range(5, 11):
        img.putpixel((x, 8), (46, 112, 64, 255))
        img.putpixel((9, 6), (46, 112, 64, 255))
        img.putpixel((9, 7), (46, 112, 64, 255))
    for y in range(9, 12):
        for x in range(1, 15):
            img.putpixel((x, y), shade((236, 230, 208), -12 if y == 11 else 0) + (255,))
    for cx in (4, 11):
        for y in range(11, 15):
            for x in range(cx - 1, cx + 2):
                img.putpixel((x, y), (34, 34, 38, 255))
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
    save(range_finder_item(), f"{i}/range_finder.png")
    save(oob_stake_block(), f"{b}/oob_stake.png")
    save(oob_stake_item(), f"{i}/oob_stake.png")
    save(golf_cart_item(), f"{i}/golf_cart.png")
    save(golf_cart_texture(), f"{ASSETS}/textures/entity/golf_cart.png")
    save(icon().resize((128, 128), Image.NEAREST), f"{ASSETS}/icon.png")
    save(icon().resize((512, 512), Image.NEAREST), "docs/publish/icon-512.png")


# ---------------------------------------------------------------- models

TURF = ["fairway", "rough", "green"]
ITEMS = ["golf_ball", "driver", "iron", "wedge", "putter", "golf_bag", "course_book", "range_finder", "golf_cart"]
BLOCKS = TURF + ["bunker_sand", "cup", "tee_box", "flag", "oob_stake"]
# Blocks whose item shows a flat item texture instead of the block model
FLAT_ITEM_BLOCKS = ["flag", "oob_stake"]


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

    stake = {"from": [7, 0, 7], "to": [9, 14, 9], "faces": {
        f: {"uv": [7, 2, 9, 16], "texture": "#stake"} for f in ("north", "south", "east", "west")}}
    stake["faces"]["up"] = {"uv": [7, 7, 9, 9], "texture": "#stake"}
    stake["faces"]["down"] = {"uv": [7, 7, 9, 9], "texture": "#stake", "cullface": "down"}
    write_json(f"{ASSETS}/models/block/oob_stake.json", {
        "textures": {"particle": f"{NS}:block/oob_stake", "stake": f"{NS}:block/oob_stake"},
        "elements": [stake]})

    for b in BLOCKS:
        write_json(f"{ASSETS}/blockstates/{b}.json", {"variants": {"": {"model": f"{NS}:block/{b}"}}})
    # The hole sign looks like a birch sign; same rotation table as vanilla
    write_json(f"{ASSETS}/blockstates/hole_sign.json", {"variants": {
        f"rotation={r}": dict({"model": f"minecraft:block/birch_sign_rot_{r % 4}"}, **({"y": r // 4 * 90} if r >= 4 else {}))
        for r in range(16)}})
    write_json(f"{ASSETS}/items/hole_sign.json", {"model": {"type": "minecraft:model", "model": "minecraft:item/birch_sign"}})

    for it in ITEMS + FLAT_ITEM_BLOCKS:
        parent = "minecraft:item/handheld" if it in ("driver", "iron", "wedge", "putter") else "minecraft:item/generated"
        write_json(f"{ASSETS}/models/item/{it}.json", {"parent": parent, "textures": {"layer0": f"{NS}:item/{it}"}})

    for it in ITEMS:
        model = {"type": "minecraft:model", "model": f"{NS}:item/{it}"}
        if it == "golf_ball":
            model["tints"] = [{"type": "minecraft:dye", "default": -1}]
        write_json(f"{ASSETS}/items/{it}.json", {"model": model})
    for b in BLOCKS:
        model = f"{NS}:item/{b}" if b in FLAT_ITEM_BLOCKS else f"{NS}:block/{b}"
        write_json(f"{ASSETS}/items/{b}.json", {"model": {"type": "minecraft:model", "model": model}})


# ---------------------------------------------------------------- data

def loot_tables():
    for b in BLOCKS + ["hole_sign"]:
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
    # 26.3 decides from this tag whether a block counts as ground for heightmaps (mobs, rain, placement)
    write_json(f"{RES}/data/minecraft/tags/block/blocks_motion_no_leaves.json", {
        "replace": False, "values": [f"{NS}:{b}" for b in TURF + ["cup", "bunker_sand"]]})
    write_json(f"{RES}/data/minecraft/tags/block/mineable/axe.json", {"replace": False, "values": [f"{NS}:hole_sign"]})
    write_json(f"{DATA}/tags/item/clubs.json", {"values": [f"{NS}:{c}" for c in ("driver", "iron", "wedge", "putter")]})


def shaped(name, pattern, key, result, count=1, category="equipment"):
    write_json(f"{DATA}/recipe/{name}.json", {
        "type": "minecraft:crafting_shaped", "category": category, "key": key, "pattern": pattern,
        "result": {"id": f"{NS}:{result}", "count": count}})


def shapeless(name, ingredients, result, count=1, category="misc"):
    write_json(f"{DATA}/recipe/{name}.json", {
        "type": "minecraft:crafting_shapeless", "category": category, "ingredients": ingredients,
        "result": {"id": f"{NS}:{result}", "count": count}})


# Recipe -> item whose pickup unlocks it in the recipe book
UNLOCKED_BY = {
    "golf_ball": "minecraft:slime_ball", "golf_ball_dyed": f"{NS}:golf_ball",
    "driver": "minecraft:stick", "iron": "minecraft:stick", "wedge": "minecraft:stick", "putter": "minecraft:stick",
    "golf_bag": "minecraft:leather", "course_book": "minecraft:book",
    "tee_box": "minecraft:green_carpet", "flag": "minecraft:red_wool",
    "fairway": "minecraft:grass_block", "rough": "minecraft:grass_block", "green": f"{NS}:fairway",
    "bunker_sand": "minecraft:sand", "cup": f"{NS}:green",
    "range_finder": "minecraft:redstone", "hole_sign": "minecraft:birch_sign", "oob_stake": "minecraft:white_dye",
    "golf_cart": "minecraft:minecart",
}


def recipe_unlocks():
    for name, item in UNLOCKED_BY.items():
        recipe = f"{NS}:{name}"
        write_json(f"{DATA}/advancement/recipes/{name}.json", {
            "parent": "minecraft:recipes/root",
            "criteria": {
                "has_item": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item}]}},
                "has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipes": recipe}},
            },
            "requirements": [["has_the_recipe", "has_item"]],
            "rewards": {"recipes": [recipe]},
        })


# name, parent, icon, frame, title, description. Granted from GolfAdvancements in code
ADVANCEMENTS = [
    ("fore", "root", f"{NS}:driver", "task", "Fore!", "Hit your first golf shot"),
    ("course_architect", "root", f"{NS}:course_book", "task", "Course Architect", "Add a hole to a course with the Course Book"),
    ("fully_equipped", "root", f"{NS}:golf_bag", "task", "Fully Equipped", "Close a golf bag holding all four clubs"),
    ("long_drive", "fore", f"{NS}:driver", "goal", "Long Drive", "Drive the ball 70 blocks or more"),
    ("splash", "fore", "minecraft:water_bucket", "task", "Splash!", "Hit your ball into water"),
    ("fish_food", "splash", "minecraft:cod", "goal", "Fish Food", "Feed the fish 100 golf balls"),
    ("hot_shot", "fore", "minecraft:lava_bucket", "task", "Hot Shot", "Hit your ball into lava"),
    ("overthinker", "fore", "minecraft:clock", "task", "Overthinker", "Hold your swing too long 10 times"),
    ("quitter", "fore", "minecraft:white_banner", "task", "Quitter", "Give up a hole with /minegolf pickup"),
    ("beach_day", "fore", f"{NS}:bunker_sand", "task", "Beach Day", "Leave your ball in a bunker"),
    ("sand_wizard", "beach_day", f"{NS}:wedge", "challenge", "Sand Wizard", "Hole out straight from a bunker, at least 20 blocks away"),
    ("in_the_cup", "fore", f"{NS}:cup", "task", "In the Cup", "Hole out during a round"),
    ("snowman", "in_the_cup", "minecraft:snowball", "task", "Snowman", "Take 8 strokes on a single hole"),
    ("birdie", "in_the_cup", "minecraft:feather", "task", "Birdie", "Finish a hole one under par"),
    ("eagle", "birdie", "minecraft:elytra", "challenge", "Eagle", "Finish a hole two under par"),
    ("hole_in_one", "in_the_cup", f"{NS}:golf_ball", "challenge", "Hole in One", "Sink your tee shot on a hole at least 20 blocks long"),
    ("full_18", "in_the_cup", f"{NS}:flag", "challenge", "Full 18", "Finish an 18-hole round"),
    ("under_par", "birdie", "minecraft:gold_ingot", "challenge", "Under Par", "Finish a round of 9 or more holes under par"),
    ("perfect_round", "under_par", "minecraft:nether_star", "challenge", "Perfect Round", "Play every hole of an 18-hole round under par"),
]
XP = {"challenge": 100, "perfect_round": 500}


def advancements():
    def display(name, icon, frame, root=False):
        d = {
            "icon": {"id": icon},
            "title": {"translate": f"advancements.{NS}.{name}.title"},
            "description": {"translate": f"advancements.{NS}.{name}.description"},
            "frame": frame,
            "show_toast": not root,
            "announce_to_chat": not root,
        }
        if root:
            d["background"] = f"{NS}:block/fairway_top"
        return d

    held = {"golf_ball": f"{NS}:golf_ball", "club": f"#{NS}:clubs", "bag": f"{NS}:golf_bag", "book": f"{NS}:course_book"}
    write_json(f"{DATA}/advancement/golf/root.json", {
        "display": display("root", f"{NS}:flag", "task", root=True),
        "criteria": {k: {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": v}]}}
                     for k, v in held.items()},
        "requirements": [list(held)],
    })
    for name, parent, icon, frame, _, _ in ADVANCEMENTS:
        adv = {
            "parent": f"{NS}:golf/{parent}",
            "display": display(name, icon, frame),
            "criteria": {"done": {"trigger": "minecraft:impossible"}},
            "requirements": [["done"]],
        }
        xp = XP.get(name, XP["challenge"] if frame == "challenge" else 0)
        if xp:
            adv["rewards"] = {"experience": xp}
        write_json(f"{DATA}/advancement/golf/{name}.json", adv)

    lang_path = f"{ASSETS}/lang/en_us.json"
    with open(lang_path, encoding="utf-8") as f:
        lang = json.load(f)
    lang = {k: v for k, v in lang.items() if not k.startswith(f"advancements.{NS}.")}
    lang[f"advancements.{NS}.root.title"] = "MineGolf"
    lang[f"advancements.{NS}.root.description"] = "Grab a club and hit the links"
    for name, _, _, _, title, desc in ADVANCEMENTS:
        lang[f"advancements.{NS}.{name}.title"] = title
        lang[f"advancements.{NS}.{name}.description"] = desc
    write_json(lang_path, lang)


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
    shaped("range_finder", ["IGI", "IRI"], {"I": "minecraft:iron_ingot", "G": "minecraft:glass_pane", "R": "minecraft:redstone"},
           "range_finder")
    shapeless("hole_sign", ["minecraft:birch_sign", "minecraft:paper"], "hole_sign", 1, "building")
    shaped("oob_stake", ["D", "S", "S"], {"D": "minecraft:white_dye", "S": stick}, "oob_stake", 4, "building")
    shaped("golf_cart", ["WWW", "IMI"], {"W": "minecraft:white_wool", "I": "minecraft:iron_ingot", "M": "minecraft:minecart"},
           "golf_cart")


if __name__ == "__main__":
    textures()
    models()
    loot_tables()
    tags()
    recipes()
    recipe_unlocks()
    advancements()
    print("generated")
