# Changelog

All notable changes to AethelAC. Releases are cut automatically by CI from
`main`; this file summarizes what each stage shipped.

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
