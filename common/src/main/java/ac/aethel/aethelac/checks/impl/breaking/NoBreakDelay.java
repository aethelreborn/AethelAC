package ac.aethel.aethelac.checks.impl.breaking;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.BlockBreakListener;
import ac.aethel.aethelac.checks.type.PreViaPacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.BlockBreak;
import ac.aethel.aethelac.utils.nmsutil.BlockBreakSpeed;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.util.Vector3i;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Locale;

/**
 * Detects dig chains that removed the client's post-break cooldown.
 *
 * <p>When a break completes the vanilla client arms a five tick delay
 * (blockHitDelay on legacy clients, destroyDelay on 1.14+) that gates the next
 * dig start - including a start on a different position - for every break path,
 * creative instabuild and instant-break blocks included. A legit client
 * therefore cannot start the next dig sooner than ~6 client ticks (250-300 ms)
 * after the previous FINISHED_DIGGING, whether it is sweeping blocks while
 * holding attack or re-clicking every block. Chain-mining modules (VeinMiner
 * style tree fellers, PacketMine and "no break delay" tweaks) delete that delay
 * and deliver the next start one or two ticks later, which is what this tier
 * looks for.</p>
 *
 * <p>Wall-clock alone would not be safe: a server GC pause, a Netty stall or a
 * client uplink stall delivers the finish and the following start back-to-back,
 * so the measured gap collapses even though the client itself waited its ticks.
 * The tier therefore also counts the client's own tick clock inside the window -
 * movement/tick packets and arm animation packets, both ~1 per client tick while
 * mining - and ignores the event once the client produced enough of them: those
 * packets ride the same stall, so the evidence survives whatever collapsed the
 * wall clock. Isolated events are not punished either, only a repeated
 * pattern.</p>
 */
@CheckData(name = "NoBreakDelay", stableKey = "aethel.breaking.no_break_delay",
        description = "Started the next dig without the client's post-break cooldown", experimental = true)
public class NoBreakDelay extends Check implements BlockBreakListener, PreViaPacketReceiveListener {
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private final long[] ring = new long[8];
    private int index;
    private long lastFlagNanos;

    private long finishNanos;
    private Vector3i finishPos;
    private boolean armed;
    private int tickSignals;
    private int swingSignals;
    private boolean instantDig;

    private long minDelayNanos;
    private int signalFloor;
    private int minEvents;
    private long counterNanos;

    public NoBreakDelay(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        minDelayNanos = Math.max(1, config.getIntElse("attack-patterns.post-break-delay-ms", 150)) * 1_000_000L;
        signalFloor = Math.max(1, config.getIntElse("attack-patterns.post-break-tick-signals", 4));
        minEvents = Math.max(2, config.getIntElse("attack-patterns.post-break-min-events", 3));
        counterNanos = Math.max(1, config.getIntElse("attack-patterns.post-break-counter-seconds", 10)) * 1_000_000_000L;
    }

    @Override
    public void onBlockBreak(@NotNull BlockBreak blockBreak) {
        // Creative instabuild and spectator mining are gated by the same client
        // delay but break every tick by design; never judge them.
        if (player.gamemode == GameMode.CREATIVE || player.gamemode == GameMode.SPECTATOR) {
            return;
        }

        if (blockBreak.action == DiggingAction.START_DIGGING) {
            evaluatePreviousDig(blockBreak);

            // Blocks the held tool destroys in a single tick leave no hold window
            // for the cooldown to be measured against, and fast-click farming of
            // them is normal play - exempt them instead of guessing.
            instantDig = BlockBreakSpeed.getBlockDamage(player, blockBreak.block) >= 1.0D;
            return;
        }

        if (blockBreak.action != DiggingAction.FINISHED_DIGGING) {
            return;
        }

        armed = false;

        if (instantDig) {
            return;
        }

        armed = true;
        tickSignals = 0;
        swingSignals = 0;
        finishNanos = System.nanoTime();
        finishPos = blockBreak.position;
    }

    private void evaluatePreviousDig(@NotNull BlockBreak blockBreak) {
        if (!armed) {
            return;
        }

        armed = false;

        long now = System.nanoTime();
        long gap = now - finishNanos;

        if (gap >= minDelayNanos || finishPos == null || blockBreak.position.equals(finishPos)) {
            return;
        }

        // The client's own ticks inside the window prove it waited: a stall that
        // collapsed the wall clock collapsed this packet stream the same way.
        if (Math.max(tickSignals, swingSignals) >= signalFloor) {
            return;
        }

        ring[index] = now;
        index = (index + 1) % ring.length;

        int events = 0;
        for (long stamp : ring) {
            if (stamp != 0L && now - stamp <= counterNanos) {
                events++;
            }
        }

        if (events >= minEvents && now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
            flag(String.format(Locale.ROOT,
                    "started a new dig %.0f ms after finishing the previous break - the client waits five ticks before the next dig",
                    gap / 1.0E6D));
            lastFlagNanos = now;
            Arrays.fill(ring, 0L);
        }
    }

    @Override
    public void onPreViaPacketReceive(PacketReceiveEvent event) {
        if (!armed) {
            return;
        }

        if (isTickPacket(event.getPacketType())) {
            tickSignals++;
        } else if (isAnimation(event.getPacketType())) {
            swingSignals++;
        }
    }
}
