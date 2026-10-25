# MineGolf

Golf for Minecraft 26.3 (Fabric). Build courses from tee boxes, cups and turf, then play them alone
or with friends using clubs, balls and a golf bag.

Design: [docs/superpowers/specs/2026-10-19-minegolf-design.md](docs/superpowers/specs/2026-10-19-minegolf-design.md) ·
Plan: [docs/superpowers/plans/2026-10-19-minegolf.md](docs/superpowers/plans/2026-10-19-minegolf.md) ·
Course tools (0.2.0): [spec](docs/superpowers/specs/2026-10-24-course-tools-design.md),
[plan](docs/superpowers/plans/2026-10-24-course-tools.md) ·
Golf cart (0.3.0): [spec](docs/superpowers/specs/2026-10-25-golf-cart-design.md),
[plan](docs/superpowers/plans/2026-10-25-golf-cart.md)

## Content

| Item / Block | Use |
|---|---|
| Driver, Iron, Wedge, Putter | Hold right-click next to your resting ball, release to hit. Aim with your view direction. Driver only from a tee box. |
| Golf Ball | Right-click a tee box during a round, or any block top for practice. Dye it in the crafting grid. |
| Golf Bag | Right-click to store 4 clubs + 16 balls. Press `R` to swap the club in your hand with the next one in the bag. |
| Course Book | Create a course, add holes, set par, start a round. |
| Tee Box, Cup, Flag | Hole markers. The flag has no collision. |
| Fairway, Rough, Green, Bunker Sand | Turf with different roll and bounce. Water and lava cost a penalty stroke. |
| Range Finder | Hold it and look at a block: distance, height and a suggested club with power (`physics/ShotAdvisor`, carry only). |
| Golf Cart | Place it on a block, right-click to ride (2 seats, first one drives). W/S gas and brake/reverse, A/D steer (`physics/CartPhysics`). Right-click with a golf bag to put it on the back, sneak + right-click with an empty hand to take it; while riding, `R` takes clubs from that bag. |
| Hole Sign | Place within 4 blocks of a linked tee box: shows hole, par and length, rewritten when the course changes. |
| Out-of-Bounds Stake | Stakes link to their two closest stakes within 16 blocks (never across another stake). A ball that stops on the other side of the line: penalty stroke, back to where it was hit. Hold a stake or the Course Book to see the lines. |

## Playing

1. Place a **Tee Box** and, some distance away, a **Cup** (with a **Flag** on top if you like). Turf is optional
   but makes it real golf: green around the cup, fairway between, rough and bunkers around.
2. Right-click a blank **Course Book**, name the course.
3. Right-click the book again → **Add hole** → right-click the tee box, then the cup. Repeat for more holes
   (up to 18), adjust par with `-` / `+`. Each hole also has ▲ ▼ to reorder, ✕ to delete (click twice) and
   ⟳ to relink: after a tee box or cup was broken and placed again, ⟳ then click the tee and the cup.
4. **Start round**. Players within 32 blocks get a clickable `[Join]` in chat (`/minegolf join <id>`).
5. On each hole: right-click the tee box with a golf ball, then swing. Everyone plays at the same time; the
   next hole starts when all players have holed out. Scores show in the top-left; a scorecard opens at the end.

Achievements: own tab with 20 advancements, granted from `stats/GolfAdvancements` (tree in
`tools/gen_assets.py`). Score achievements use a fair par from the hole length (`round/GolfRules`).
Statistics: `stats/GolfStats`, shown under Statistics → General.

Out of bounds: put the stakes of one row closer to each other than to the row on the other side of the
fairway, every ~8 blocks works well; otherwise a line can close the fairway. The stake geometry is in
`physics/OutOfBounds`, the stake positions per dimension in `course/StakeIndex`.

To stop early, press **Leave round** in the Course Book (or `/minegolf leave`); the others play on.

Commands (no OP needed): `/minegolf join <round>`, `/minegolf leave`, `/minegolf pickup` (give up the hole,
scores double par). Reaching double par ends the hole automatically.

## Development

Needs JDK 25:

```bash
export JAVA_HOME="$(cygpath -w ~/.jdks/jdk-25.0.4.1+1)"
./gradlew build           # jar in build/libs/, runs the JUnit physics/scorecard tests
./gradlew runGametest     # headless server: ball rolls into cup, bunker, water penalty, course saving
./gradlew runClient       # play in a dev client
python tools/gen_assets.py  # regenerate textures, models, recipes, tags, loot tables
```

Manual checklist (`runClient`):

- [ ] Practice: place ball on grass, putt, aim line shows, power bar fills and blinks at full power
- [ ] Driver refuses off the tee; works on the tee box
- [ ] Ball into water → penalty message, ball back at last rest spot
- [ ] Course Book: create, add 2 holes, change par, start round, HUD shows hole/par/strokes/distance
- [ ] Hole out on both holes → scorecard screen
- [ ] Golf bag: store clubs, `R` cycles the club in hand
- [ ] Achievements tab appears after picking up a club; Fore! on the first shot, Splash! in water
- [ ] Statistics → General lists the golf stats
- [ ] Second player (`runClient` again, other username) joins via `[Join]`
