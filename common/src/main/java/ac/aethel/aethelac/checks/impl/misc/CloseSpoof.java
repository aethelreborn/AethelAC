package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects simulated inventory closing: a close-window packet fired
 * immediately before an attack, cycle after cycle, with no inventory ever
 * having been open.
 *
 * <p>Closing an inventory client-side is silent and instant, so a human
 * pressing E mid-fight and clicking within the same 50 ms is rare once and
 * unheard of repeatedly; the SimulateInventoryClosing module of modern
 * aura clients fires the close before every attack to reset server-side
 * item-usage state.</p>
 */
@CheckData(name = "CloseSpoof", stableKey = "aethel.misc.close_spoof",
        description = "Fake inventory close before attacks", experimental = true)
public class CloseSpoof extends Check implements PacketReceiveListener {
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;
    private static final int CAPACITY = 8;

    private final long[] pairNanos = new long[CAPACITY];
    private int pairCount;
    private int pairIndex;

    private long lastFlagNanos;
    private long pendingCloseNanos;

    private long maxGapNanos;
    private int minPairs;
    private long counterNanos;

    public CloseSpoof(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        maxGapNanos = Math.max(1, config.getIntElse("inventory-automation.close-attack-max-ms", 50)) * 1_000_000L;
        minPairs = Math.max(2, config.getIntElse("inventory-automation.close-attack-min-pairs", 3));
        counterNanos = Math.max(1, config.getIntElse("inventory-automation.close-attack-counter-seconds", 60)) * 1_000_000_000L;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            if (player.serverOpenedInventoryThisTick || player.intersectedWithNetherPortal) {
                pendingCloseNanos = 0;
                return;
            }
            pendingCloseNanos = System.nanoTime();
            return;
        }

        boolean attack;
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            attack = new WrapperPlayClientInteractEntity(event).getAction()
                    == WrapperPlayClientInteractEntity.InteractAction.ATTACK;
        } else if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            attack = true;
        } else {
            return;
        }

        if (!attack || pendingCloseNanos == 0) {
            return;
        }

        long now = System.nanoTime();
        long gap = now - pendingCloseNanos;
        pendingCloseNanos = 0;
        if (gap > maxGapNanos) {
            return;
        }

        pairNanos[pairIndex] = now;
        pairIndex = (pairIndex + 1) % CAPACITY;
        if (pairCount < CAPACITY) {
            pairCount++;
        }

        if (pairCount < minPairs || now - lastFlagNanos < FLAG_COOLDOWN_NANOS) {
            return;
        }

        int recent = 0;
        for (int i = 0; i < pairCount; i++) {
            if (now - pairNanos[i] <= counterNanos) {
                recent++;
            }
        }

        if (recent >= minPairs) {
            flag(String.format(Locale.ROOT, "%d fake inventory closes before attacks (last %.0f ms)",
                    recent, gap / 1_000_000.0D));
            lastFlagNanos = now;
            pairCount = 0;
            pairIndex = 0;
        }
    }
}
