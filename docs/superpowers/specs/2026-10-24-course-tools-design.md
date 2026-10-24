# Course tools (package A, 0.2.0) – design

Three additions that make courses easier to read and play: a Range Finder, a Hole Sign and Out-of-Bounds stakes.
No new achievements or statistics.

## Range Finder (item)

- Held in main or off hand. The client casts a ray from the eye along the view, up to 256 blocks, ignoring fluids.
- HUD line above the hotbar while a block is targeted: `47 blocks · 3 up · Iron ~70%`.
  - Distance: horizontal, from the player's feet to the centre of the targeted block's top face.
  - Height: target top minus player feet, rounded, shown as `N up`, `N down` or `level`.
  - Looking at a Flag or Cup measures to the cup centre and prefixes the line with `⚑`.
- Club suggestion comes from `physics.ShotAdvisor` (pure Java, no `net.minecraft` imports):
  - `Suggestion suggest(double distance, double rise, boolean onTee, boolean greenTarget)`.
  - Putter when the target is green-tagged turf and distance ≤ 15: the power that rolls that far on green.
  - Otherwise it tries Wedge, Iron, then Driver (Driver only with `onTee`). It takes the first club whose full
    power carries at least to the target, and finds the power where the flight lands at that distance and height by
    bisection over `BallPhysics` on a flat world (carry only, no roll).
  - Out of range: the longest allowed club at 100% with a `+` mark (`Driver 100%+`).
  - `onTee` is true when the player's own resting ball within hit range sits on a tee box.
- Client only, no packets. Recipe: iron ingot, glass pane, redstone (shaped).

## Hole Sign (block)

- `HoleSignBlock extends StandingSignBlock` with the birch wood type. It is added to `BlockEntityType.SIGN` with
  Fabric's `addValidBlock`, so the vanilla sign block entity and renderer draw it. It drops itself.
- The block entity is waxed, so players cannot edit the text.
- Text (front and back, 4 lines): `Hole 3` / `Par 4` / `142 blocks` / course name (cut to 15 chars).
  When no hole is linked: `No hole` on line 1, the rest empty.
- Which hole: the closest Tee Box within 4 blocks (horizontal, ±2 vertical) that is the tee of a hole of any
  course in this dimension. Ties go to the first course and hole found.
- When the text is written:
  - On placement (server side, `setPlacedBy`).
  - After every Course Book change (create hole, par, reorder, delete, relink): the server rewrites all hole signs
    within reach of every tee of that course, and of a deleted hole's old tee.
- Class `course.HoleSigns` holds the lookup and the rewrite. Recipe: birch sign + paper (shapeless).

## Out-of-Bounds Stake (block)

- `OutOfBoundsStakeBlock`: thin white post, no collision (like the Flag), instabreak.
- Lines: every stake connects to its two closest stakes that are at most 16 blocks away (horizontal distance; y is
  ignored). An edge exists if either end chose the other.
- Rule: when a ball in a round or in practice comes to rest, the segment from where the shot was hit to where it
  stopped is tested against all lines near it. An odd number of crossings means out of bounds:
  - In a round: 1 penalty stroke and the ball goes back to where it was hit (same path as a water hazard).
  - In practice: message only, the ball goes back too.
- Stake index: `course.StakeIndex`, saved data per dimension holding stake positions. `onPlace` adds a stake.
  Stakes that are no longer there (checked when their chunk is loaded) are dropped when they are read.
- Geometry in `physics.OutOfBounds` (pure Java): `lines(List<double[]> stakes)` and
  `isOut(x0, z0, x1, z1, lines)`.
- Holding a stake item or a Course Book shows the lines within 48 blocks as white dust particles. The server sends
  them to that player every 10 ticks.
- Recipe: 2 sticks + white dye → 4 stakes (shaped).

## Testing

- JUnit: `OutOfBoundsTest`: crossing one line, crossing two, passing a row's end, a corner, the 16-block limit,
  and the two-closest rule. `ShotAdvisorTest`: wedge / iron / driver ranges, uphill needs more power, putter on green,
  out of range.
- Gametests:
  - A hole sign next to a linked tee writes the hole text and is rewritten after a par change.
  - A ball that stops behind a stake line gets a penalty stroke and is reset.
  - A ball flies through a stake.
- The recipe-book test expects 18 recipes.
