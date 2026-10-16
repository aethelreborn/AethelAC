# AethelAC

Async, prediction-based anticheat for Minecraft Java Edition 1.8–26.3, built on [PacketEvents](https://github.com/retrooper/packetevents).

AethelAC simulates vanilla movement and physics server-side for every player, then compares the simulation with what the client actually sent. Bypasses require moving outside of the game's own physics, which makes the checks mathematically grounded instead of heuristic.

## Features

- Movement simulation for walking, sprinting, swimming, flying, riding, and knockback.
- Full per-player world replication, including latency-compensated block and inventory state.
- Packet-level validation of order, timing, and contents.
- Reach, aim, auto-clicker, timer, velocity, and packet-order checks.
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
| `/aethel profile <player>` | View client information |
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

The default configuration ships hardened for competitive PvP:

- Movement and knockback simulation setbacks fire immediately (0.02 block/tick
  threshold, 0.5 block advantage ceiling), with slow advantage decay.
- Reach flags from 3.0001 on 1.8 clients; impossible hits are cancelled.
- Timer checks allow 60 ms of drift; negative and vehicle timers are tightened.
- Packet spam, transaction time, and ping abuse limits are cut to a third of
  defaults; verbose flags are mirrored to the console.
- Punishments ship tiered ladders per check group: alerts from the first flag,
  console kick at 30-80 violations, and a 30-day ban at 40-140 violations
  depending on the group. Violation memory spans ten minutes.
- Known cheat-client fingerprints (client brands and registered plugin
  channels, see `client-fingerprint` in `config.yml`) flag the `Fingerprint`
  check: alerts and logs from the first match, a console kick at 15 repeated
  matches, and a 30-day ban at 30.

Everything lives in `config/*.yml`, `punishments/*.yml`, `messages/*.yml`, and
`databases/*.yml` and can be tuned per server.

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
