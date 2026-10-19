# AethelAC — Session Handoff (READ THIS FIRST)

This folder exists so that a **fresh assistant session (new OS, no chat memory)**
can continue the AethelAC project seamlessly. Everything important lives here,
committed to the repo.

- `SESSION_LOG.md` — the chronological audit log of everything done so far
  (stage by stage, with CI run IDs, design rationale, gotchas). Append to it.
- `scripts/stage_script_template.py` — the reusable pattern for the per-stage
  config/punishment editor script.
- `../AGENTS.md` — points the next session at this folder automatically.

---

## 1. What this project is

**AethelAC** — a rebranded, strict-hardened fork of Grim (open-source Minecraft
anticheat), developed at `github.com/aethelreborn/AethelAC` (public, GPL-3.0).
It is released automatically by CI on every push to `main`. Publishes to
Modrinth (loaders: bukkit/folia/fabric, MC 1.8–26.3; Modrinth assets already
done). Repo layout: `common/` (all check code), `bukkit/`, `fabric/`, configs
under `common/src/main/resources/{config,punishments,messages}`.

## 2. The standing mandate (user directive — obey it)

- Work **continuously in a loop**. NEVER stop after a single stage to report or
  ask for permission. The user checks in occasionally and gets annoyed if the
  assistant stops ("why are you stopping every time").
- Ship **unlimited versions** — keep going for days/weeks; not just to v5.
- **Miss no cheat that can bypass us.** Every stage hardens something real.
- Only stop for a genuine decision the user must make (like the deferred
  AethelGuard go/no-go), or when truly blocked.
- When the user is present: be terse, CLI-style, direct, no fluff.

### The 9-step loop per stage

1. **Research** — new cheat modules/bypasses (web, client source, notes) →
   find what we don't detect.
2. **Compare** — check existing repo checks so we don't duplicate (grep first:
   `checks/impl/**`, stableKeys, punishment names).
3. **Plan** — pick check name, family/cluster, config keys, thresholds, FP
   guards.
4. **Todo list** — keep a visible todo list (todowrite) with the stage
   in_progress; mark done immediately when done.
5. **Agents** — for research/monitoring/verification when useful.
6. **Push** — 3 commits per stage, then one `git push origin main`.
7. **Monitor CI** — the workflow builds, runs checkstyle + tests, releases.
8. **Audit** — append a full entry to `HANDOFF/SESSION_LOG.md` (research →
   findings → files → CI run ID → NEXT). Update `HANDOFF/README.md` state.
9. **Loop** — immediately start the next stage; do not stop.

## 3. Hard rules & conventions

### Machine / build
- **No local builds** (old machine: 4GB RAM, no JDK). CI is the compiler;
  a failed CI is fixed by a follow-up commit ("Fix X") — never amend.
- Verify code by reading APIs in-repo before writing (grep the exact wrapper/
  method names — the packetevents version here is 2.14.1+b1f9403-SNAPSHOT).
- Keep shell commands light.

### Git
- Repo-local identity: `Aethel <aethelreborn@gmail.com>`.
- 3 commits per stage: (1) check code, (2) configs+punishments+spec,
  (3) docs (README+CHANGELOG). Push once, then monitor CI to completion.
- CI monitor: `gh run list -R aethelreborn/AethelAC -L 1 --json status,conclusion,databaseId`
  in a sleep-30 loop until `"status":"completed"` (runs ~3-8 min; bash timeout
  1260000).
- **Every push to main creates a release.** Keep CHANGELOG matching what ships.
- Release tags: vX → vX.1; vX.n (n<24) → vX.(n+1); vX.24 → v(X+1).
  Released through **v4.24** as of this handoff (config version **61**).

### Config versioning (per stage that adds config)
1. Add keys to all **13** files in `common/src/main/resources/config/*.yml`.
2. Bump `config-version:` in all 13 (currently **61** → next 62...).
3. Add AethelConfigSpecs docstring line `vN → vN+1: ...` and bump
   `ConfigUpdater.Spec.builder("/config/", N+1, ConfigFlavor.V2)`.
4. Do it with a python script following `scripts/stage_script_template.py`
   (insert after an anchor key, `yaml.safe_load` validate + assertion,
   idempotent skip guard). Run from repo root.
5. Punishments: `common/src/main/resources/punishments/*.yml` (11 locales),
   add check name to the right group (Misc/Combat/AimHeuristics/Autoclicker).
   Note: family names match by substring (PunishmentManager.java:86-88).

### New check conventions
- `@CheckData(name=..., stableKey="aethel.<family>.<snake>", description=...,
  experimental = true)` — aggressive checks always `experimental=true`.
- Config read ONLY in `onReload` override (the Check ctor calls reload before
  field initializers — no inline init).
- Flag cooldowns: 5s typical (UniformClick 10s). Ping gates via
  `player.getTransactionPing() > maxPingMs` for ms-timing checks.
- Registry: `common/.../manager/CheckManager.java`. Clusters combat/breaking/
  multiactions/packetorder/scaffolding use wildcard imports; aim/misc use
  explicit imports; append puts in the right block (breaking/aim/misc etc.);
  SetbackBlocker must stay last in its region.
- FP stance: skip on teleports/vehicle/spectator/non-self-camera where
  sensible; prefer repeated-event thresholds over single occurrences.
- README: add a bullet in the matching feature section.
- CHANGELOG: new `## [vX.Y] - <date>` entry at top.

### Documentation duty
- After finishing a stage: append to `HANDOFF/SESSION_LOG.md` (this replaces
  the old gitignored `AETHEL_DEV_NOTES.md` scratch — that file is legacy).
- Keep `HANDOFF/README.md` section 6 (state) + section 7 (next moves) current.

## 4. Key APIs (all verified working in this repo)

- Attack packets: `PacketType.Play.Client.INTERACT_ENTITY`
  (`WrapperPlayClientInteractEntity.getAction() == InteractAction.ATTACK`) and
  legacy `Play.Client.ATTACK`.
- Digging: `Play.Client.PLAYER_DIGGING` →
  `WrapperPlayClientPlayerDigging.getAction()`, enum
  `com.github.retrooper.packetevents.protocol.player.DiggingAction`
  (`START_DIGGING`, `FINISHED_DIGGING`, `CANCELLED_DIGGING`, ...).
- Item use: `Play.Client.USE_ITEM`, or `PLAYER_BLOCK_PLACEMENT` with
  `getFace() == BlockFace.OTHER`.
- Held item: `player.inventory.getHeldItem().getType() == ItemTypes.SHIELD`
  (`com.github.retrooper.packetevents.protocol.item.type.ItemTypes`);
  name checks like `held.getType().getName().toString().endsWith(":mace")`.
- Entity map: `player.compensatedEntities.entityMap`
  (`Int2ObjectOpenHashMap<PacketEntity>`; iterate
  `Map.Entry<Integer, PacketEntity> e : entityMap.int2ObjectEntrySet()` and
  `e.getKey().intValue()` — do NOT call `getIntKey()`).
- Entity position: `entity.trackedServerPosition.getPos()` (Vector3d);
  hitbox: `entity.getPossibleCollisionBoxes()` → `SimpleCollisionBox`
  (public minX..maxZ); players only: `EntityTypes.PLAYER.equals(entity.getType())`.
- World ray: `WorldRayTrace.traverseBlocks(player, start, end, predicate)`
  (predicate (WrappedBlockState, Vector3i) → HitData or null; HitData record:
  position/blockHitLocation/closestDirection/state). Block transparency/glass
  exempt via `state.getType().getName().toString().contains("glass")`.
  Collision boxes for a cell: `Collisions.getCollisionBoxes(player,
  SimpleCollisionBox, List, false)`.
- Eye/look: eye = `player.x, player.y + player.getEyeHeight(), player.z`;
  look dir = `-sin(yaw)*cos(pitch), -sin(pitch), cos(yaw)*cos(pitch)`
  (unit vector).
- Tick: `AethelAPI.INSTANCE.getTickManager().currentTick`;
  `isTickPacket(type)` from `AethelProcessor`.
- Jump state: `player.isJumping` / `player.lastJumping`
  (set in MovementCheckRunner:333-334 from `packetStateData.knownInput.jump()`).
- Rotation events: `CheckManager.onRotationUpdate` dispatches to all checks
  implementing `RotationListener` (`void process(RotationUpdate)`;
  `newYaw/newPitch/deltaYawABS/...`).
- Container: `player.serverOpenedInventoryThisTick`; send OPEN_WINDOW /
  CLOSE_WINDOW (`PacketType.Play.Server.*`); receive `Play.Client.CLOSE_WINDOW`.
- BlockPlace (scaffolding): `BlockPlace.position` = clicked block,
  `getFace()`, `getPlacedBlockPos()`, `resync()`; `place.set(...)` →
  `compensatedWorld.updateBlock`.
- Block state: `player.compensatedWorld.getBlock(Vector3i)` →
  `WrappedBlockState` (`getType()`, `.getType().isAir()`, `.isSolid()`).
- Flame/velocity (for future JumpReset work): search `ENTITY_VELOCITY` — no
  existing check uses it yet; the wrapper is
  `WrapperPlayServerEntityVelocity` (PacketType.Play.Server.ENTITY_VELOCITY);
  self filtering: id == `player.entityID` (verify first!).

## 5. Research intel gathered (keep accumulating)

- **TristanClient/Argon** (client source): "Placement Optimizer: removes 2
  tick placement delay - bypasses vulcan and grim"; "airplacement with anchor
  macro and airplace optimizer"; System56: "placement optimizer MUST EXCLUDE
  ANCHORS"; "2 ticks for both break and place = safe config".
- **LiquidBounce**: ON_TICK aura sends PosRot→ATTACK→revert;
  SimulateInventoryClosing (fake CLOSE_WINDOW before attacks); Prestige fork
  has Pearl Catch / Shield Drain / humanized aim.
- **Meteor**: AutoWeapon zero switch delay.
- **Releon** (reference client source at old machine `/home/darkie/ass/Releon 1.21.11/`):
  triggerbot default delay 0ms.
- catpvp runs old Grim + manual moderation; BetterHitreg is appearance-only;
  150 ms temporal-buffer validation pattern.
- **Mouse quantization intel (v4.11)**: vanilla mouse deltas are whole
  multiples of q = 1.2*(0.6*sens+0.2)^3 (float32): 0.0096 deg at sens 0,
  0.6144 at sens 1. The 1.8 protocol quantizes yaw to 1.40625 deg, so
  quantum-fitting checks are structurally blind on 1.8 (by design, not a
  regression). Upstream Grim's baritone divisor detection is acknowledged
  broken (issue #1432). Upstream FP class for raw quantum tests: Optifine
  cinematic camera (sub-quantum micro deltas). `AethelMath.gcd` returns the
  last remainder above MINIMUM_DIVISOR (~0.0086), so per-pair gcd tests
  cannot cleanly recover the quantum - histogram cluster fitting is the
  working approach.
- Competitive servers used for testing: eu.loyisa.cn.
- **LiquidBounce KillAura docs (2026-06-08 revision, v4.16 research)**: the
  module exposes AutoBlocking unblock modes (StopUsingItem / ChangeSlot /
  SwapHand - the slot-swap variants are covered by SwapAttack, the
  stop-use variant by AutoBlock), SimulateInventoryClosing (fake
  CLOSE_WINDOW before attacks - covered by CloseSpoof v3.20), a Fake block
  mode that is client-side animation only (no server signature, out of
  scope), requoted FailSwing (fake swings aimed at targets 2.5-3.0 blocks
  beyond attack range; uncovered, but FP-risky because human pre-swinging
  looks identical without a machine-cadence filter), and KeepSprint (a
  1.8-era sprint-reset bypass, low detection value on modern clients).
  Nothing else in the documented setting surface is a new server-side
  signature - the aura family's remaining holes are the rotation-side
  ones, not the interaction-side ones.
- **v4.22 research negatives (canonical from this stage on)**: sim8/sim9 -
  a straight-line TrackingLock precision tier (0.4/0.3 deg lock) flags
  perfect-prediction humans at 1.00 (worst fraction 0.95 at omega 1.8-2.5
  deg/tick), so the documented straight-line exemption is sim-justified;
  sim10 - uniform wide-range triggerbot delays cannot be caught by delta
  shape, because tick quantization makes fast humans platykurtic too (the
  "p.9 norm 160/25" human flags 0.84-1.00); sim11 - the deep-sleeper band
  above 460 ms needs a reaction CV ratio at or below ~0.6 to be catchable,
  after which slow-narrow humans leak 0.8-1.0. Vanilla 1.20.1
  `MouseHandler.turnPlayer()` (smoothCamera -> SmoothDouble.
  getNewDeltaValue -> player.turn) means cinematic camera input reaches the
  server as dense, non-quantized deltas - any future delta-shape detector
  must keep cinematic streams silent.
- **Window-reaction invariant (v4.22)**: a CLICK_WINDOW referencing a fresh
  container id cannot exist before the client received OPEN_WINDOW, so the
  server-side gap is at least one round trip; the running minimum of
  transaction and keepalive pings over 30 s is a lower bound of that round
  trip (a keepalive-only ping spoof cannot inflate it - the floor takes the
  min of both sources), and human visual reaction adds >= ~100 ms on top.
  Apparent reaction below 70 ms three times in 10 s is machine territory;
  blind-mashing a server-pushed menu on a sub-70 ms rhythm is the accepted
  FP class, while opens the player caused themselves are phase-locked to
  their own press rhythm and safe.
- **Post-break delay invariant (v4.24)**: the vanilla client arms a five
  tick delay after every completed break (blockHitDelay in 1.8
  PlayerControllerMP, destroyDelay in 1.20.1 MultiPlayerGameMode) that
  gates the next START_DIGGING on any position - creative instabuild and
  instant-break paths included - so the legit FINISH->START floor is ~6
  client ticks (250-300 ms). 1.8 swings once per tick while merely holding
  attack (sendClickBlockToController -> onPlayerDamageBlock + swingItem,
  including the blockHitDelay early-return), 1.9+ swings per tick too
  (sparse flying packets) - that is why swing density is a usable tick
  clock in NoBreakDelay and why wall-clock gaps alone are unusable (a
  stall collapses gap and packet stream together; wall-clock-only detection
  simulated 0.35-0.88 flag on legit holders).
- **Keepalive/transaction pairing invariant (v4.26)**: both echo streams
  ride the same TCP connection, so any delay applied to both cancels; only a
  delay applied to the keepalive echoes alone (the stream servers show in
  tab lists and trust) survives as a paired divergence. Two simulation
  findings reshaped the design: (a) window-wide medians are invalid because
  the transaction ring only covers its last ~10 s while the keepalive ring
  spans the whole window - congestion with the same period as the keepalive
  interval (15 s) reads as a 0.230 FP rate (sim14b), fixed by centred
  per-keepalive pairing (sim15: 0.000); (b) a client frame period that
  divides the keepalive interval (5/10/20/30/40 FPS caps divide 1 s and
  15 s) locks or quantizes keepalive reply timing, which is why judgement
  requires a keepalive quartile spread inside [2, 80] ms. Sampling rate
  facts: keepalives are server-sent (1 s Paper-style fleets/proxies; 15 s
  vanilla) while transaction samples come from every valid PONG /
  WINDOW_CONFIRMATION response (Grim's own pings), so both sample-count
  gates must survive either regime; judgement needs 4 pairs and lands ~8 s
  (1 s regime) or ~57 s (15 s regime) into a session.
- **Delicate Client v1.0.0 intel (2026-10-19; 222 modules, MC 26.2/26.3)**:
  a current meta client built around a "Grim self-check" plus a module
  AntiCheat panel that self-limits packet rates - inventory clicks 5 per
  310 ms (~62 ms pace), interacts 9 per 310 ms, actions 59 per 310 ms,
  attacks unlimited - and a ChestStealer that "takes everything out of
  containers you open". Budget configs land in the FastClick rate tier
  (5 per 310 ms) or, trimmed to 2-3 per 310 ms (103-155 ms), in the
  widened cadence band. Other modules with server-side surface worth
  mapping: HideMining (dig progress withheld from others), PacketMine
  (withheld finishes released in bursts), MultiTask (mine + use in
  parallel), TickShift, PingSpoof (KeepAlive delays), NoRotate
  (BadPacketsB already covers the rotation-correction path at
  CheckManagerListener:433), SpeedMine, VeinMiner/Nuker, Scaffold.
- Behavioral C-tier criticism from user's reviewer: aim/triggerbot/autoclicker/
  fingerprint were "threshold guesses" — this session answered it with the
  invariant-hardening stages v4.1–v4.5.

## 6. Current state (as of this handoff)

- **Latest release: v4.26** (PingSpoof keepalive-vs-transaction divergence
  tier, CI 39245309082; v4.25 = handoff refresh; v4.24 = NoBreakDelay
  post-break cooldown tier). **NEXT RELEASE NUMBER: v4.27 -
  increment this on EVERY push to main, including docs-only pushes (each
  push creates a release!).** Config version **62**, messages version 6,
  13 config locales / 11 punishment locales / 11 message locales. All CI
  green.
- **This session shipped 35 releases**: v3.16 AnchorCadence, v3.17 AirPlace,
  v3.18 CrystalCadence tick tier, v3.19 Accuracy, v3.20 CloseSpoof,
  v3.21 JumpCadence, v3.22 PearlSwap, v3.23 UniformClick, v3.24 InvWalk,
  v4.0 stability audit, v4.1 HiddenAim, v4.2 TriggerBot precision tier,
  v4.3 SubTickClick, v4.4 SubTickBreak, v4.5 AutoBlock, v4.6 handoff docs,
  v4.7 JumpReset, v4.8 handoff refresh, v4.9 AutoBlock extension,
  v4.10 changelog/numbering maintenance, v4.11 RotationGrid, v4.12 handoff
  refresh, v4.13 RotationGrid sustained tier, v4.14 TriggerBot metronome
  tier, v4.15 handoff refresh, v4.16 TriggerBot metronome late band,
  v4.17 handoff refresh, v4.18 TrackingLock, v4.19 handoff refresh,
  v4.20 InvWalk rotation tier, v4.21 handoff refresh, v4.22 FastClick
  zero-reaction looting tier + fast menu-cadence band, v4.23 handoff
  refresh, v4.24 NoBreakDelay post-break cooldown tier, v4.25 handoff
  refresh, v4.26 PingSpoof keepalive-vs-transaction divergence tier.
  (Full details per stage in `SESSION_LOG.md`.)
- Repo tip at handoff: `5a13737` plus the handoff refresh commit that
  carries this file.
- **v4.11 RotationGrid shipped** (per-axis 256-sample delta window + histogram
  cluster fit + armed combat probes + 30-probe burst ring), extended in
  **v4.13** with quantum memory (120 s) and a 300-probe horizon tier (5%
  violation fraction). State is in-memory per player; config keys grid-*
  under aim-analysis; AimHeuristics ladder. Fully-synthetic rotation streams
  (never quantized) stay silent by construction — see section 7 item 1.
- **v4.14 TriggerBot metronome tier shipped** (same check, Misc ladder, no
  punishment change): opportunities = crosshair entries lasting >= 300 ms;
  ring of the last 20 opportunities flags when the fire ratio is >= 0.85,
  mean reaction sits in [100, 280] ms and the reaction CV is <= 0.10.
  **v4.16 added the late band**: mean up to 460 ms at CV <= 0.04, catching
  narrowly jittered higher-delay modules (fixed/50 ms-wide delays across
  300-450 ms all flag at 1.0 in simulation). Config keys trigger-metronome-*
  under attack-patterns. Accepted risks documented in the main README:
  ~90% fire-rate humans with reaction spread <= 25 ms and no slow tail reach
  bot territory by design, and uniform delay ranges 100 ms and wider keep
  the reaction CV above both ceilings (the tier's evasion class).
- **v4.18 TrackingLock shipped** (new aim check, AimHeuristics ladder):
  swept-tick tracking-lock detector - a target-direction angular rate of at
  least 1.5 deg/tick arms a sample, a crosshair within 1.5 deg of the
  hitbox center locks it; flag needs 20+ samples, 3+ direction reversals
  (floor 0.1 deg/tick) and a 0.9 locked fraction in a 60-tick window (5 s
  cooldown). Config keys tracking-lock-* under aim-analysis (config v58).
  Sim evidence: all human models (10, including tremor/predictive, 300
  trials per motion class) 0.000 with best-window lock fractions <= 0.16;
  smoothing bots (gains 0.7-1.0, aim jitter, flip and ramped reversals)
  0.52-0.88 per stream. Documented holes: straight-line motion (no
  reversals) is exempt; gain < ~0.7 soft smoothing misses; targets mounted
  on vehicles whose riders send no positions are not judged.
- **v4.20 InvWalk rotation tier shipped** (same check, Misc ladder, no
  punishment change): rotation updates with a summed yaw+pitch delta of at
  least 0.5 deg landing while a container is open count, two within 10 s
  flag, once the open age exceeds max(350 ms, 2x ping + 100 ms). Guards:
  spectator, vehicle, non-self camera, teleport and 1.17-duplicate skips;
  bedrock (Geyser/Floodgate, via GeyserUtil.isBedrockPlayer) exempt.
  Rationale: a vanilla container GUI captures mouse input completely, so no
  camera rotation can originate from a normal client while it is up; TCP
  ordering means only the open side needs latency compensation (rotations
  sent after a close arrive after the close packet). Config keys
  invwalk-rotation-* under inventory-automation (config v59).
- **v4.22 FastClick zero-reaction looting tier + fast menu-cadence band
  shipped** (same check, Misc ladder, no punishment change): the first click
  of every opened window is compared against a round-trip floor (running
  minimum of transaction/keepalive pings over 30 s) - a click that
  references a fresh container id can only exist after the client received
  the open, so the server-side gap is at least one round trip. Three hits of
  apparent < 70 ms inside 10 s flag; floors above 300 ms are never judged,
  re-sent opens with the previous container id are not armed, bedrock
  (Geyser/Floodgate) players are exempt. The menu-cadence band floor moved
  250 -> 110 ms (CV ceiling 0.045 unchanged, still the machine criterion).
  Sim evidence: humans 0.000 (self-driven mashers included), instaloot bots
  1.000; exogenous server-pushed menus mashed blind are the documented FP
  class (0.578 with >=3 opens in 10 s). Config keys click-reaction-* under
  inventory-automation (config v60).
- **v4.24 NoBreakDelay shipped** (new breaking check, Misc ladder, config
  v61): the vanilla client's five tick post-break delay gates the next
  START_DIGGING on any position, so a new start on a different position
  arriving under post-break-delay-ms (150) after a FINISH - while the
  client's own tick signals (movement/tick + swing packets) inside the
  window stay under post-break-tick-signals (4) - is impossible for a
  vanilla client. Three events inside post-break-counter-seconds (10) flag
  (5 s cooldown). Creative/spectator and instant breaks
  (BlockBreakSpeed.getBlockDamage >= 1.0 on START) exempt. Sim evidence:
  legit hold/re-click/instant models 0.000 at 60/20/5 FPS including stall
  bursts, no-delay/1-tick bots 1.000, 2-tick 0.99; without the tick-signal
  filter the same legit holders flag 0.35-0.88 (the filter is
  load-bearing). Experimental (opt-in): the documented FP class is the
  stationary 1.9+ re-clicker whose finish and re-click get coalesced by
  300-400 ms stalls about once a second (0.047-0.160 sim stream rate).
- **v4.26 PingSpoof shipped** (new misc check, Misc ladder, config v62,
  experimental): flags keepalive latency spoofing - the Delicate PingSpoof
  "KeepAlive delays" module shape, the one remaining uncovered Delicate
  surface with a real server-side signature. The check times its own
  keepalive round trips (id-matched send/receive) and pairs every one with
  the transaction samples inside a centred window (pair-window-seconds 3 ->
  half 1.5 s; a queue of up to 4 pending pairs), judging only the median
  paired divergence (>= min-divergence-ms 100 across >= 4 pairs inside
  window-seconds 60; 3 events inside counter-seconds 15; 10 s cooldown).
  Pairing exists because simulation killed the naive median-vs-median
  comparison in two ways: (a) coverage mismatch - the keepalive ring spans
  the whole window while the transaction ring only its last ~10 s, so
  congestion that covers the sparse keepalive samples but not recent
  transactions reads as divergence (0.230 FP against a 15 s-periodic
  bufferbloat model in the vanilla 15 s interval regime); (b) frame
  quantization - a low frame rate can delay keepalive replies by up to a
  frame when the frame period divides the keepalive interval (0.135-0.985
  across the asymmetric models before the spread gates). Final gates: both
  streams' alive counts, keepalive spread inside [min-keepalive-spread-ms 2,
  max-spread-ms 80] (above the ceiling the client's own timing is
  indistinguishable from added delay; below the floor it is frame-locked),
  transaction-side-only delay deliberately unjudged (it lowers every paired
  divergence), bedrock exempt. Sim evidence (sim15: 200 sessions x 120 s per
  model, both keepalive regimes, GC stalls, aligned and jittered
  bufferbloat, route changes, 1-60 FPS symmetric and asymmetric handling):
  all 12 legit models 0.000 in both regimes; fixed +100/+150/+200/+300 ms
  spoofs 0.995-1.000 at 1 s intervals (median first flag 7.7-9.2 s) and
  0.795-1.000 at 15 s intervals (~57 s); uniform 50-250 ms spoofs
  0.765-1.000; spoof + bufferbloat 1.000; spoof + heavy GC 1.000.
  Documented holes: two-sided delay invisible (needs TCP-level RTT), under
  ~10 FPS or wide-jitter clients gated out, sub-2 ms steady links skipped.

## 7. Next moves (priority order)

1. Next detection stage candidates (research first, then pick one):
   (a) pure-arbitrary rotation detector for the RotationGrid hole - streams
   that never show quantized deltas (fully synthetic aim). Any rotation-math
   tier must key on a human impossibility, not on quantization, because
   cinematic camera input is source-verified to reach the server as dense
   non-quantized deltas (MouseHandler.turnPlayer -> SmoothDouble ->
   player.turn). Live idea: decisive-delta (0.5-12 deg) consistency during
   combat windows only, no cluster gate - unproven, sim first;
   (b) sensitivity-aware grid spoofing (modules that add sub-quantum noise
   to genuine deltas) - arms-race tier, research first;
   (c) Delicate-style meta-client surfaces not yet mapped as checks:
   HideMining (dig progress withheld from other players), PacketMine
   (withheld finishes released in bursts), AutoTrade/AutoCraft window
   automation, remaining dig-side corollaries (progress/finish surfaces),
   stats stages. Shipped from this list: the post-break cooldown corollary
   as v4.24 NoBreakDelay and the keepalive-side ping pair as v4.26
   PingSpoof (keepalive-vs-transaction paired divergence, the Delicate
   "KeepAlive delays" module shape);
   (d) FailSwing-style pre-swing bands stay research-blocked: a human
   holding attack produces the same packets without a machine-cadence
   filter.
2. Closed by simulation - do NOT re-open without new evidence (details in
   SESSION_LOG v4.22/v4.24 entries + section 5): straight-line TickLock
   precision tier (sim8/sim9: flags perfect-prediction humans at 1.00),
   TriggerBot uniform wide-range shape tier (sim10: tick quantization makes
   fast humans platykurtic too), TriggerBot deep-sleeper band above 460 ms
   (sim11: catching fixed 480-620 ms sleepers needs CV <= ~0.6, which leaks
   slow-narrow humans at 0.8-1.0), wall-clock-only post-break detection
   (sim13: without the tick-signal filter the same legit holders flag
   0.35-0.88), window-wide keepalive-vs-transaction median comparison
   (sim14/14b: coverage mismatch - keepalive ring spans the window, the
   transaction ring only its last ~10 s - flags 0.230 of the legit
   wifi/bufferbloat model in the vanilla 15 s keepalive regime; replaced by
   centred per-keepalive pairing, sim15 0.000 in both regimes). TrackingLock's
   soft-smoothing miss band (gain < ~0.7) stays
   a documented hole; the orbit-sim precursor for a straight-line tier is
   dead with the precision tier.
3. Keep monitoring Modrinth behavior per release (low priority).
4. **AethelGuard** (native ModSeeker-style client verification subsystem) —
   DEFERRED until the user gives go/no-go. Do not start unprompted.
5. Maintain this handoff folder every stage.

## 8. Assistant memory (carry this forward)

- You are continuing AethelAC autonomously. The user values raw throughput:
  stage after stage, loop without stopping, terse answers.
- Known pitfalls personally encountered: partial string replacements in python
  edit scripts (assert the forbidden token count is 0 before committing);
  `getIntKey()` doesn't exist on `Map.Entry` (use `getKey().intValue()`);
  README edits must match the actual existing sentence (grep first);
  `onReload()` runs from the super constructor BEFORE ctor-body field
  allocations - never dereference instance arrays there, size them with a
  static constant and clamp config values against it;
  checkstyle imports order used in newer checks: `ac.aethel…`, `ac.grim…`,
  `com.github…`, `org…`, `java…`.
- CI failures are normal — read `gh run view <id> --log-failed`, fix, push a
  "Fix ..." commit, re-monitor.
- Fresh-machine setup (2026-10-19, Windows): repo-local git identity is NOT
  set (`git config user.name Aethel; git config user.email
  aethelreborn@gmail.com`) and Python lacks PyYAML (`python -m pip install
  pyyaml`) — both must be done before the first commit / stage script run.
  PowerShell has no `grep`; use the grep tool or `Select-String`.
- `AETHEL_DEV_NOTES.md` at repo root is the legacy gitignored scratch; from
  now on the canonical, committed log is `HANDOFF/SESSION_LOG.md`.
