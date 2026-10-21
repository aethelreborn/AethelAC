# Changelog

All notable changes to AethelAC. Releases are cut automatically by CI from
`main`; this file summarizes what each stage shipped.

## [v5.13] - 2026-10-21

- Bound the per-viewer block-change history used by `ThroughWalls`. A lag
  machine or dense explosion could push more than the 4096-entry budget inside
  the five-second prune interval. The map now prunes on overflow and, if the
  window is still over budget, drops the history and suppresses occlusion
  verdicts for five seconds. Forgetting recent changes without that pause
  would make a freshly placed wall look long-standing - the exact false
  positive the age gate exists to prevent. Normal building and mining stay far
  below the budget, so the pause is never reached in ordinary play.

## [v5.12] - 2026-10-21

- Narrow `ThroughWalls`' block-action exemption to breaks only. The first cut
  skipped attacks for a second after any block placement or item use, but 1.8
  sword blocking emits a block-placement/use packet every four ticks, so
  `lastBlockPlaceUseItem` stayed permanently fresh and the check would never
  have run for most 1.8 PvP. Placements are not a false-positive source: a
  predicted placement makes the client see more than the server, which can
  only hide an attack. The failed-prediction break window remains exempt.

## [v5.11] - 2026-10-21

- Add `ThroughWalls`, the occlusion invariant from the guide's entity
  interaction chapter. A vanilla client raycasts blocks first and only
  considers an entity that is nearer than the first block hit, so an attack
  whose aimed ray crosses a solid block before the target cannot be produced
  by an unmodified client. The check reconstructs the same ray with the
  engine's own `HitboxData`/`WorldRayTrace` block shapes and flags when the
  block is strictly in front of the lag-compensated target box.
- The check is built so every uncertain input can only excuse an attack, never
  create one: the verdict is deferred to the movement packet that follows the
  attack (both 1.8 and modern clients queue the attack first, so this supplies
  the exact attack-tick rotation), it skips attacks within one second of the
  player's own dig or place, uses the highest possible eye height, and ignores
  blocks whose state changed within the last second plus the player's
  transaction ping. A pending attack older than 250 ms is dropped rather than
  judged with a stale rotation.
- `CompensatedWorld` now retains the last five seconds of block-change times
  per viewer (bounded to 4096 positions and pruned on insertion), exposed as
  `getBlockAgeMillis`. Identical-state resyncs are still not recorded; only
  visible changes are.
- A dedicated `ThroughWalls` punishment group ships in all 11 punishment
  locales: alerts from five violations, console kick at 40 and a 30-day ban at
  80, so a one-off geometry disagreement cannot punish while a sustained
  through-wall aura still does.

## [v5.10] - 2026-10-21

- Make the optional ping ceiling actually cancel attacks. `PingPolicy` never
  flags, so no punishment group can list it, and `shouldModifyPackets()`
  required the group-derived enabled flag - which meant
  `attack-patterns.cancel-attack-over-ping-ms` never cancelled a packet on
  any install, even when an operator set a ceiling. A narrow `isPolicy()`
  hook now lets policy modules bypass the group ledger while keeping every
  other modification gate (anticheat disabled, per-player no-modify,
  exemption). No detection overrides the hook and `flag()` is unchanged, so
  violation recording is untouched. The bundled config comment in all 13
  locales now states why the switch needs no `punishments.yml` entry.
- Note for operators: this does not change any default. The ceiling ships at
  `-1`; the stage fixes the behaviour for servers that opt in.

## [v5.9] - 2026-10-21

- Revert the fall correction to the interaction eye. v5.8 assumed that a
  placement, dig or attack packet is built from a position one tick older
  than the one the server holds. Reading the client tick order shows the
  opposite. 1.8.9 handles the mouse and calls `rightClickMouse()` from the
  previous render frame's `objectMouseOver` (`Minecraft.java` 2154) and only
  then ticks the world, moves the player and sends the movement packet
  (2207). 1.21.11 calls `handleInputEvents()` / `doItemUse()` (1949, 2161)
  before `world.tickEntities()` (1960). The raycast and the server therefore
  use the same position, and the eye was never stale. Shifting it down made
  `Reach` reconstruct an eye below the real one for a falling attacker: with
  no intercept the check cancels the hit and flags `Hitboxes`, a group with a
  30-day ban ladder. Simulated against a same-level target, the shifted
  reconstruction starts missing at about 1.2 blocks/tick of fall and misses
  every legitimate attack at 2.0-3.5 blocks after ~15 ticks (0 of 4), while
  the correct reconstruction hits all four at every fall speed including
  terminal. The check code and the eye helpers are reverted to their
  pre-v5.8 state; the v5.8 entry is kept for history and marked superseded.
- Record the verified geometry in the README, and keep the rotation
  candidates that already cover the one thing that *is* a tick behind: the
  client's yaw and pitch.

## [v5.8] - 2026-10-21 (superseded by v5.9)

- Make the interaction ray checks fall-aware. A placement, dig or attack
  packet is sent before the movement packet of the same tick, so the eye
  every one of those checks reconstructs is one tick old - and while the
  player is falling that single tick is worth a block of eye height at
  terminal speed. `PositionPlace`, `PositionBreakA`, `RotationBreak`,
  `FarBreak`, `FarPlace` and `Reach` now shift the reconstructed eye down by
  the distance the server's own velocity says the player fell, and add the
  residual uncertainty to their tolerance. The distance comes from the
  server's estimate of the client's velocity, so a client cannot buy itself
  a larger uncertainty by claiming a bigger drop. Simulated: with the raw
  reconstruction a legitimate player interacting with the block at their
  feet is flagged 15 times across six fall speeds (0.5-3.9 blocks per tick);
  with the correction the false flags are 0 while every out-of-reach
  interaction still flags (18 of 18). This is the false-positive class the
  field documented for placement-ray checks, where a build competition
  filled with alerts for legal placements.

## [v5.7] - 2026-10-21

- Fix `DamageReaction` blindness on high-ping servers. The tier measured the
  delay from the server's own health update to the client's reaction packet,
  which carries one full round trip, and judged it against a 120 ms mean
  ceiling. On a server whose ping sits at or above that ceiling the tier
  could never fire: simulated bot reaction rate 1.000 at 0-100 ms ping and
  0.000 from 140 ms upwards. The check now subtracts the measured
  transaction ping - the same round trip Grim already times - and judges the
  client-side reaction the reflex floor actually applies to. Simulated
  validation (400 sessions per model and ping): bot reaction 0-25 ms flags
  1.000 at 0, 60, 140, 250 and 400 ms ping, while human lognormal reactions
  (medians 320 / 220 / 180 ms) and the coincidence class (an unrelated click
  inside the window after 5% of damage events) stay at 0.000 - including on a
  server where 10% of transactions carry a 200-800 ms lag spike.
- Extend `DamageReaction` with the AutoArmor dimension: clicks on the armor
  slots of the personal inventory (slots 5-8) now count as damage
  reactions, which is the machine-speed signature of armour automation.
  Container windows are excluded so chest slots are never mistaken for
  personal-inventory slots, and the offhand dimension is narrowed the same
  way. New config keys `damage-reaction-ping-compensation` and
  `damage-reaction-armor-slots` (config 63 -> 64).

## [v5.6] - 2026-10-21

- Add `CheatClient`: a strict cheat-client detector that reads three
  server-observable artefacts out of the login handshake - the client brand
  (`minecraft:brand`), the channels the client registers
  (`minecraft:register`, including the namespace in front of the colon) and
  the mod ids of a Forge/FML handshake the client volunteers. Vape, Prestige,
  Krypton, Myau, LiquidBounce, Meteor, Wurst, Aristois, Sigma, Impact,
  Nucleus, FDP, RusherHack, Phobos and the rest ship in the new
  `cheat-client` lists (config 62 -> 63). The lists are the union of the
  published fingerprint sets of Allium, the smp-network cheat signatures,
  PACC, Windfall and Guardian, trimmed of the names legitimate clients also
  carry (Sodium, Hanabi, OptiFine, Iris, Lunar, Badlion, Prism, Forge,
  Fabric, Geyser). A match kicks the player on the first flag and bans on the
  fourth, so a known cheat client cannot sit on a server. Bedrock
  (Geyser/Floodgate) players are exempt, and a failure in the optional
  Bedrock lookup skips the check rather than guessing.
- Note the limit honestly: a ghost client that spoofs its brand to `vanilla`
  and registers no channel leaves nothing for any brand or channel check.
  Against those users the behavioural tiers - an attack with no arm swing,
  timer drift, impossible packets - are what actually bite.

## [v5.3] - 2026-10-20

- Report at startup how many experimental check tiers are enabled, and warn
  when `experimental-checks: false` leaves them silent. Config upgrades now
  append the shipped `true` default when the key is missing; bundled configs
  and the deployment guide explain the consequences. No config-version bump.
- Make `PingSpoof` skip players when the optional Geyser/Floodgate status
  lookup fails, and log the first failure instead of assuming a Java client.

## [v4.26] - 2026-10-19

- Add `PingSpoof` (experimental): flags keepalive latency spoofing, the
  Delicate-style "KeepAlive delays" module shape. The check times the
  server's own keepalive round trips and pairs each one with the transaction
  round trips Grim already measures inside a centred window around it, so
  congestion, stalls and the two streams' different sampling rates cancel
  out; only a delay applied to the keepalive echoes alone survives as a
  median paired gap. A gap of `min-divergence-ms` (100 ms) across four or
  more pairs, repeated three times in fifteen seconds, flags (10 s
  cooldown). The keepalive stream's own quartile spread gates judgement:
  above `max-spread-ms` (80 ms) the client's frame quantization or a jittery
  link makes added delay indistinguishable (never judged), and below
  `min-keepalive-spread-ms` (2 ms) the reply timing is locked to a
  deterministic client clock (frame-rate caps that divide the keepalive
  interval) and equally ambiguous. Transaction-side-only delay is
  deliberately not judged, and bedrock (Geyser/Floodgate) players are
  exempt.
- Simulated validation (200 sessions x 120 s per model, both a 1 s
  Paper-style keepalive regime and the vanilla 15 s interval, with GC
  stalls, bufferbloat phases - aligned and jittered - mid-session route
  changes, and 1-60 FPS clients including keepalive-only and
  transaction-only frame quantization): all twelve legit models stay at a
  0.000 flag rate in both regimes. Fixed 100-300 ms spoofs flag
  0.995-1.000 with a median first flag of 7.7-9.2 s in the 1 s regime
  (0.795-1.000 at about 57 s with 15 s keepalives), uniform 50-250 ms
  spoofs 0.765-1.000, spoofs combined with bufferbloat or heavy GC
  0.960-1.000. Documented holes: two-sided delay (invisible from a
  plugin), clients under ~10 FPS or with wide jitter (gated out), and
  ultra-steady sub-2 ms links (skipped).
- Config `config-version` 61 -> 62 (`ping-spoof` section: `min-divergence-ms`,
  `max-spread-ms`, `min-keepalive-spread-ms`, `min-keepalive-samples`,
  `min-transaction-samples`, `pair-window-seconds`, `window-seconds`,
  `min-events`, `counter-seconds`), `PingSpoof` joined the `Misc` punishment
  group in all locales.

## [v4.25] - 2026-10-19

- Maintenance: refresh the committed handoff state through v4.24 (NoBreakDelay
  post-break cooldown tier) so the next session resumes from the current
  stage.

## [v4.24] - 2026-10-19

- Add `NoBreakDelay` (experimental): when a break completes the vanilla client
  arms a five tick delay (`blockHitDelay` on 1.8 `PlayerControllerMP`,
  `destroyDelay` on 1.14+ `MultiPlayerGameMode`) that gates the next dig
  start - on any position, for survival and creative instabuild alike - so a
  legit client cannot start the next dig sooner than about six client ticks
  (250-300 ms) after its previous `FINISHED_DIGGING`. Starts landing inside
  `post-break-delay-ms` (150 ms) of the finished break, three times in ten
  seconds, flag. Events are dropped while the client's own tick packets
  (movement packets, or the arm swing sent once per tick while mining) show
  it ticked through the window, which keeps a stalled connection - GC pause,
  Netty backlog or client uplink stall - from reading as a removed cooldown;
  instant-break and single-tick breaks, creative and spectator players are
  exempt, and isolated events are never punished.
- Simulated validation (400 ticks/session model): holding miners, per-block
  re-clickers and instant-break fields on 1.8 and modern protocols at 60, 20
  and 5 FPS stay at a 0.000 flag rate on clean links and with a 150-200 ms
  stall every 2-5 seconds; no-delay and one-tick-reduced modules reach 1.000,
  two-tick reductions 0.99, and the wall-clock-only variant of the same rules
  flags 0.35-0.88 of those same legit miners, which is what the tick-signal
  filter buys. Residual class (documented): repeated 300-400 ms stalls about
  once a second, 0.047-0.160.
- Config `config-version` 60 -> 61 (`attack-patterns` post-break knobs:
  `post-break-delay-ms`, `post-break-tick-signals`, `post-break-min-events`,
  `post-break-counter-seconds`), `NoBreakDelay` joined the `Misc` punishment
  group in all locales.

## [v4.23] - 2026-10-19

- Maintenance: refresh the committed handoff state through v4.22 (FastClick
  zero-reaction looting tier) so the next session resumes from the current
  stage.

## [v4.22] - 2026-10-19

- Extend `FastClick` with a zero-reaction looting tier (instaloot and
  loot-aura modules): the first click of every opened window is compared
  against the minimum of recent round-trip samples, because a click that
  references a new container id can only have been produced by a client
  that already received the open packet - the server-side gap is at least
  one round trip. Three or more first clicks inside ten seconds landing
  less than 70 ms above that floor flag; windows whose best round trip
  exceeds 300 ms are never judged and bedrock (Geyser/Floodgate) players
  are exempt. Simulated validation: human looters and self-driven mashers
  stay at a 0.000 flag rate while tick-scheduled and packet-hooked looting
  modules reach 1.0.
- Widen the `FastClick` menu-cadence band down to 110 ms
  (`menu-min-interval-ms` default 250 -> 110; operators who pinned the old
  value keep it): the 0.045 interval CV ceiling, not the pace, is the
  machine criterion, so the fast stealer band (110-250 ms) is now covered.
  Simulated validation: human looting models at paces of 120-240 ms reach
  interval CVs of 0.098-0.274 (0.000 flag rate) while machine rhythms and
  fixed-delay modules stay at CVs of 0.008-0.031 (0.997-1.000 flag rate).

## [v4.21] - 2026-10-19

- Maintenance: refresh the committed handoff state through v4.20 (InvWalk
  rotation tier) so the next session resumes from the current stage.

## [v4.20] - 2026-10-19

- Extend `InvWalk` with a rotation tier: rotation updates with a summed
  yaw+pitch delta of at least 0.5 degrees landing while a container is open
  - after the open age exceeds twice the transaction ping plus 100 ms, with
  two counted updates inside ten seconds - now flag alongside world
  interactions, catching GUI-rotation modules that aim with the container
  still up. A vanilla container GUI captures mouse input completely, so no
  camera rotation can originate from a normal client while it is open.
  Rotations arriving before the compensated open age are never judged
  (they can predate the window), and bedrock (Geyser/Floodgate) players are
  exempt from the rotation tier because their GUI semantics differ.

## [v4.19] - 2026-10-19

- Maintenance: refresh the committed handoff state through v4.18
  (TrackingLock aim check) so the next session resumes from the current
  stage.

## [v4.18] - 2026-10-19

- Add `TrackingLock` (`aim-analysis`): catches smoothing / lock aim-assist
  by judging only swept ticks - target-direction updates of at least 1.5
  degrees per tick - and measuring how often the crosshair holds within 1.5
  degrees of the hitbox center. A flag needs 20+ swept ticks with 3+ strafe
  reversals and a 0.9 locked fraction inside a 60-tick window. Simulated
  validation: ten human tracking models (lag/gain/noise, tremor and
  predictive variants, 300 trials per target-motion class) all stay at a
  0.0 stream flag rate with best-window lock fractions at or below 0.16,
  while exponential smoothing trackers (gain 0.7-1.0, with and without aim
  jitter) reach 0.52-0.88 per stream; straight-line motion is exempt by
  design (no reversals) and very soft smoothing (gain 0.5) is the
  documented miss class.

## [v4.17] - 2026-10-19

- Maintenance: refresh the committed handoff state through v4.16
  (TriggerBot metronome late band) so the next session resumes from the
  current stage.

## [v4.16] - 2026-10-19

- Extend `TriggerBot`'s metronome tier with a late band: modules whose mean
  reaction sits above 280 ms (up to 460 ms) are flagged at a tighter
  reaction coefficient-of-variation ceiling of 0.04, because a
  machine-steady beat survives the tick quantization that widens a human's
  spread. Simulated validation: fixed 300/350/400 ms and 50 ms-wide
  uniform delays across 300-450 ms all reach a 1.0 stream flag rate, while
  plausible humans (slow 300-400 ms means, held-attack cadences, high fire
  rates) stay at or below 0.045; the only leak is the already-documented
  implausible profile (25 ms reaction spread, no slow tail). Uniform delay
  ranges 100 ms and wider keep the reaction CV above both ceilings and are
  the tier's known evasion class.

## [v4.15] - 2026-10-19

- Maintenance: refresh the committed handoff state through v4.14
  (TriggerBot metronome tier) so the next session resumes from the current
  stage.

## [v4.14] - 2026-10-19

- Extend `TriggerBot` with a metronome tier for delayed triggerbot modules:
  over the last 20 crosshair-entry opportunities that lasted at least
  300 ms, a module that answers at least 85% of them with a mean reaction
  in 100-280 ms and a reaction coefficient of variation at or below 0.10 is
  flagged. Simulated validation: delayed bots (fixed/uniform/normal sleeps
  120-250 ms, including a module skipping one opportunity in ten) all reach
  a 0.96-1.0 stream flag rate at the shipped thresholds, while plausible
  humans (50-75% fire rates, ex-Gaussian reaction times) stay at or below
  0.07; only physiologically implausible consistency (reaction spread under
  25-30 ms with no slow tail) reaches bot territory, documented as an
  accepted experimental-tier risk.

## [v4.13] - 2026-10-19

- Harden `RotationGrid` with a sustained tier: the fitted mouse quantum now
  survives two minutes of fully synthetic rotation (so a sustained fight
  cannot close the gate mid-combat), and a horizon tier flags when 5% of
  the last 300 armed probes are off the grid, catching slow-burn aim-assist
  corrections that never cluster into a 30-probe burst. Simulated
  validation: legit streams (including mouse use beside fully synthetic
  combat phases) produced zero violations in any 300-probe window, while
  corrections at 3-8% now activate the horizon tier within the first
  combat phase instead of staying invisible.

## [v4.12] - 2026-10-19

- Maintenance: refresh the committed handoff state through v4.11
  (RotationGrid) so the next session resumes from the current stage.

## [v4.11] - 2026-10-19

- Add `RotationGrid` (`aim-analysis`): catches mixed aim-assist / silent-aim
  rotation streams by fitting the sensitivity quantum from the delta
  histogram and counting combat deltas that are neither multiples of the
  fitted grid nor explained by another populated cluster. Only streams that
  demonstrably contain quantized mouse deltas can flag; fully smoothed,
  cinematic and protocol-quantized 1.8 input never opens the grid gate.

## [v4.10] - 2026-10-17

- Maintenance: correct release numbering in this changelog (handoff state
  refresh was v4.8, extended AutoBlock was v4.9) and refresh the committed
  handoff folder.

## [v4.9] - 2026-10-17

- Extend `AutoBlock` to legacy protocols: on 1.8 a held sword counts as a
  blocking item (sword-block modules), and shield release packets now count
  as toggles on modern protocols (drop-shield-to-attack pattern).

## [v4.8] - 2026-10-17

- Maintenance: refresh committed handoff state through v4.7.

## [v4.7] - 2026-10-17

- Add `JumpReset`: jumps pinned to within two ticks of every knockback
  (ping-adaptive window), catching automatic sprint-reset modules while
  human resets stay in their organic timing band.

## [v4.6] - 2026-10-17

- Add the committed session handoff folder (`HANDOFF/`): full development
  log, workflow mandate, verified API notes and next moves, so development
  can resume seamlessly from any machine. The next detection stage starts
  at v4.7.

## [v4.5] - 2026-10-17

- Add `AutoBlock`: shield use toggled within 50 ms of attacks, catching
  shield-drain and auto-block modules whose shield activation rides the
  same tick as their attacks.

## [v4.4] - 2026-10-17

- Add `SubTickBreak`: dig starts delivered faster than the client input loop
  allows, catching nuker floods and duplicated dig exploits at the packet
  level - including same-position restarts MultiBreak cannot see.

## [v4.3] - 2026-10-17

- Add `SubTickClick`: attacks delivered faster than the client input loop
  allows (repeated sub-25 ms deliveries), catching packet-flooding kill-aura
  modules and duplicated attack exploits by physical impossibility instead
  of distribution shape.

## [v4.2] - 2026-10-17

- TriggerBot precision tier: delay-configured triggerbots (100-120 ms
  "human-like" delays) are separated from statistically fast humans by
  crosshair-entry precision - the attack must land within 0.10 blocks of the
  hitbox center, four times within 60 seconds.

## [v4.1] - 2026-10-17

- Add `HiddenAim`: through-wall precision tracking of a moving, occluded
  player. The first hardening stage for the behavioral aim family - it keys
  on information a vanilla client cannot legally have (hidden target
  positions) instead of a rotation-threshold guess.

## [v4.0] - 2026-10-17

Stability milestone. No new detections: full registry, config and punishment
audit, plus documented reliability tiers for every check family.

- Audit: 158 checks, all stable keys unique; all punishment entries resolve
  (family names match by substring); 13/13 config locales in perfect key
  parity; config version 47 consistent across locales.
- Document the invariant vs behavioral reliability tiers in the README,
  including the hardening roadmap for the behavioral families (aim,
  triggerbot, autoclicker, fingerprint).

## [v3.24] - 2026-10-17

- Add `InvWalk`: attacks, placements and digs while a container window is
  open, catching invwalk and loot aura modules that the vanilla input model
  makes impossible.

## [v3.23] - 2026-10-17

- Add `UniformClick`: 200 click intervals at 5+ CPS with excess kurtosis
  below 0.5, catching uniformly randomized autoclicker delays that bounded
  randomizers cannot leave.

## [v3.22] - 2026-10-17

- Add `PearlSwap`: ender pearls thrown within 15 ms of a slot change, or the
  slot reverting instantly, catching pearl catch and switchback throw macros.

## [v3.21] - 2026-10-17

- Add `JumpCadence`: twelve or more one-to-two-tick jump pulses with a
  machine-constant interval cadence, catching W-tap and D-tap macros.

## [v3.20] - 2026-10-17

- Add `CloseSpoof`: close-window packets fired within 50 ms before attacks
  three or more times a minute, catching the SimulateInventoryClosing aura
  module that resets item-usage state before every hit.

## [v3.19] - 2026-10-17

- Add `Accuracy`: crosshair pins within 0.025 blocks of the hitbox center
  across most attacks, catching center-locking aim modules that survive
  rotation-pattern checks.

## [v3.18] - 2026-10-17

- Harden `CrystalCadence` with a tick-quantized tier: "safe" two-tick
  automations that dodge the millisecond CV gate still land on the same tick
  deltas every cycle and are now caught by the tick ring.

## [v3.17] - 2026-10-17

- Add `AirPlace`: clicks on positions that have been stable air with no block
  history and no recent own placement, catching airplace and placement
  optimizer modules without false-flagging ghost-block desync.

## [v3.16] - 2026-10-17

- Add `AnchorCadence`: respawn anchors clicked twice within 150 ms of each
  other (charge + detonate) eight or more times with a tight cadence,
  catching anchor macro mods and air-anchor double clicks.

## [v3.15] - 2026-10-17

- Make all ms-based timing checks ping-aware: attacks from players whose
  transaction ping exceeds `timing-max-ping-ms` (150 ms) are ignored by
  SilentAim, SwapAttack, TriggerBot and CrystalCadence to absorb out-of-order
  packet batching.
- Add optional `PingPolicy` module (`cancel-attack-over-ping-ms`, disabled by
  default) that cancels attack packets over a ping ceiling so hits do not
  register.

## [v3.14] - 2026-10-17

- Add `CrystalCadence`: end crystals broken within 150 ms of their own spawn
  eight or more times with a tight cadence, catching instant placer/destroyer
  crystal auras.

## [v3.13] - 2026-10-17

- Add `TriggerBot`: attacks within 100 ms of a target entering the crosshair
  of a near-static rotation, catching zero-delay triggerbot modules.

## [v3.12] - 2026-10-17

- Add `SwapAttack`: held slot changed within 15 ms of an attack, or instantly
  reverted, catching mace swap, lunge/attribute swap and breach swap macros
  plus the same-tick silent-hotbar sync of modern aura clients.

## [v3.11] - 2026-10-17

- Add `SilentAim`: rotation snap toward the target around an attack followed
  by a revert to the exact pre-attack rotation within 90 ms - the on-tick
  silent-aim pattern of modern clients (Vape-style silent aim, LiquidBounce
  on-tick timing).

## [v3.10] - 2026-10-17

- Audit pass: 145 checks with unique stable keys, config and message
  versions aligned with their updater specs, punishment bindings verified.
- Document chat-prompt automation and legacy fishing sound coverage limits.

## [v3.9] - 2026-10-17

- `/aethel profile` gains quick reel and firework boost counters
  (`%fish_quick%`, `%firework_boosts%`).

## [v3.8] - 2026-10-17

- Add `AutoFish`: three or more reels landing within 120 ms of the bobber
  splash sound (near the player), catching same-tick auto-fishing modules.

## [v3.7] - 2026-10-17

- Add `FireworkCadence`: eight or more consecutive elytra firework boosts
  with machine-constant intervals (CV at or below 0.05 in the 700-4000 ms
  band), covering Super Fire Work style pulse modules.

## [v3.6] - 2026-10-17

- `/aethel profile` gains the mace smash fall-window fraction and fixed-gap
  resource pack report metrics (`%mace_band%`, `%pack_fixed%`).

## [v3.5] - 2026-10-17

- Changelog now tracks each release stage.

## [v3.4] - 2026-10-17

- `PackSpoof` now also covers configuration-phase resource pack status
  packets (1.20.3+ pushes).

## [v3.3] - 2026-10-17

- Extend `FastClick` with a menu-bot tier: twenty consecutive clicks with
  near-constant intervals (CV at or below 0.045) in the 250-900 ms band.

## [v3.2] - 2026-10-17

- Add `PackSpoof`: two or more resource packs reported loaded 293-360 ms
  after acceptance (Server RP spoof modules use a fixed 300 ms timer).

## [v3.1] - 2026-10-17

- Widen `RotationDecay` to decay ratios 0.18-0.995; the redundant half-decay
  gate is removed, covering fast geometric rotators (BackAngle speeds up to
  0.82).

## [v3] - 2026-10-17

- Baseline carrying the complete strict detection suite as released through
  v2.24 (see below). Versioning continue at v3.1 next.

## [v2.24] - 2026-10-17

- Release candidate: full audit of config/messages versions, check
  registrations, and stable keys; README status badges.

## [v2.23] - 2026-10-17

- Add `MaceSmash`: mace attacks pinned to the minimal 1.5-1.7 block smash
  fall window (10+ recent smash attacks, 90%+ in band). Binds to the
  `Combat` ladder.

## [v2.22] - 2026-10-17

- CI hygiene: workflow timeouts, artifact retention, Gradle build cache.

## [v2.21] - 2026-10-17

- Locale key-parity pass: dropped a legacy German `VehicleC` block, added
  the missing Romanian item-usage knob and Japanese reload messages.

## [v2.20] - 2026-10-17

- Document known detection limitations in the README.

## [v2.19] - 2026-10-17

- Restructure the configuration documentation into prediction hardening,
  fingerprints, the automation detection suite, and staging/tooling.

## [v2.18] - 2026-10-17

- Add `NoSwingAttack`: four or more attacks in a row with no arm animation
  within 200 ms (silent-aura no-swing modes). Binds to `AimHeuristics`.

## [v2.17] - 2026-10-17

- Tuning pass: widen the `SlowCadence` metronome CV default to 0.05.

## [v2.16] - 2026-10-17

- Add `DropBurst`: twelve or more item drops inside a second.
- Extend `FastClick` with armor-slot bursts: four or more direct armor-slot
  clicks inside 400 ms.

## [v2.15] - 2026-10-17

- `/aethel profile <player>` now renders automation metrics: attack CPS,
  attack cadence CV, rotating-attack fraction, container click rate, idle
  events, tool-swap cycles, and damage reactions.

## [v2.14] - 2026-10-17

- Extend `DamageReaction` to offhand automation: offhand clicks, swap
  actions, and swap clicks count as machine-speed reactions to damage.

## [v2.13] - 2026-10-17

- Add `AutoToolChain`: hotbar tool swaps cycling around every dig (six or
  more sub-400 ms cycles a minute).

## [v2.12] - 2026-10-17

- Add `DamageReaction`: item uses within 120 ms mean of a health decrease
  (ClickPearl/AutoGApple).

## [v2.11] - 2026-10-17

- Add `FastClick`: sixty or more container clicks in ten seconds at a mean
  interval of 110 ms or less (chest stealers).

## [v2.10] - 2026-10-17

- Add `IdleAutomation`: sparse jump/swing events during long stillness
  (anti-AFK modules).

## [v2.9] - 2026-10-17

- Add `PhantomBreak`: finish or abort dig actions without a matching start
  (anti-cheat-aware velocity payloads). Binds to `BadPackets`.

## [v2.8] - 2026-10-17

- `SlowCadence` two-tier detection: add a 100-interval steady tier (CV
  0.08) for randomized-delay triggerbots.

## [v2.7] - 2026-10-17

- Add `ReturnFlick`: attack-anchored rotation bursts that reverse and land
  back on their starting heading (silent-aim restore passes).
- Widen the `RotationDecay` series band.

## [v2.6] - 2026-10-17

- Add `SlowCadence`: triggerbot metronome cadence (48 intervals, CV 0.035).
- Add `AttackRotation`: attacks landing mid-rotation.

## [v2.5] - 2026-10-17

- Add `RotationDecay`: constant-ratio geometric rotation series.

## [v2.4] - 2026-10-17

- Add `shadow-checks` staging (alerts and logs only).
- Extend `AimLinear` with yaw and pitch series.
- `Autoclicker` counts attack packets directly.

## [v2.3] - 2026-10-17

- Add `LinearRotation`: machine-constant combat rotation series.
- Add autoclicker no-burst analysis.

## [v2.2] - 2026-10-17

- Add `Autoclicker`: sustained CPS windows and interval consistency.

## [v2.1] - 2026-10-17

- Add `Fingerprint`: known cheat-client brands and plugin channels.

## [v1] - 2026-10-17

- Initial AethelAC release: rebranded, strict-hardened prediction core.
