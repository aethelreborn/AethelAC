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

The default configuration is tuned for competitive PvP: immediate setbacks, tight reach
expansion, timer drift limits, and experimental checks enabled. Every check can be tuned
individually in `checks.yml`, alerts and punishments in `punishments.yml`, and history
storage in `database.yml`.

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
