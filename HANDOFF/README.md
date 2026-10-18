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
- Competitive servers used for testing: eu.loyisa.cn.
- Behavioral C-tier criticism from user's reviewer: aim/triggerbot/autoclicker/
  fingerprint were "threshold guesses" — this session answered it with the
  invariant-hardening stages v4.1–v4.5.

## 6. Current state (as of this handoff)

- **Latest release: v4.7** (CI 39137006222). Config version **53**,
  messages version 6, 13 config locales / 11 punishment locales / 11 message
  locales. All CI green this session.
- **This session shipped 16 releases**: v3.16 AnchorCadence, v3.17 AirPlace,
  v3.18 CrystalCadence tick tier, v3.19 Accuracy, v3.20 CloseSpoof,
  v3.21 JumpCadence, v3.22 PearlSwap, v3.23 UniformClick, v3.24 InvWalk,
  v4.0 stability audit + README reliability tiers, v4.1 HiddenAim,
  v4.2 TriggerBot precision tier, v4.3 SubTickClick, v4.4 SubTickBreak,
  v4.5 AutoBlock, v4.6 handoff docs, v4.7 JumpReset.
  (Full details per stage in `SESSION_LOG.md`.)
- Repo tip at handoff: `c17cd296f` (plus the handoff commit that follows).
- **v4.6 = handoff documentation**; **v4.7 JumpReset shipped** (done:
  self-velocity wrapper = `WrapperPlayServerEntityVelocity` with
  `entityId == player.entityID`, KnockbackHandler.java:48-60 precedent;
  rising-edge pattern = JumpCadence tick sampling with `player.isJumping`).

## 7. Next moves (priority order)

1. **v4.8** — rotation-statistics hardening step (aim family): research
   first which checks consume the AimProcessor divisor/sensitivity signals
   (`checks/impl/aim/processor/AimProcessor.java`: sensitivityYaw/divisorYaw/
   modeYaw fields), then pick the strongest invariant-style extension
   (e.g. rotation-delta autocorrelation, or gcd-divisor violations under
   combat). Backlog also: consumable use-under-hit tier, stats stages.
2. **v4.9+ candidates** (backlog): dig-side corollaries, any new bypass
   intel from research.
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
  checkstyle imports order used in newer checks: `ac.aethel…`, `ac.grim…`,
  `com.github…`, `org…`, `java…`.
- CI failures are normal — read `gh run view <id> --log-failed`, fix, push a
  "Fix ..." commit, re-monitor.
- `AETHEL_DEV_NOTES.md` at repo root is the legacy gitignored scratch; from
  now on the canonical, committed log is `HANDOFF/SESSION_LOG.md`.
