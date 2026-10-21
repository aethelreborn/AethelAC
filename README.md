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
- Interaction rays (reach, dig and placement geometry) are measured from the
  position the server last received, which is the position the client itself
  raycast from: both 1.8 and modern clients build the interaction packet
  before the tick that moves and reports the player, so no eye-height
  correction for falling is applied - a player is never charged for a tick
  that has not happened yet. The only interaction component that runs a tick
  behind is the client's yaw and pitch, which the ray checks already try as
  candidates.
- `ThroughWalls` rejects attacks and entity interactions whose aim ray crosses
  a solid block before it reaches the target. The client's entity pick only
  returns a target that is nearer than the first block hit, so this geometry is
  impossible for an unmodified client. The click uses the previous render
  frame's pick, and the server already holds the rotation that frame was
  rendered with - both 1.8 and modern clients queue the interaction before the
  tick that reports the new rotation - so the verdict is taken immediately
  against that held rotation. It only runs when the last two rotations differ
  by at most six degrees, which bounds the render interpolation between them.
  Blocks changed within the last round trip are ignored, as are actions within
  a second of the player's own block break (a rejected client-side break can
  leave the client seeing air where the server still has the block).
  Placements are deliberately not exempt: a predicted placement makes the
  client see more than the server, which can only hide an action. The target's
  lag-compensated hitbox is measured from the highest possible eye height, so
  every gate can only hide an action, never invent one. If a block-change burst
  ever exceeds the tracked history, the check stays silent for that five-second
  window instead of guessing which walls are fresh.
- Punishments ship tiered ladders per check group: alerts from the first flag,
  console kick at 30-80 violations, and a 30-day ban at 40-140 violations
  depending on the group. Violation memory spans ten minutes.

### Client fingerprints

- Known cheat-client fingerprints (client brands and registered plugin
  channels, see `client-fingerprint` in `config.yml`) flag the `Fingerprint`
  check: alerts and logs from the first match, a console kick at 15 repeated
  matches, and a 30-day ban at 30.
- `CheatClient` is the strict one. It reads three artefacts a client sends
  whether it wants to or not: the `minecraft:brand` payload, the channel list
  from `minecraft:register` (and the namespace in front of the colon), and
  the mod ids of a Forge/FML handshake the client volunteers. Vape, Prestige,
  Krypton, Myau, LiquidBounce, Meteor, Wurst, Aristois, Sigma, Impact and the
  rest ship in the `cheat-client` lists in `config.yml`; a match kicks the
  player on the first hit and bans on the fourth, so a known cheat client
  cannot sit on a server. The lists are the union of the published
  fingerprint sets of several anticheats, trimmed of the names legitimate
  clients also carry (Sodium, Hanabi, OptiFine, Iris, Lunar, Badlion,
  Prism, Forge, Fabric).
- The honest limit: a ghost client that spoofs its brand to `vanilla` and
  registers no channel leaves nothing for any brand or channel check. Against
  those users the teeth are the behavioural tiers - an attack with no arm
  swing, timer drift, impossible packets - not the fingerprint.

### Automation detection suite

Attack patterns (`attack-patterns` in `config.yml`):

- `Autoclicker` flags sustained 18+ CPS over a full minute, 20+ CPS over
  twenty seconds, machine-regular click intervals, or high-rate clicking that
  never produces human burst pairs (alerts from 10 violations, console kick at
  80, 30-day ban at 140).
- `SlowCadence` flags triggerbot-style metronome cadence at human-plausible
  rates in two tiers: 48 intervals with a coefficient of variation at or below
  0.05, or 100 intervals at or below 0.08 for randomized-delay bots.
- `UniformClick` flags uniformly randomized autoclickers: 200 click intervals
  at 5+ CPS whose excess kurtosis falls below 0.5 - a bounded random delay
  never leaves its band, and human click streams are always heavy-tailed.
- `SubTickClick` flags attacks delivered faster than the client input loop
  allows: repeated attacks within 25 ms mean multiple packets per input tick,
  the signature of packet-flooding kill-aura modules and duplicated attack
  exploits. Isolated delivery hiccups are ignored; only repeated sub-tick
  bursts within the counter window flag.
- `SubTickBreak` applies the same input-loop bound to digging: repeated dig
  starts within 25 ms mean a single input tick produced multiple start
  packets - nuker floods and duplicated dig exploits, including same-position
  restarts MultiBreak's per-tick attribution cannot see.
- `NoBreakDelay` (experimental) flags chain-mining modules that patched the
  client's post-break cooldown out: after every finished break the vanilla
  client arms a five tick delay before it starts the next dig - on any
  position, in survival and creative alike - so a start landing inside
  `post-break-delay-ms` (150 ms) of the previous break, three or more times
  in ten seconds, is a cooldown that was removed. Events are dropped while
  the client's own tick packets (movement packets, or the arm swing it sends
  once per tick while mining) show it ticked through the window, so a stalled
  connection cannot look like a removed delay; instant-break blocks,
  single-tick breaks, creative and spectator players are exempt.
- `AutoBlock` flags shield-drain and auto-block modules: block use toggled
  within 50 ms of attacks, four or more times a minute. Re-pressing the
  shield key that fast on every click is beyond reliable human coordination,
  and the toggle rides the module's own attack tick. On 1.8 protocols a held
  sword counts as a blocking item, catching legacy sword-block modules; on
  modern protocols shield release packets count as toggles too.
- `NoSwingAttack` flags silent-aura modes that strip the arm animation: four
  or more attacks in a row with no swing within 200 ms.
- `MaceSmash` flags mace smash-timing automation: ten or more recent smash
  attacks with 90% or more pinned to the minimal 1.5-1.7 block fall window,
  where natural leaps and knockback never cluster.
- `SilentAim` flags silent auras: a rotation snap toward the target around an
  attack, followed by a revert to the exact pre-attack rotation inside 90 ms -
  the on-tick rotation pattern of LiquidBounce-style silent aim that a human
  cannot reproduce around a click.
- `Accuracy` flags hitbox-center locking: the crosshair ray passing within
  0.025 blocks of the exact hitbox center for three quarters of the attack
  ring - a precision humans only reach by chance.
- `SwapAttack` flags silent-hotbar meta swaps: the held slot changing within
  15 ms of an attack (or reverting back within 250 ms), the mechanism behind
  mace swap, lunge/attribute swap and breach swap macros and the zero
  switch-delay default of modern aura clients.
- `PearlSwap` flags pearl-flash macros: an ender pearl thrown within 15 ms of
  selecting it (or the slot reverting instantly), the signature of pearl
  catch and switchback throw modules.
- `TriggerBot` flags attacks fired on the first tick a target enters the
  crosshair of a near-static rotation - sub-100 ms entries a human cannot
  react to, matching the 0 ms default attack delay of triggerbot modules.
  A second tier separates delay-configured modules (100-120 ms "legit"
  delays) from fast humans through crosshair-entry precision instead of a
  single timing bound: the attack must also land within 0.10 blocks of the
  hitbox center. A third, metronome tier catches modules that sleep
  100-450 ms between detection and attack by stream consistency instead of
  a single bound: over the last 20 crosshair entries lasting at least
  300 ms, the module answers at least 85% of them with a mean reaction in
  100-280 ms and a coefficient of variation at or below 0.10 (simulated:
  delayed bots 0.96-1.0 stream flag rate, plausible humans at or below
  0.07), and a late band covers narrower jittered sleeps up to 460 ms at a
  coefficient of variation at or below 0.04 (fixed and 50 ms-wide delayed
  modules across 300-450 ms all flag at a 1.0 simulated stream rate,
  plausible humans stay at or below 0.045).
- `CrystalCadence` flags instant crystal auras: eight or more end crystals
  broken within 150 ms of their own spawn with a cadence CV at or below 0.6,
  or quantized to three ticks or less across the ring, a pace only a
  placer/destroyer module attacking the spawn packet can hold.
- `AnchorCadence` flags anchor macros: eight or more charge-detonate cycles on
  the same respawn anchor position, each pair of clicks within 150 ms of each
  other with a machine-consistent cadence, matching the two-tick step delay of
  AutoAnchor-style mods and air-anchor double clicks.
- `AirPlace` flags placement optimizer modules clicking blocks that only exist
  client-side: a click on a position that is air in our world counts only when
  the position has been stable air with no block history and no recent own
  placement, so ghost-block desync cannot false it.
- All timing-sensitive combat checks skip attacks from players whose
  transaction ping exceeds `attack-patterns.timing-max-ping-ms` (150 ms by
  default) to absorb out-of-order packet batching on high latency links.
  `attack-patterns.cancel-attack-over-ping-ms` (disabled by default) optionally
  cancels attack packets above a ping ceiling so the hits never register. It
  is a policy switch rather than a check: it never flags, so it is not listed
  in `punishments.yml`, and it runs whenever the ceiling is positive while
  still honouring the per-player modification permissions.

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
  or more direct armor-slot clicks inside 400 ms), menu bots (twenty
  consecutive clicks with near-constant intervals, CV at or below 0.045, in
  the 110-900 ms band), and zero-reaction looting (three or more first clicks
  inside ten seconds landing less than 70 ms above the round-trip floor of a
  freshly opened window). The reaction tier rests on a protocol fact: a click
  that references a new container id can only have been produced by a client
  that already received the open packet, so the server-side gap between
  sending the window and receiving the first click is at least one round
  trip. Instaloot and loot-aura modules answer the packet and show a gap
  equal to the round-trip floor; human input needs a visual reaction first.
- `AutoToolChain` (same section) flags AutoTool modules that cycle the hotbar
  to a tool and back around every dig (six or more sub-400 ms cycles a
  minute).
- `DropBurst` (same section) flags drop-everything macros: twelve or more
  drops inside a second.
- `CloseSpoof` (same section) flags simulated inventory closing: a
  close-window packet within 50 ms before an attack three or more times in a
  minute - the SimulateInventoryClosing module of modern aura clients fires
  one before every hit.
- `JumpCadence` (`jump-analysis` in `config.yml`) flags metronome jump
  tapping: twelve or more one-to-two-tick jump pulses with an interval CV at
  or below 0.12, the signature of W-tap and D-tap macro modules.
- `JumpReset` flags automatic jump resets: a jump within two ticks of a
  knockback (widened by one tick per 50 ms of ping), five or more times a
  minute. Jump-reset modules pin the reset to the velocity packet on every
  hit; human resets land in a wide organic band.
- `InvWalk` (`inventory-automation` in `config.yml`) flags world interactions
  and rotation updates while a container is open: a vanilla GUI captures the
  mouse completely, so attacking, placing or digging seconds after an
  open-window packet is only possible for invwalk and loot aura modules, and
  a counted rotation stream (0.5 degrees or more of summed yaw+pitch per
  update, twice within ten seconds, once the open age exceeds twice the
  transaction ping plus 100 ms) exposes GUI-rotation modules that aim with
  the container still up. Bedrock (Geyser/Floodgate) players are exempt from
  the rotation tier.
- `HiddenAim` (`aim-analysis` in `config.yml`) flags precision-tracking of a
  moving, fully occluded player: the vanilla client cannot show a hidden
  target, so holding the crosshair within a few degrees of its hitbox center
  for ten rotation updates while it moves is only possible with ESP-fed
  smoothing. Occlusion is a collision-shape ray to the target center (glass
  exempt), so partial blocks never count.
- `RotationGrid` (same section) flags rotation streams that interleave
  quantized mouse movement with off-grid synthetic corrections: every
  physical mouse delta is a whole multiple of one sensitivity quantum, so
  the check fits the smallest populated quantum cluster of the delta
  histogram and counts combat rotation deltas that are neither multiples of
  that grid nor explained by another populated cluster. A burst tier (8 of
  the last 30 armed probes) catches dense corrections; a horizon tier (5%
  of the last 300 armed probes) catches slow-burn ones, and the fitted
  quantum survives two minutes of fully synthetic rotation so sustained
  fights cannot close the gate mid-combat. Streams that never show
  quantized deltas (smoothing-only, cinematic, protocol-quantized 1.8
  rotations) never open the grid gate and stay silent.
- `TrackingLock` (same section) flags smoothing aim-assist glued to a
  strafing target: only ticks where the target's direction sweeps at least
  1.5 degrees per tick are judged, a swept tick is locked when the crosshair
  sits within 1.5 degrees of the hitbox center, and a flag needs ninety
  percent of a 60-tick window locked through at least three strafe
  reversals. Sensorimotor latency keeps human tracking several degrees
  behind a reversaling target; tick-rate exponential smoothing holds the
  sub-degree lock indefinitely. Straight-line motion without reversals is
  exempt by design.
- `DamageReaction` (`reaction-analysis` in `config.yml`) flags
  ClickPearl/AutoGApple/AutoSwap/AutoArmor style automation: six or more item
  uses, offhand manipulations (offhand clicks or swap actions) or armor-slot
  clicks within 120 ms (mean) of a health decrease over five minutes. The
  measured reaction carries one full round trip, so the check subtracts the
  measured transaction ping and judges the client-side reaction - without
  that compensation the tier cannot fire at all on a server whose ping sits
  at or above the ceiling (a 140 ms server blinded it completely, simulated
  bot rate 0.000; with the compensation bots flag 1.000 at 0-400 ms ping
  while every modelled human class stays at 0.000).
- `IdleAutomation` (`idle-automation` in `config.yml`) flags anti-AFK modules
  that jump or swing sparsely for minutes while the player sends no rotation,
  movement or interaction.
- `PackSpoof` (`pack-spoof` in `config.yml`) flags resource-pack spoofing:
  two or more packs reported loaded 293-360 ms after acceptance, the fixed
  timer of Server RP spoof modules; real downloads finish at network speed.
- `PingSpoof` (`ping-spoof` in `config.yml`) flags keepalive latency
  spoofing: the check times the server's own keepalive round trips and pairs
  every one with the transaction round trips around it - a vanilla client
  keeps both echo streams consistent on the same socket, while a spoofing
  module delays only the keepalive replies it knows the server times. A
  median paired gap of `min-divergence-ms` (100 ms) across four or more
  pairs, repeated three times in fifteen seconds, flags. Clients whose own
  keepalive spread exceeds `max-spread-ms` (80 ms; frame quantization, bad
  links) and frame-locked steady streams under `min-keepalive-spread-ms`
  (2 ms) are never judged, and bedrock (Geyser/Floodgate) players are
  exempt.
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

### Detection reliability tiers

Checks are deliberately split into two reliability classes so operators know
what a flag is worth:

- **Invariant checks** flag states the vanilla client cannot legally produce:
  failed movement predictions, desync between placements and world state,
  protocol ordering violations, world interactions while a container GUI
  captures input, dig/place state machines, and arithmetic impossibilities
  (durability, cadence rings). These are proof-grade on their own.
- **Behavioral checks** (aim heuristics, triggerbot timing, autoclicker
  statistics, client fingerprints) flag improbable patterns rather than
  impossibilities. They stay behind `experimental-checks` plus per-check ping
  gates and are meant to be layered over the invariant set, not to stand
  alone against a cheater who randomizes their signatures.

**Deployment note - `experimental-checks`:** with the switch set to `false`
every behavioral tier above is completely silent - the flag is dropped before
the violation counter, so there are no alerts, no punishments and no stored
evidence at all. The bundled default is `true`, but a config carried over from
before that default flipped keeps its old `false`, and the config upgrade path
used to append `false` for missing keys. A server in that state effectively
runs the invariant set alone, so the state is now reported loudly on the first
join: `AethelAC: N checks loaded (M experimental tiers enabled)`, or a warning
naming how many tiers are dormant. If you see the warning, set
`experimental-checks: true` in `config.yml`.

Active hardening roadmap for the behavioral families ("make the probabilistic
checks carry proof-like weight"):

- v4.1 (shipped): through-wall aim tracking - precision-tracking a moving,
  fully occluded player for sustained ticks is closer to an invariant than a
  threshold, because the information needed is not legally visible.
- v4.2 (shipped): reaction-time tiers - sub-120ms repeated reactions split
  from statistically fast humans by crosshair-entry precision, not by a
  single timing bound.
- v4.3 (shipped): autoclicker bounds - the sub-tick delivery window of the
  client input loop (at most one attack per tick) instead of
  distribution-shape tests.
- v4.11 (shipped): rotation grid consistency - a legitimate mouse stream can
  only produce whole multiples of one sensitivity quantum, so off-grid
  corrections are counted only after the player demonstrably uses quantized
  deltas; players whose input never lands on the grid stay silent.

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
- A rotation source that is synthetic on every update (never interleaving
  genuine quantized mouse deltas) never opens the `RotationGrid` gate; once
  a grid has been demonstrated, sustained synthetic phases stay covered by
  the quantum-memory and horizon tiers. That stream class is also the
  target of the variance and geometric-series family checks.
- The `TriggerBot` metronome tier deliberately trades away one implausible
  human profile: firing on roughly 90% of crosshair opportunities with a
  reaction spread under about 25 ms and no slow tail reaches bot territory.
  Plausible humans (50-75% fire rates, ex-Gaussian reactions) stay at or
  below a 0.07 stream flag rate in simulation.
- `TrackingLock` deliberately exempts straight-line motion (no direction
  reversals) because predictive human tracking of a uniformly moving target
  is legitimate; it also misses very soft smoothing (gain under roughly
  0.7), whose steady-state error floor sits above the lock angle at the
  swept-tick floor.
- The `InvWalk` rotation tier never judges rotations that land before the
  open-window packet has had twice the transaction ping plus 100 ms to
  reach the client, because those can be legitimate input sent while the
  window was still in flight; players on bedrock (Geyser/Floodgate) are
  exempt wholesale because their GUI semantics differ.
- The `FastClick` zero-reaction tier compares the first click of every
  opened window against the minimum of recent round-trip samples; a link
  that improves by tens of milliseconds after those samples, or blind
  mashing on a fast rhythm through menus the server opened without the
  player's own click, can cross that floor. Both need three such clicks
  inside ten seconds, players whose best round trip exceeds 300 ms are
  skipped, and bedrock (Geyser/Floodgate) players are exempt.
- The `NoBreakDelay` post-break tier never judges a start whose window
  contains four or more of the client's own tick packets; a player who
  releases the mouse between blocks, and whose breaks and re-clicks are
  delivered inside the same stall burst (roughly 300-400 ms of coalescing
  about once a second), can still be measured as a removed cooldown. Clean
  and moderate stall rates stay at a 0.000 simulated flag rate, which is why
  the check is experimental.
- The `PingSpoof` tier can only see one-sided spoofing: a client that delays
  keepalive *and* transaction replies by the same amount is invisible from
  the server side (that would need TCP-level round trips a plugin cannot
  read). Clients whose own echo spread exceeds `max-spread-ms` (very low
  frame rates, jittery links) and links tighter than
  `min-keepalive-spread-ms` are never judged, and transaction-side-only
  delay is deliberately not judged because it only makes the player look
  laggier. Vanilla 15 second keepalive intervals work, but judgement needs
  four pairs, so flags land about a minute in instead of seconds.
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
