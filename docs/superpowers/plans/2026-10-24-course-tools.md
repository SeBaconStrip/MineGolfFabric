# Course Tools (package A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Range Finder item, Hole Sign block and Out-of-Bounds stakes for MineGolf 0.2.0.

**Architecture:** Two new pure-Java classes in `physics` (`ShotAdvisor`, `OutOfBounds`) carry the maths and are unit
tested. Thin Minecraft glue reads the world: a client HUD line for the Range Finder, `course.HoleSigns` for sign text,
`course.StakeIndex` (saved data) for stake positions, and a hook in `GolfBallEntity` on AT_REST.

**Tech Stack:** Fabric 26.3 (Mojang names), Fabric API 0.164.0, Java 25, JUnit 5, Fabric gametests.

**Spec:** `docs/superpowers/specs/2026-10-24-course-tools-design.md`

## Global Constraints

- Build: `export JAVA_HOME="$(cygpath -w ~/.jdks/jdk-25.0.4.1+1)"`, then `./gradlew build` and `./gradlew runGametest`.
- `net.colin.minegolf.physics` must not import `net.minecraft.*`.
- Assets come from `tools/gen_assets.py` (run with `python tools/gen_assets.py` from the repo root); lang keys are added
  to `en_us.json` by hand.
- Mod version becomes `0.2.0`.

---

### Task 1: ShotAdvisor (pure physics)

**Files:**
- Create: `src/main/java/net/colin/minegolf/physics/ShotAdvisor.java`
- Test: `src/test/java/net/colin/minegolf/physics/ShotAdvisorTest.java`

**Interfaces:**
- Produces: `record Suggestion(Club club, double power, boolean outOfRange)`;
  `static Suggestion suggest(double distance, double rise, boolean onTee, boolean greenTarget)`;
  `static double carry(Club club, double power, double rise)`: horizontal distance where a flight launched from a
  fairway lie first comes down through height `rise`. `Double.NaN` if it never gets there.

- [ ] **Step 1: Write failing tests**
  - `wedgeForShortShot`: `suggest(20, 0, false, false)` → WEDGE, power in (0.5, 1).
  - `ironForMidShot`: 40 → IRON. `driverOnlyFromTee`: 70 with onTee → DRIVER; without → IRON, outOfRange.
  - `uphillNeedsMorePower`: power for (40, +4) > power for (40, 0) with the same club.
  - `putterOnGreen`: `suggest(10, 0, false, true)` → PUTTER, power in (0.3, 1).
  - `suggestedPowerHitsTheTarget`: `carry(club, power, 0)` within 1 block of 30 for the suggestion at 30.
- [ ] **Step 2:** `./gradlew test` → fails (class missing).
- [ ] **Step 3:** Implement. Simulate with `BallPhysics.step` over an empty `SurfaceLookup` (no ground), stop at the
  first tick where `y` falls through `rise` while descending, interpolate. Putter: simulate rolling on a flat green
  lookup until rest. Bisection over power, 30 iterations.
- [ ] **Step 4:** `./gradlew test` → passes.

### Task 2: Range Finder item and HUD line

**Files:**
- Create: `src/main/java/net/colin/minegolf/item/RangeFinderItem.java` (plain item, stack 1)
- Create: `src/client/java/net/colin/minegolf/client/RangeFinderHud.java`
- Modify: `ModItems` (register, creative tab), `GolfHud.extract` (call `RangeFinderHud.extract`)

**Interfaces:**
- Consumes: `ShotAdvisor.suggest`, `GolfBallEntity.findHittable`, `GolfBallEntity.isOnTee`.

- [ ] Ray: `level.clip(new ClipContext(eye, eye + look·256, OUTLINE, NONE, player))`.
- [ ] Flag hit → cup block is the first `ModBlocks.CUP` at or below the flag (up to 3 blocks); target = cup centre top.
- [ ] Line text via lang keys `minegolf.hud.range` ("%s blocks · %s · %s %s%%") and `minegolf.hud.up/down/level`.
- [ ] Verify in game (the client log has no errors, the HUD shows the line).

### Task 3: OutOfBounds geometry (pure)

**Files:**
- Create: `src/main/java/net/colin/minegolf/physics/OutOfBounds.java`
- Test: `src/test/java/net/colin/minegolf/physics/OutOfBoundsTest.java`

**Interfaces:**
- Produces: `record Line(double x0, double z0, double x1, double z1)`; `static final double MAX_GAP = 16`;
  `static List<Line> lines(List<double[]> stakes)` (each `{x, z}`); `static boolean isOut(double x0, double z0,
  double x1, double z1, List<Line> lines)`.

- [ ] **Tests:** a row of 4 stakes along x at z=10, 8 apart → 3 lines; shot from z=0 to z=20 inside the row's x
  range → out; shot that crosses the row twice (two parallel rows) → not out; shot past the row's end → not out;
  stakes 20 apart → no line; stake with three neighbours links only the two closest (plus edges chosen by the
  others); corner (L shape) is closed.
- [ ] Implement: proper segment intersection (strict, endpoints touching counts as a crossing once by using
  half-open rules on the line's parameter), deduplicate edges.
- [ ] `./gradlew test` passes.

### Task 4: Stake block, index, penalty, particles

**Files:**
- Create: `block/OutOfBoundsStakeBlock.java`, `course/StakeIndex.java`
- Modify: `ModBlocks`, `ModItems` (tab), `GolfBallEntity` (AT_REST), `RoundManager.onPenalty` (message key),
  `MineGolf` (server tick for particles)

**Interfaces:**
- `StakeIndex.get(ServerLevel)`, `add(BlockPos)`, `List<BlockPos> near(ServerLevel, double x, double z, double r)`
  (drops stakes in loaded chunks that are gone).
- `RoundManager.onPenalty(GolfBallEntity ball, String messageKey)`; the old one-arg method keeps `minegolf.msg.penalty`.

- [ ] Stake: `FlagBlock`-like thin shape, `noCollision().instabreak()`, `onPlace` adds to the index (server).
- [ ] On AT_REST with a shot: lines from stakes within (shot length + 32) of the midpoint; if `isOut` → reset to
  rest spot (the shot start), play sound, `onPenalty(ball, "minegolf.msg.out_of_bounds")`; practice gets
  `minegolf.msg.practice_out_of_bounds`.
- [ ] Particles: every 10 server ticks, for players holding a stake or Course Book, send white dust along lines
  within 48 blocks (one particle per block, at the stakes' average y + 0.5).
- [ ] Gametests `ballBehindStakeLineIsOutOfBounds`, `ballFliesThroughStake`.

### Task 5: Hole Sign

**Files:**
- Create: `block/HoleSignBlock.java`, `course/HoleSigns.java`
- Modify: `ModBlocks` (register without the generic block item helper, birch sound), `ModItems` (BlockItem, tab),
  `MineGolf` (`BlockEntityType.SIGN.addValidBlock`), `CourseSavedData` (`all()`), `ServerNetworking.courseAction`,
  `CourseBookItem.useOn`

**Interfaces:**
- `HoleSigns.write(ServerLevel, BlockPos sign)`; `HoleSigns.refreshAround(ServerLevel, Collection<BlockPos> tees)`.

- [ ] `HoleSignBlock extends StandingSignBlock(WoodType.BIRCH, …)`; `setPlacedBy` → waxed + `HoleSigns.write`.
- [ ] Text: `minegolf.sign.hole` "Hole %s", `minegolf.sign.par` "Par %s", `minegolf.sign.length` "%s blocks",
  course name literal (15 chars), `minegolf.sign.no_hole` "No hole". Same text on both sides.
- [ ] Course changes: collect the course's tees before and after the change, `refreshAround` both sets.
- [ ] Gametest `holeSignShowsHoleAndFollowsParChange`.

### Task 6: Assets, recipes, docs, version

- [ ] `gen_assets.py`: textures `range_finder` (item), `oob_stake` (block); stake model (white post with red cap),
  blockstate; hole sign blockstate → `minecraft:block/birch_sign`, item model → `minecraft:item/birch_sign`; loot
  tables for stake and hole sign; recipes `range_finder`, `hole_sign`, `oob_stake` with recipe-book unlocks.
- [ ] Lang: item/block names and the keys above.
- [ ] Recipe test expects 18. README, CHANGELOG (0.2.0), `docs/publish/description.md`, `mod_version=0.2.0`.
- [ ] `./gradlew build runGametest` green, commit, push.
