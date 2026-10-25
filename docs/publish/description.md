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
  reorder or delete holes, relink a hole after rebuilding its tee or cup, and start a round.
- **Rounds with friends** – players within 32 blocks get a clickable `[Join]` in chat. Everyone plays their
  own ball at the same time; the next hole starts once all have holed out. The HUD shows hole, par, strokes
  and distance to the cup, and a scorecard with birdies, bogeys and totals opens at the end.
- **Golf Cart** – drive the course in a two-seat cart: W/S for gas and brake, A/D to steer. Put your golf bag on
  the back and swap clubs with `R` without leaving your seat.
- **Range Finder** – look at any block: distance, height difference and a suggested club with power, worked
  out with the same physics as the ball. Aim at the flag to measure to the cup.
- **Hole Signs** – place one next to a tee box and it writes hole number, par and length by itself, and keeps
  them up to date when you change the course.
- **Out of Bounds** – white stakes form a boundary line. A ball that stops on the other side costs a penalty
  stroke and is played again from where it was hit. Hold a stake or the Course Book to see the lines.
- **Practice anywhere** – place a ball on any block and play without keeping score.
- **Dyeable balls** – combine a golf ball with any dye to tell everyone's ball apart.
- **20 achievements and 10 statistics** – including the brutal *Sand Wizard* and *Perfect Round*.
- **Recipe book** – every recipe unlocks as soon as you hold its main ingredient.

## Achievements and statistics

MineGolf has its own advancement tab with 20 achievements, from **Fore!** for your first shot to
**Perfect Round** for playing every hole of an 18-hole round under par. A few to aim for:

| Achievement | How |
|---|---|
| Sand Wizard ★ | Hole out straight from a bunker, at least 20 blocks away |
| Perfect Round ★ | Every hole of an 18-hole round under par |
| Hole in One ★ | Sink your tee shot on a hole at least 20 blocks long |
| Fish Food | Feed the fish 100 golf balls |
| Snowman | Take 8 strokes on a single hole |
| Overthinker | Hold your swing too long 10 times |

Course builders choose par freely, so score achievements use a fair par based on the hole's length
(up to 15 blocks par 2, up to 60 par 3, up to 140 par 4, longer par 5). A par 6 on a short hole will not
hand out eagles.

Statistics (Statistics → General, also usable as scoreboard objectives like
`minecraft.custom:minegolf.strokes`): strokes, holes played, rounds finished, holes in one, birdies, eagles,
penalty strokes, balls in water, wasted swings and longest drive.

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
| Range Finder | Iron, Glass Pane, Iron above Iron, Redstone, Iron |
| Golf Cart | 3 White Wool above Iron, Minecart, Iron |
| Hole Sign | Birch Sign + Paper |
| Out-of-Bounds Stake ×4 | White Dye above 2 Sticks |

All items are also in the **MineGolf** creative tab.

## Requirements

- Minecraft **26.3**, Fabric Loader **0.19.5+**, [Fabric API](https://modrinth.com/mod/fabric-api)
- Install on **both client and server**.
