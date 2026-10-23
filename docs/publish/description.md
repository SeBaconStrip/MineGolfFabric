# MineGolf

**Real golf in Minecraft.** Build your own course from tee boxes, cups and turf, then play it alone or with
friends: hold to swing, release to hit, and watch the ball fly, bounce and roll across fairway, green and
bunker.

## Features

- **Four clubs** – Driver (tee shots, ~80 blocks), Iron (~50), Wedge (high and short, ~25) and Putter (rolls
  on the green). Hold right-click next to your ball, release to hit. A power bar fills while you hold, a
  dotted aim line shows where the ball starts to fly. Hold too long and the swing is wasted.
- **Real ball physics** – the server simulates every shot: bounces, roll, slopes, walls. Every surface plays
  differently:
  - **Green** – rolls far and true
  - **Fairway** – good bounce and roll
  - **Rough** – grabs the ball and costs distance on the next shot
  - **Bunker** – the ball plugs; getting out is hard
  - **Ice** – slides forever
  - **Water and lava** – one penalty stroke, ball back to where it last lay
- **Golf Bag** – holds your four clubs and 16 balls. Press `R` to swap the club in your hand for the next one
  in the bag.
- **Course Book** – name a course, click a tee box and a cup for each hole (up to 18), set par from 2 to 6,
  and start a round.
- **Rounds with friends** – players within 32 blocks get a clickable `[Join]` in chat. Everyone plays their
  own ball at the same time; the next hole starts once all have holed out. The HUD shows hole, par, strokes
  and distance to the cup, and a scorecard with birdies, bogeys and totals opens at the end.
- **Practice anywhere** – place a ball on any block and play without keeping score.
- **Dyeable balls** – combine a golf ball with any dye to tell everyone's ball apart.

## How to play

1. Place a **Tee Box** and, some distance away, a **Cup**. A **Flag** on top helps you find it.
   Lay **Green** around the cup, **Fairway** in between, **Rough** and **Bunker Sand** around.
2. Right-click a **Course Book**, give the course a name.
3. Open the book again → **Add hole** → right-click the tee box, then the cup.
4. **Start round**, then right-click the tee box with a golf ball and tee off.

Commands (no operator needed): `/minegolf join <round>`, `/minegolf leave`, `/minegolf pickup` (give up the
hole for double par).

## Recipes

| Item | Recipe |
|---|---|
| Golf Ball ×2 | Bone Meal + Slime Ball + Bone Meal (shapeless) |
| Driver | 2 Sticks above 2 Planks |
| Iron | 2 Sticks above 2 Iron Ingots |
| Wedge | 2 Sticks above 1 Iron Ingot |
| Putter | 2 Sticks above 2 Copper Ingots |
| Golf Bag | 7 Leather + 1 String |
| Course Book | Book + Green Dye |
| Tee Box ×6 | 3 Green Carpet above 3 Planks |
| Flag | Red Wool above 2 Sticks |
| Fairway ×4 | 4 Grass Blocks + Bone Meal |
| Rough ×4 | 4 Grass Blocks + Short Grass |
| Green ×4 | 4 Fairway + Bone Meal |
| Bunker Sand ×4 | 4 Sand + Bone Meal |
| Cup | Green + Iron Ingot |

All items are also in the **MineGolf** creative tab.

## Requirements

- Minecraft **26.3**, Fabric Loader **0.19.5+**, [Fabric API](https://modrinth.com/mod/fabric-api)
- Install on **both client and server**.
