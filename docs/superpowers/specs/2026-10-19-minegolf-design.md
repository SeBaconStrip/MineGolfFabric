# MineGolf — Design

Fabric mod for Minecraft 26.3. Players build golf courses from tee boxes, cups and turf blocks,
then play them alone or together with clubs, balls and a golf bag.

- Mod id `minegolf`, package `net.colin.minegolf`, repo `C:\Projects\MineGolfFabric`
- Toolchain copied from TobaccoModFabric: Loom `1.18-SNAPSHOT`, MC `26.3`, loader `0.19.5`,
  fabric-api `0.164.0+26.3`, Java 25, Mojang names, `splitEnvironmentSourceSets()`
- Solo and multiplayer are equal citizens: a solo round is a round with one player.

## Decisions

| Topic | Decision |
|---|---|
| Players | Solo and multiplayer equally |
| Shot input | Hold right-click to charge a power bar, release to hit; aim = look yaw |
| Terrain | Own turf blocks (Fairway, Rough, Green, Bunker) with friction and bounce |
| Course setup | Tee Box + Cup blocks, linked into holes with a Course Book |
| Clubs | Driver, Iron, Wedge, Putter |
| Golf bag | Storage (4 clubs, 16 balls) + key to cycle the held club |
| Group play | Simultaneous: everyone plays their own ball, hole advances when all are done |
| v1 extras | Aim line only (no wind, no records, no golf cart) |
| Physics | Server-authoritative `GolfBallEntity`, client interpolates |

## 1. Content

### Items

- **Golf Ball** (`golf_ball`), stack 16. Dyeable: shapeless recipe ball + dye stores the color in
  `DataComponents.DYED_COLOR`; the entity renders tinted. Looking at a ball shows the owner name.
- **Clubs**, stack 1, no durability:

  | Club | Launch angle | Max carry (flat, 100%) | Restriction |
  |---|---|---|---|
  | Driver | 12° | ~80 blocks | only from a Tee Box |
  | Iron | 25° | ~50 blocks | — |
  | Wedge | 50° | ~25 blocks | ignores bunker penalty |
  | Putter | 0° (ground roll) | ~15 blocks roll on green | — |

- **Golf Bag** (`golf_bag`), stack 1. Holds 5 slots in a `DataComponents.CONTAINER`
  (4 clubs + 1 ball stack of up to 16), shown in the vanilla hopper screen. Right-click opens a bag screen. While the bag is anywhere in the
  inventory, key `R` (rebindable, category MineGolf) swaps the club in the main hand with the next
  club in the bag (Driver → Iron → Wedge → Putter → Driver). Empty hand takes the first club.
- **Course Book** (`course_book`), stack 1. Unbound book creates a course; bound book
  (component `minegolf:course_id`, UUID) edits it and starts rounds.

### Blocks

- **Tee Box** (`tee_box`): slab-height mat. A ball is teed by right-clicking it with a golf ball.
- **Cup** (`cup`): full block looking like green turf with a hole. Counts as green for physics.
- **Flag** (`flag`): one block whose model is 2 blocks tall, no collision.
- **Turf**: `fairway`, `rough`, `green`, `bunker_sand` (falls like sand).

  | Surface | Restitution | Rolling friction (×/tick) | Speed kept on landing | Lie modifier |
  |---|---|---|---|---|
  | Green, Cup | 0.20 | 0.985 | 70% | 100% |
  | Fairway, Tee Box | 0.35 | 0.92 | 75% | 100% |
  | Rough + all other vanilla blocks | 0.15 | 0.80 | 50% | 75% |
  | Bunker Sand, vanilla sand/red sand | 0.0 | 0.60 | 10% | 40% (Wedge 100%) |
  | Ice variants | 0.30 | 0.995 | 95% | 100% |

  Club speeds (blocks/tick at 100 %): Driver 3.48, Iron 2.03, Wedge 1.28, Putter 0.23.

  Surface lookup is a block tag per surface (`minegolf:surface/green` etc.), so packs can extend it.
- **Hazards**: ball touching water or lava, or falling below the world minimum → +1 penalty
  stroke, ball returns to its last rest position.

### Recipes (survival)

- Golf Ball: bone meal + slime ball + bone meal (shapeless) → 2 balls; ball + any dye → dyed ball
- Clubs: iron ingots + sticks in club shapes (Driver uses a planks head)
- Golf Bag: leather in a U shape (7) with string in the top middle → 1 bag
- Course Book: book + bone meal (shapeless)
- Tee Box: 3 green carpet over 3 planks → 6
- Turf: Fairway = 4 grass blocks + bone meal → 4,
  Rough = 4 grass + short grass → 4, Green = 4 fairway + bone meal → 4,
  Bunker Sand = 4 sand + bone meal → 4
- Cup: green + iron ingot; Flag: stick + stick + red wool

## 2. Shot and ball physics

### Shot flow

1. Player holds a club and stands within 2.5 blocks of **their own** resting ball.
2. Holding use charges power 0 → 100 % over 30 ticks. Held at 100 % until tick 60, then it
   drops to 0 % (no indefinite full power). A power bar renders above the hotbar.
3. While charging, the client draws the **aim line**: particles along the first 30 ticks of the
   predicted flight from `BallPhysics`, ignoring blocks after the first ground contact.
4. Charging uses vanilla item use, so the release (`releaseUsing`) runs on the server with the
   held ticks and the player's yaw; no shot packet exists. The server validates own ball,
   distance, ball at rest and club allowed for the lie when use starts. Valid → velocity set,
   +1 stroke, swing sound. Invalid → action bar message, nothing happens.
5. Shot velocity: `speed = club.maxSpeed × power × lieModifier`, direction from yaw and the club
   launch angle. Putter shots are pure horizontal.

### Physics (`BallPhysics`, server tick, also used for aim line)

- Pure Java, no Minecraft classes. Inputs: position, velocity, a `SurfaceLookup` interface
  (`Surface at(int x, int y, int z)` + `boolean solid(x,y,z)`). Output: new state + events
  (LANDED, HOLED, HAZARD, AT_REST).
- Air: gravity 0.05 blocks/tick², drag ×0.99 per tick.
- Landing: vertical velocity × −restitution, horizontal × speed kept on landing; if the bounce is
  < 0.08 it stops bouncing and rolls.
- Rolling: horizontal velocity × surface friction per tick. Drops off edges. Hitting a wall
  reflects that axis with factor 0.5. Blocks use their real collision height: steps up to 0.15
  (carpets, tee mats) are rolled over, higher ones are walls (no slope simulation in v1).
- Sub-steps: moves are split so no step exceeds 0.4 blocks (no tunneling through blocks).
- **At rest**: speed < 0.005 for 10 consecutive ticks → AT_REST, rest position stored.
- **Holed**: ball center inside a Cup block's top face within 0.3 blocks of its center at
  horizontal speed < 0.35 → HOLED. Faster → lip-out, keeps rolling.
- **Stuck guard**: no rest within 600 ticks → reset to last rest position, no stroke.

### Practice mode

Outside a round, right-clicking any block top with a ball places a practice ball. Strokes are not
counted, the cup still triggers a "Holed!" message. Sneak + use on your own ball picks it up.
Other players cannot hit your ball.

## 3. Course and round system

### Courses

- `CourseSavedData` (stored in the overworld data storage, server-wide): map of `Course`
  `{ UUID id, String name, UUID owner, List<Hole> holes }`.
  `Hole { ResourceKey<Level> dimension, BlockPos tee, BlockPos cup, int par }`, max 18 holes,
  par 2–6 (default 3).
- **Unbound Course Book** use → screen with a name field → `CreateCoursePayload` → book becomes
  bound.
- **Bound Course Book** use in air → course screen: name, hole list (#, par, distance, status),
  par −/+ buttons, delete-last-hole button, **Add hole**, **Start round**.
- **Add hole** = link mode on the server for that player: next use on a Tee Box sets the tee
  (action bar "Now click the cup"), next use on a Cup appends the hole. Sneak-use or 60 s timeout
  cancels. A block already in another hole of this course is rejected.
- Only the owner (or an OP) may edit. Anyone holding the bound book may start a round.
- A hole is **invalid** when its tee or cup block is no longer there (checked on round start and
  when the course screen opens). Rounds skip invalid holes with a chat warning.

### Rounds

- `RoundManager` (server, in memory) keeps active rounds; one round per player.
  Rounds end on server stop (not persisted).
- **Start**: host presses Start round → round created → every player within 32 blocks gets a chat
  invite with a clickable `[Join]` (`/minegolf join <roundId>`). Joining is allowed until the
  first shot on hole 1.
- **Per hole**: each player tees up by using a golf ball on that hole's Tee Box (ball comes from
  the inventory or bag; returned when the hole ends for them). Using the wrong tee shows where the
  right one is.
- Strokes per player per hole; penalties add strokes. Reaching `2 × par` strokes without holing
  ends the hole for that player with score `2 × par`. `/minegolf pickup` does the same on purpose.
- Hole ends when every player has holed or picked up → chat line with the hole scores; the HUD
  points to the next tee. After the last hole → scorecard screen (players × holes, total, ± par)
  and a chat summary.
- `/minegolf leave` or disconnect removes the player and their ball. Empty round → round ends.

### HUD (client, only in a round)

Top-left panel: course name, `Hole n/N`, `Par p`, own strokes, distance to cup (or to next tee),
then one line per other player with their strokes on this hole. Power bar above the hotbar while
charging (also in practice mode).

### Networking (Fabric payloads, server validates everything)

- C2S: `CycleClubPayload()`,
  `CreateCoursePayload(String name)`, `CourseActionPayload(UUID course, Action action, int value)`
  where Action ∈ {PAR_UP, PAR_DOWN, DELETE_LAST, ADD_HOLE, START_ROUND} (`value` = hole index).
- S2C: `OpenCourseScreenPayload(CourseView)`, `RoundStatePayload(RoundView)` (on every change),
  `RoundEndPayload(ScorecardView)`, `RoundClearPayload()`.

## 4. Structure, errors, testing

### Packages

```
net.colin.minegolf
  MineGolf                      entry point, registration order
  item/      ModItems, ClubItem, Club (enum: profile data), GolfBallItem, GolfBagItem, CourseBookItem, ModComponents
  block/     ModBlocks, TeeBoxBlock, CupBlock, FlagBlock, BunkerSandBlock
  physics/   BallPhysics, BallState, Surface (enum), SurfaceLookup        (no Minecraft imports)
  entity/    ModEntities, GolfBallEntity, LevelSurfaceLookup
  course/    Course, Hole, CourseSavedData, LinkMode
  round/     Round, RoundManager, Scorecard                              (Scorecard pure Java)
  network/   payload records + ServerNetworking
  command/   MineGolfCommand
client (split source set)
  MineGolfClient, GolfBallRenderer, PowerBar/HudOverlay, AimLine, KeyBindings,
  CourseScreen, CreateCourseScreen, ScorecardScreen, GolfBagScreen
```

### Error handling

- Every C2S payload re-checks: player alive, item in hand, ownership, distance, round membership.
  Violations are dropped silently except player-facing rule errors (action bar text).
- Ball entity is removed when its owner leaves; balls of finished holes are removed and the item
  returned (dropped at the player if the inventory is full).
- Chunk unload: max carry (~80 blocks) stays inside the owner's view distance, so no chunk tickets;
  an unloaded ball is saved with the chunk like any entity.
- Broken tee/cup during a round: the hole is skipped for everyone with a chat message.

### Testing

- **JUnit 5** (`src/test`): `BallPhysics` (carry per club within ±15 % of the table on a flat
  fairway, bunker plugs, ball rests, cup captures slow balls and lips out fast ones, no tunneling)
  and `Scorecard` (totals, ± par, pickup cap).
- **Fabric gametest** (`runGametest`): registry/data load, ball shot on green rolls into cup,
  water hazard adds a penalty, course link via `CourseSavedData`.
- **Manual** (`runClient`): checklist in README — build a 2-hole course, play solo, play with a
  second client (`runClient` twice with different usernames).

### Out of scope for v1

Wind, course records/leaderboards, golf cart, slopes, spin/hook/slice, persistent rounds across
restarts, course sharing/copying, placing the bag as a block.
