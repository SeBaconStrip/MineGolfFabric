"""Builds a 6-hole demo course ("MineGolf Links") into a superflat world.

Run with the game closed:  python tools/demo_course.py "run/saves/New World"

Writes a datapack that builds the course the next time the world is opened (once), and registers the
course in data/minegolf/courses.dat so it can be played right away. The builder gets a starter kit and a
Course Book bound to the course.
"""
import gzip
import io
import json
import math
import os
import random
import struct
import sys
import uuid

NS = "minegolf_demo"
G = -61  # top grass layer of a default superflat world
COURSE_NAME = "MineGolf Links"
COURSE_ID = uuid.UUID("6d1f1c52-8a4b-4c1e-9a77-3f0e2b5c9d10")

# Region the course occupies (and that gets cleared)
X1, X2, Z1, Z2 = -262, -8, 28, 232

# number, tee x, cup x, lane z, par, name, extras
HOLES = [
    (1, -70, -118, 45, 3, "Opener", {}),
    (2, -128, -238, 79, 4, "The Pond", {"pond": (40, 52)}),
    (3, -238, -206, 113, 3, "Island", {"island": True}),
    (4, -196, -26, 147, 5, "The Long One", {"creek": 76}),
    (5, -30, -118, 181, 4, "Lookout", {"hill": True}),
    (6, -128, -73, 215, 3, "Home", {"home": True}),
]

rnd = random.Random(7)
cmds = []


def fill(x1, y1, z1, x2, y2, z2, block, mode=""):
    cmds.append(f"fill {x1} {y1} {z1} {x2} {y2} {z2} {block}{' ' + mode if mode else ''}")


def setblock(x, y, z, block):
    cmds.append(f"setblock {x} {y} {z} {block}")


def ellipse(cx, cz, rx, rz, y, block, mode=""):
    """Filled ellipse as one fill per row."""
    for dz in range(-int(rz), int(rz) + 1):
        w = rx * math.sqrt(max(0.0, 1 - (dz / rz) ** 2)) if rz else rx
        if w < 0.5:
            continue
        fill(round(cx - w), y, cz + dz, round(cx + w), y, cz + dz, block, mode)


def lane_x(tee, d, s):
    return tee + d * s


# ------------------------------------------------------------------ holes

def build_hole(n, tee, cup, z, par, name, extra):
    d = 1 if cup > tee else -1
    length = abs(cup - tee)

    def rect(s1, s2, dz1, dz2, y, block, mode=""):
        a, b = lane_x(tee, d, s1), lane_x(tee, d, s2)
        fill(min(a, b), y, z + dz1, max(a, b), y, z + dz2, block, mode)

    rect(-8, length + 12, -11, 11, G, "minegolf:rough")
    if par >= 4:
        rect(10, length - 6, -5, 5, G, "minegolf:fairway")
    elif length > 40:
        rect(int(length * 0.45), length - 6, -4, 4, G, "minegolf:fairway")
    rect(-3, 3, -3, 3, G, "minegolf:fairway")  # tee area
    ellipse(cup, z, 7, 7, G, "minegolf:green")

    def bunker(s, dz, r):
        ellipse(lane_x(tee, d, s), z + dz, r, r * 0.8, G, "minegolf:bunker_sand")

    def water(cx, cz, rx, rz):
        ellipse(cx, cz, rx, rz, G, "minecraft:water")
        ellipse(cx, cz, rx, rz, G - 1, "minecraft:water")

    tee_y = G + 1
    if n == 1:
        bunker(length - 10, -4, 3)
        bunker(length + 5, 6, 2)
    if "pond" in extra:
        s1, s2 = extra["pond"]
        water(lane_x(tee, d, (s1 + s2) // 2), z, (s2 - s1) / 2, 13)
        rect(s1 - 2, s2 + 2, 9, 10, G, "minecraft:spruce_planks")  # footbridge
        bunker(length, 9, 3)
        bunker(length - 30, -6, 3)
    if extra.get("island"):
        # Water ring around the green, a footbridge behind it
        for dz in range(-11, 12):
            outer = math.sqrt(max(0, 11.5 ** 2 - dz ** 2))
            inner = math.sqrt(max(0, 7.5 ** 2 - dz ** 2)) if abs(dz) < 7.5 else None
            for y in (G, G - 1):
                if inner is None:
                    fill(round(cup - outer), y, z + dz, round(cup + outer), y, z + dz, "minecraft:water")
                else:
                    fill(round(cup - outer), y, z + dz, round(cup - inner) - 1, y, z + dz, "minecraft:water")
                    fill(round(cup + inner) + 1, y, z + dz, round(cup + outer), y, z + dz, "minecraft:water")
        rect(length + 7, length + 12, -1, 0, G, "minecraft:spruce_planks")
    if "creek" in extra:
        s = extra["creek"]
        rect(s, s + 2, -13, 13, G, "minecraft:water")
        rect(s, s + 2, -13, 13, G - 1, "minecraft:water")
        rect(s - 1, s + 3, 9, 10, G, "minecraft:spruce_planks")
        bunker(100, 7, 3)
        bunker(118, -7, 3)
        bunker(length - 9, 0, 2)
        bunker(length + 3, -8, 3)
    if extra.get("hill"):
        # Stepped mound under the tee, the tee box sits on top
        for h, r in enumerate((7, 6, 5, 4, 3), start=1):
            ellipse(tee, z, r, r, G + h, "minecraft:grass_block")
        ellipse(tee, z, 3, 3, G + 5, "minegolf:fairway")
        tee_y = G + 6
        bunker(58, -6, 3)
        bunker(64, 6, 2)
        bunker(length + 8, 0, 3)
    if extra.get("home"):
        bunker(length - 10, 0, 2)
        bunker(length, 8, 3)
        bunker(length + 9, 0, 3)
        water(lane_x(tee, d, length - 2), z - 13, 5, 3)

    setblock(tee, tee_y, z, "minegolf:tee_box")
    setblock(cup, G, z, "minegolf:cup")
    setblock(cup, G + 1, z, "minegolf:flag")

    label_x = tee - d * 3
    label_z = z - 3
    cmds.append(f'summon minecraft:text_display {label_x} {tee_y + 2.4} {label_z} '
                f'{{Tags:["{NS}"],billboard:"center",text:{{text:"Hole {n} · {name}",color:"gold",bold:true}}}}')
    cmds.append(f'summon minecraft:text_display {label_x} {tee_y + 2.0} {label_z} '
                f'{{Tags:["{NS}"],billboard:"center",text:{{text:"Par {par} · {length} blocks",color:"white"}}}}')
    return {"tee": (tee, tee_y, z), "cup": (cup, G, z), "par": par}


# ------------------------------------------------------------------ landscape

FLOWERS = ["minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:oxeye_daisy",
           "minecraft:azure_bluet", "minecraft:allium"]
TREES = ["minecraft:oak", "minecraft:birch", "minecraft:fancy_oak", "minecraft:oak", "minecraft:birch"]


def in_lane(x, z, margin):
    for _, tee, cup, lz, *_ in HOLES:
        lo, hi = min(tee, cup) - 12, max(tee, cup) + 12
        if lo - margin <= x <= hi + margin and abs(z - lz) <= 11 + margin:
            return True
    return False


def landscape():
    # Tree lines between the lanes and along the edges
    for row_z in [Z1 + 3] + [h[3] + 17 for h in HOLES]:
        x = X1 + 4 + rnd.randint(0, 5)
        while x < X2 - 3:
            z = row_z + rnd.randint(-2, 2)
            if Z1 + 2 <= z <= Z2 - 2 and not in_lane(x, z, 1):
                cmds.append(f"place feature {rnd.choice(TREES)} {x} {G + 1} {z}")
            x += rnd.randint(7, 12)
    # Small grassy mounds and flowers in the gaps
    for _ in range(40):
        x, z = rnd.randint(X1 + 3, X2 - 3), rnd.randint(Z1 + 3, Z2 - 3)
        if not in_lane(x, z, 3):
            ellipse(x, z, rnd.randint(2, 3), 2, G + 1, "minecraft:grass_block")
    for _ in range(500):
        x, z = rnd.randint(X1 + 1, X2 - 1), rnd.randint(Z1 + 1, Z2 - 1)
        if not in_lane(x, z, 0):
            block = rnd.choice(FLOWERS) if rnd.random() < 0.45 else "minecraft:short_grass"
            cmds.append(f"execute if block {x} {G} {z} minecraft:grass_block run setblock {x} {G + 1} {z} {block} keep")


def clubhouse():
    x1, z1, x2, z2 = -58, 36, -48, 44
    fill(x1, G, z1, x2, G, z2, "minecraft:spruce_planks")
    fill(x1, G + 1, z1, x2, G + 4, z2, "minecraft:oak_planks", "hollow")
    fill(x1, G + 1, z1, x1, G + 4, z1, "minecraft:spruce_log")
    fill(x2, G + 1, z1, x2, G + 4, z1, "minecraft:spruce_log")
    fill(x1, G + 1, z2, x1, G + 4, z2, "minecraft:spruce_log")
    fill(x2, G + 1, z2, x2, G + 4, z2, "minecraft:spruce_log")
    fill(x1 - 1, G + 5, z1 - 1, x2 + 1, G + 5, z2 + 1, "minecraft:dark_oak_slab")
    fill(-54, G + 1, z2, -52, G + 2, z2, "minecraft:air")  # doorway facing the first tee
    fill(-56, G + 2, z1, -50, G + 3, z1, "minecraft:glass_pane")
    setblock(-53, G + 1, z1 + 1, "minecraft:barrel")
    setblock(-55, G + 1, z1 + 1, "minecraft:lantern")
    fill(-54, G, z2 + 1, -52, G, 45, "minecraft:dirt_path")
    cmds.append(f'summon minecraft:text_display -53 {G + 6.5} {z2} '
                f'{{Tags:["{NS}"],billboard:"center",text:{{text:"{COURSE_NAME}",color:"green",bold:true}}}}')


# ------------------------------------------------------------------ functions

def write_pack(world, holes):
    base = os.path.join(world, "datapacks", NS)
    fn = os.path.join(base, "data", NS, "function")
    os.makedirs(fn, exist_ok=True)
    os.makedirs(os.path.join(base, "data", "minecraft", "tags", "function"), exist_ok=True)

    def write(path, text):
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(text)

    write(os.path.join(base, "pack.mcmeta"), json.dumps(
        {"pack": {"description": "MineGolf demo course", "min_format": 121, "max_format": 121}}, indent=2))
    write(os.path.join(base, "data", "minecraft", "tags", "function", "load.json"),
          json.dumps({"values": [f"{NS}:load"]}))

    cx1, cx2, cz1, cz2 = X1 >> 4, X2 >> 4, Z1 >> 4, Z2 >> 4
    loaded = " ".join(f"if loaded {cx * 16 + 8} {G} {cz * 16 + 8}"
                      for cx in range(cx1, cx2 + 1) for cz in range(cz1, cz2 + 1))

    write(os.path.join(fn, "load.mcfunction"),
          f"execute unless data storage {NS}:state {{started:1b}} run function {NS}:start\n")
    write(os.path.join(fn, "start.mcfunction"), "\n".join([
        f"data modify storage {NS}:state started set value 1b",
        f"forceload add {X1} {Z1} {X2} {Z2}",
        f"schedule function {NS}:wait 20t",
    ]) + "\n")
    write(os.path.join(fn, "wait.mcfunction"), "\n".join([
        f"execute {loaded} run function {NS}:build",
        f"execute unless data storage {NS}:state {{built:1b}} run schedule function {NS}:wait 20t",
    ]) + "\n")

    build = [f"data modify storage {NS}:state built set value 1b",
             f"kill @e[type=minecraft:text_display,tag={NS}]"]
    # Clear everything above the ground in 16x16 columns, then reset the grass layer
    for x in range(X1, X2 + 1, 16):
        for z in range(Z1, Z2 + 1, 16):
            build.append(f"fill {x} {G + 1} {z} {min(x + 15, X2)} {G + 30} {min(z + 15, Z2)} minecraft:air")
    for x in range(X1, X2 + 1, 128):
        for z in range(Z1, Z2 + 1, 128):
            build.append(f"fill {x} {G} {z} {min(x + 127, X2)} {G} {min(z + 127, Z2)} minecraft:grass_block")
    build += cmds
    build += [f"forceload remove {X1} {Z1} {X2} {Z2}", f"schedule function {NS}:welcome 20t"]
    write(os.path.join(fn, "build.mcfunction"), "\n".join(build) + "\n")

    ids = struct.unpack(">iiii", COURSE_ID.bytes)
    book = f"minegolf:course_book[minegolf:course_id=[I;{ids[0]},{ids[1]},{ids[2]},{ids[3]}]]"
    bag = ("minegolf:golf_bag[container=["
           + ",".join(f'{{slot:{i},item:{{id:"minegolf:{c}",count:1}}}}'
                      for i, c in enumerate(["driver", "iron", "wedge", "putter"]))
           + ',{slot:4,item:{id:"minegolf:golf_ball",count:16}}]]')
    tee1 = holes[0]["tee"]
    write(os.path.join(fn, "welcome.mcfunction"), "\n".join([
        f"execute unless entity @a run return run schedule function {NS}:welcome 20t",
        f"execute if data storage {NS}:state {{welcomed:1b}} run return 0",
        f"data modify storage {NS}:state welcomed set value 1b",
        f"give @a {book}",
        f"give @a {bag}",
        "give @a minegolf:putter",
        "give @a minegolf:golf_ball 8",
        f"tp @a {tee1[0] + 4} {G + 1} {tee1[2] + 2} 90 10",
        'tellraw @a {text:"Welcome to MineGolf Links! 6 holes, par 22.",color:"green"}',
        'tellraw @a {text:"Right-click the Course Book and press Start round, then tee off at hole 1.",color:"gray"}',
    ]) + "\n")
    return len(build)


# ------------------------------------------------------------------ course data (NBT)

def read_nbt(buf):
    def payload(t):
        if t == 1: return ("b", struct.unpack(">b", buf.read(1))[0])
        if t == 2: return ("s", struct.unpack(">h", buf.read(2))[0])
        if t == 3: return ("i", struct.unpack(">i", buf.read(4))[0])
        if t == 4: return ("l", struct.unpack(">q", buf.read(8))[0])
        if t == 5: return ("f", struct.unpack(">f", buf.read(4))[0])
        if t == 6: return ("d", struct.unpack(">d", buf.read(8))[0])
        if t == 7:
            n = struct.unpack(">i", buf.read(4))[0]
            return ("B", buf.read(n))
        if t == 8:
            n = struct.unpack(">H", buf.read(2))[0]
            return ("S", buf.read(n).decode("utf-8"))
        if t == 9:
            et = buf.read(1)[0]
            n = struct.unpack(">i", buf.read(4))[0]
            return ("L", et, [payload(et) for _ in range(n)])
        if t == 10:
            out = {}
            while True:
                tt = buf.read(1)[0]
                if tt == 0: return ("C", out)
                name = buf.read(struct.unpack(">H", buf.read(2))[0]).decode("utf-8")
                out[name] = payload(tt)
        if t == 11:
            n = struct.unpack(">i", buf.read(4))[0]
            return ("I", list(struct.unpack(f">{n}i", buf.read(4 * n))))
        if t == 12:
            n = struct.unpack(">i", buf.read(4))[0]
            return ("Lg", list(struct.unpack(f">{n}q", buf.read(8 * n))))
        raise ValueError(t)

    t = buf.read(1)[0]
    buf.read(struct.unpack(">H", buf.read(2))[0])
    return payload(t)


TAG_IDS = {"b": 1, "s": 2, "i": 3, "l": 4, "f": 5, "d": 6, "B": 7, "S": 8, "L": 9, "C": 10, "I": 11, "Lg": 12}


def write_nbt(tag):
    out = io.BytesIO()

    def payload(tag):
        k = tag[0]
        if k == "b": out.write(struct.pack(">b", tag[1]))
        elif k == "s": out.write(struct.pack(">h", tag[1]))
        elif k == "i": out.write(struct.pack(">i", tag[1]))
        elif k == "l": out.write(struct.pack(">q", tag[1]))
        elif k == "f": out.write(struct.pack(">f", tag[1]))
        elif k == "d": out.write(struct.pack(">d", tag[1]))
        elif k == "B": out.write(struct.pack(">i", len(tag[1]))); out.write(tag[1])
        elif k == "S":
            b = tag[1].encode("utf-8"); out.write(struct.pack(">H", len(b))); out.write(b)
        elif k == "L":
            out.write(bytes([tag[1]])); out.write(struct.pack(">i", len(tag[2])))
            for e in tag[2]: payload(e)
        elif k == "C":
            for name, v in tag[1].items():
                out.write(bytes([TAG_IDS[v[0]]])); b = name.encode("utf-8")
                out.write(struct.pack(">H", len(b))); out.write(b); payload(v)
            out.write(b"\x00")
        elif k == "I": out.write(struct.pack(">i", len(tag[1]))); out.write(struct.pack(f">{len(tag[1])}i", *tag[1]))
        elif k == "Lg": out.write(struct.pack(">i", len(tag[1]))); out.write(struct.pack(f">{len(tag[1])}q", *tag[1]))

    out.write(bytes([TAG_IDS[tag[0]]])); out.write(struct.pack(">H", 0)); payload(tag)
    return out.getvalue()


def register_course(world, holes):
    path = os.path.join(world, "data", "minegolf", "courses.dat")
    if os.path.exists(path):
        root = read_nbt(io.BytesIO(gzip.open(path).read()))
    else:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        root = ("C", {"data": ("L", 10, []), "DataVersion": ("i", 5023)})
    courses = root[1]["data"]
    if courses[1] == 0:
        courses = ("L", 10, [])
    own = list(struct.unpack(">iiii", COURSE_ID.bytes))
    entries = [c for c in courses[2] if c[1]["id"][1] != own]
    owner = entries[0][1]["owner"] if entries else ("I", [0, 0, 0, 0])

    def pos(p):
        return ("I", list(p))

    entries.append(("C", {
        "id": ("I", own),
        "name": ("S", COURSE_NAME),
        "owner": owner,
        "holes": ("L", 10, [("C", {
            "dimension": ("S", "minecraft:overworld"),
            "tee": pos(h["tee"]),
            "cup": pos(h["cup"]),
            "par": ("i", h["par"]),
        }) for h in holes]),
    }))
    root[1]["data"] = ("L", 10, entries)
    with gzip.open(path, "wb") as f:
        f.write(write_nbt(root))
    return len(entries)


if __name__ == "__main__":
    world = sys.argv[1]
    holes = [build_hole(*h) for h in HOLES]
    landscape()
    clubhouse()
    n = write_pack(world, holes)
    count = register_course(world, holes)
    print(f"{n} build commands, {count} courses in courses.dat")
    for h in holes:
        print(h)
