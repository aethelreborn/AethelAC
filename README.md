# AethelAC

[![Release](https://github.com/aethelreborn/AethelAC/actions/workflows/release.yml/badge.svg)](https://github.com/aethelreborn/AethelAC/actions/workflows/release.yml)
[![CI](https://github.com/aethelreborn/AethelAC/actions/workflows/ci.yml/badge.svg)](https://github.com/aethelreborn/AethelAC/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/aethelreborn/AethelAC)](https://github.com/aethelreborn/AethelAC/releases)

Async, prediction-based anticheat for Minecraft Java Edition 1.8–26.3, built on [PacketEvents](https://github.com/retrooper/packetevents).

AethelAC simulates vanilla movement and physics server-side for every player, then compares the simulation with what the client actually sent. Bypasses require moving outside of the game's own physics, which makes the checks mathematically grounded instead of heuristic.

## Features

- Movement simulation for walking, sprinting, swimming, flying, riding, and knockback.
- Full per-player world replication, including latency-compensated block and inventory state.
- Packet-level validation of order, timing, and contents.
- Reach, aim-assist, triggerbot, autoclicker, macro, and packet-order checks.
- Per-check violation decay, setbacks, alerting, and punishment groups.
- Fully asynchronous; checks run on Netty threads with no main-thread work.
- Bukkit/Spigot/Paper/Folia and Fabric (1.16.1–26.3) support.
- Geyser and Floodgate aware out of the box.

## Requirements

- Java 17 or newer.
- A Spigot, Paper, Folia, or Fabric server.
- If a proxy is used, ViaVersion must run on the backend server, not the proxy.

## Installation

1. Download the latest release from the [releases page](https://github.com/aethelreborn/AethelAC/releases).
2. Put `aethelac-bukkit-<version>.jar` in the server's `plugins` directory, or `aethelac-fabric-<version>.jar` in `mods` for Fabric.
3. Restart the server. Configuration is generated under `plugins/AethelAC`.

Check for updates manually with `/aethel version`.

## Commands

| Command | Description |
| --- | --- |
| `/aethel alerts` | Toggle violation alerts |
| `/aethel verbose` | Toggle verbose flag output |
| `/aethel brands` | Toggle client brand broadcasts |
| `/aethel profile <player>` | View client information and automation metrics |
| `/aethel spectate <player>` | Spectate a player |
| `/aethel history <player>` | Session violation history |
| `/aethel reload` | Reload configuration |
| `/aethel version` | Show build information |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `aethel.alerts` | op | Receive violation alerts |
| `aethel.verbose` | op | Receive verbose output |
| `aethel.brands` | op | Receive client brand broadcasts |
| `aethel.spectate` | op | Use spectator mode |
| `aethel.profile` | op | View player profiles |
| `aethel.history` | op | View violation history |
| `aethel.exempt.<check>` | false | Exempt a player from a check |
| `aethel.nosetback.<check>` | false | Disable set-backs for a player |
| `aethel.nomodifypacket.<check>` | false | Disable packet correction for a player |

## Configuration

The default configuration ships hardened for competitive PvP. Everything lives
in `config/*.yml`, `punishments/*.yml`, `messages/*.yml`, and `databases/*.yml`
and can be tuned per server.

### Prediction and packet hardening

- Movement and knockback simulation setbacks fire immediately (0.02 block/tick
  threshold, 0.5 block advantage ceiling), with slow advantage decay.
- Reach flags from 3.0001 on 1.8 clients; impossible hits are cancelled.
- Timer checks allow 60 ms of drift; negative and vehicle timers are tightened.
- Packet spam, transaction time, and ping abuse limits are cut to a third of
  defaults; verbose flags are mirrored to the console.
- Punishments ship tiered ladders per check group: alerts from the first flag,
  console kick at 30-80 violations, and a 30-day ban at 40-140 violations
  depending on the group. Violation memory spans ten minutes.

### Client fingerprints

- Known cheat-client fingerprints (client brands and registered plugin
  channels, see `client-fingerprint` in `config.yml`) flag the `Fingerprint`
  check: alerts and logs from the first match, a console kick at 15 repeated
  matches, and a 30-day ban at 30.

### Automation detection suite

Attack patterns (`attack-patterns` in `config.yml`):

- `Autoclicker` flags sustained 18+ CPS over a full minute, 20+ CPS over
  twenty seconds, machine-regular click intervals, or high-rate clicking that
  never produces human burst pairs (alerts from 10 violations, console kick at
  80, 30-day ban at 140).
- `SlowCadence` flags triggerbot-style metronome cadence at human-plausible
  rates in two tiers: 48 intervals with a coefficient of variation at or below
  0.05, or 100 intervals at or below 0.08 for randomized-delay bots.
- `NoSwingAttack` flags silent-aura modes that strip the arm animation: four
  or more attacks in a row with no swing within 200 ms.
- `MaceSmash` flags mace smash-timing automation: ten or more recent smash
  attacks with 90% or more pinned to the minimal 1.5-1.7 block fall window,
  where natural leaps and knockback never cluster.
- `SilentAim` flags silent auras: a rotation snap toward the target around an
  attack, followed by a revert to the exact pre-attack rotation inside 90 ms -
  the on-tick rotation pattern of LiquidBounce-style silent aim that a human
  cannot reproduce around a click.
- `SwapAttack` flags silent-hotbar meta swaps: the held slot changing within
  15 ms of an attack (or reverting back within 250 ms), the mechanism behind
  mace swap, lunge/attribute swap and breach swap macros and the zero
  switch-delay default of modern aura clients.
- `TriggerBot` flags attacks fired on the first tick a target enters the
  crosshair of a near-static rotation - sub-100 ms entries a human cannot
  react to, matching the 0 ms default attack delay of triggerbot modules.
- `CrystalCadence` flags instant crystal auras: eight or more end crystals
  broken within 150 ms of their own spawn with a cadence CV at or below 0.6,
  a pace only a placer/destroyer module attacking the spawn packet can hold.
- All timing-sensitive combat checks skip attacks from players whose
  transaction ping exceeds `attack-patterns.timing-max-ping-ms` (150 ms by
  default) to absorb out-of-order packet batching on high latency links.
  `attack-patterns.cancel-attack-over-ping-ms` (disabled by default) optionally
  cancels attack packets above a ping ceiling so the hits never register.

Aim analysis (`aim-analysis` in `config.yml`), all sharing the `AimHeuristics`
ladder:

- `LinearRotation` flags fifteen consecutive combat rotation ticks holding a
  machine-like coefficient of variation in the combined rotation, yaw-only or
  pitch-only series (alerts from 5 violations, console kick at 60, 30-day ban
  at 120).
- `RotationDecay` flags six consecutive rotation deltas forming a
  constant-ratio geometric series (decay ratios 0.18-0.995), the hallmark of
  factor-based smoothing in silent-aim return and ease-out rotators.
- `AttackRotation` flags attacks landing mid-rotation on 80% or more of at
  least twelve attacks in a minute.
- `ReturnFlick` flags attack-anchored rotation bursts that reverse and land
  back on their starting heading within a few ticks, the restore pass of
  silent aim.

Inventory and macro automation:

- `FastClick` (`inventory-automation` in `config.yml`) flags chest-stealer
  style container clicking — sixty or more pickup or shift-move clicks in ten
  seconds at a mean interval of 110 ms or less — armory automation (four
  or more direct armor-slot clicks inside 400 ms), and menu bots (twenty
  consecutive clicks with near-constant intervals, CV at or below 0.045, in
  the 250-900 ms band).
- `AutoToolChain` (same section) flags AutoTool modules that cycle the hotbar
  to a tool and back around every dig (six or more sub-400 ms cycles a
  minute).
- `DropBurst` (same section) flags drop-everything macros: twelve or more
  drops inside a second.
- `DamageReaction` (`reaction-analysis` in `config.yml`) flags
  ClickPearl/AutoGApple/AutoSwap style automation: six or more item uses or
  offhand manipulations (offhand clicks or swap actions) within 120 ms (mean)
  of a health decrease over five minutes.
- `IdleAutomation` (`idle-automation` in `config.yml`) flags anti-AFK modules
  that jump or swing sparsely for minutes while the player sends no rotation,
  movement or interaction.
- `PackSpoof` (`pack-spoof` in `config.yml`) flags resource-pack spoofing:
  two or more packs reported loaded 293-360 ms after acceptance, the fixed
  timer of Server RP spoof modules; real downloads finish at network speed.
- `FireworkCadence` (`elytra-analysis` in `config.yml`) flags elytra boost
  automation: eight or more consecutive firework boosts with machine-constant
  intervals (CV at or below 0.05 in the 700-4000 ms band), the signature of
  Super Fire Work style pulse modes.
- `AutoFish` (`fishing-analysis` in `config.yml`) flags auto-fishing: three
  or more reels landing within 120 ms of the bobber splash sound, far inside
  human audio-visual reaction time; anything slower consumes the bite and
  cannot double count the mandatory recast.

All automation heuristics are experimental; each shares a punishment ladder
(`Autoclicker`, `AimHeuristics`, or `Misc`) and can be staged with
`shadow-checks`.

### Staging and tooling

- `shadow-checks` in `config.yml` stages any check in alert-and-log-only mode,
  suppressing webhooks, proxy broadcasts, kicks and bans for that check until
  the operator removes it from the list.
- `/aethel profile <player>` renders the automation metrics behind these
  checks (attack CPS, attack cadence CV, rotating-attack fraction, container
  click rate, idle events, tool-swap cycles, damage reactions, mace smash
  band, fixed-gap pack reports, quick reels, firework boosts) so staff can
  eyeball a suspect without reading flag history.

### Known limitations

- Aim-assist modules that nudge the camera by only a few degrees per tick in
  line with the server-visible motion cannot be separated from a mouse
  without entity-relative position history; they are intentionally not
  guessed at.
- Factor-based rotation smoothing decays faster than the 0.18 ratio floor or
  slower than 0.995 is out of band by design; in practice those curves decay
  too fast to fill a series or too slow to measure above aim jitter.
- Aura configurations running rotation speeds under roughly 10°/tick per
  axis stay under the rotating-attack threshold by design; lowering it risks
  flagging strafe-flick combat.
- Client-side-only cheats (freecam, nametags, sound alerts) are out of scope
  for a server-side anticheat.
- The offhand and tool-automation checks anchor on damage and dig cycles;
  macros that fire on keyboard-only triggers with no server-visible anchor
  degrade to the generic rate checks.
- Chat-prompt automation (auto-login / auto-accept modules that answer server
  text) is out of scope: the prompts are plugin-specific and a fast answer is
  indistinguishable from a pasted command macro.
- The fishing reaction check matches the modern bobber splash sound; legacy
  1.8 sound naming variations are not covered.

## Building

Requires JDK 21.

```
./gradlew build
```

The Bukkit jar is produced in `bukkit/build/libs`, the Fabric jar in `fabric/build/libs`.
Release builds run in CI and are published automatically to GitHub Releases.

## License

Licensed under the GNU General Public License v3.0. AethelAC is derived from
[Grim Anticheat](https://github.com/GrimAnticheat/Grim); upstream copyright notices are retained.
