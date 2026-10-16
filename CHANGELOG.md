# Changelog

All notable changes to AethelAC. Releases are cut automatically by CI from
`main`; this file summarizes what each stage shipped.

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
