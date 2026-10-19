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
  Released through **v4.5** as of this handoff (config version **52**).

### Config versioning (per stage that adds config)
1. Add keys to all **13** files in `common/src/main/resources/config/*.yml`.
2. Bump `config-version:` in all 13 (currently **52** → next 53...).
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
- Behavioral C-tier criticism from user's reviewer: aim/triggerbot/autoclicker/
  fingerprint were "threshold guesses" — this session answered it with the
  invariant-hardening stages v4.1–v4.5.

## 6. Current state (as of this handoff)

- **Latest release: v4.18** (TrackingLock aim check, CI 39226744367; v4.17 =
  handoff refresh; v4.16 = TriggerBot metronome late band). **NEXT RELEASE
  NUMBER: v4.19 — increment this on EVERY push to main, including docs-only
  pushes (each push creates a release!).** Config version **58**, messages
  version 6, 13 config locales / 11 punishment locales / 11 message
  locales. All CI green.
- **This session shipped 28 releases**: v3.16 AnchorCadence, v3.17 AirPlace,
  v3.18 CrystalCadence tick tier, v3.19 Accuracy, v3.20 CloseSpoof,
  v3.21 JumpCadence, v3.22 PearlSwap, v3.23 UniformClick, v3.24 InvWalk,
  v4.0 stability audit, v4.1 HiddenAim, v4.2 TriggerBot precision tier,
  v4.3 SubTickClick, v4.4 SubTickBreak, v4.5 AutoBlock, v4.6 handoff docs,
  v4.7 JumpReset, v4.8 handoff refresh, v4.9 AutoBlock extension,
  v4.10 changelog/numbering maintenance, v4.11 RotationGrid, v4.12 handoff
  refresh, v4.13 RotationGrid sustained tier, v4.14 TriggerBot metronome
  tier, v4.15 handoff refresh, v4.16 TriggerBot metronome late band,
  v4.17 handoff refresh, v4.18 TrackingLock. (Full details per stage in
  `SESSION_LOG.md`.)
- Repo tip at handoff: `6c230bc` plus the handoff refresh commit that
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

## 7. Next moves (priority order)

1. Next detection stage candidates (research first, then pick one):
   (a) InvWalk rotation tier (researched, cheapest next win): a rotation
   update while a server-opened container GUI is open is impossible for the
   vanilla mouse-captured GUI, and only world interactions are covered
   today. Guards: ping-compensated open age (max(350 ms, 2x ping + 100 ms)
   as InvWalk), per-update rotation delta floor, event floor within a
   counter window, teleport/vehicle/spectator/non-self-camera skips and a
   bedrock exemption via GeyserUtil.isBedrockPlayer(uuid);
   (b) pure-arbitrary rotation detector for the RotationGrid hole - streams
   that never show quantized deltas (fully synthetic aim). Any design must
   keep cinematic-smoothing legit input silent; live idea: decisive-delta
   (0.5-12 deg) consistency during combat windows only, no cluster gate;
   (c) sensitivity-aware grid spoofing (modules that add sub-quantum noise
   to genuine deltas) - arms-race tier, research first;
   (d) dig-side corollaries (SubTickBreak analogues: repetition/velocity of
   dig starts), stats stages, or new bypass intel from research. Backlog:
   FailSwing-style pre-swing bands (FP-risky without a cadence filter),
   triggerbot uniform wide-range late delays (documented evasion class),
   TrackingLock straight-line exemption (needs reversal evidence) and its
   soft-smoothing miss band (gain < ~0.7).
2. Keep monitoring Modrinth behavior per release (low priority).
3. **AethelGuard** (native ModSeeker-style client verification subsystem) —
   DEFERRED until the user gives go/no-go. Do not start unprompted.
4. Maintain this handoff folder every stage.

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
