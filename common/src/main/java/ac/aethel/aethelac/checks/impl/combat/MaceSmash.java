package ac.aethel.aethelac.checks.impl.combat;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects mace smash-timing automation: every swing lands in the minimal
 * smash window.
 *
 * <p>The mace smash bonus activates past 1.5 blocks of falling; players time
 * it anywhere in a wide band because natural leaps and knockback never hold
 * one exact height. MaceTarget style modules fire at the threshold every
 * time, so their smash attacks pile into the smallest possible fall distance
 * window with machine consistency.</p>
 */
@CheckData(name = "MaceSmash", stableKey = "aethel.combat.mace_smash",
        description = "Mace smash attacks pinned to the minimal fall window", experimental = true)
public class MaceSmash extends Check implements PacketReceiveListener {
    private static final int CAPACITY = 16;
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;

    private final boolean[] narrow = new boolean[CAPACITY];
    private int index;
    private int size;
    private long lastFlagNanos;

    private double bandMin = 1.5D;
    private double bandMax = 1.7D;
    private int minAttacks = 10;
    private double bandFraction = 0.9D;

    public MaceSmash(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        bandMin = config.getDoubleElse("attack-patterns.mace-band-min", 1.5D);
        bandMax = config.getDoubleElse("attack-patterns.mace-band-max", 1.7D);
        minAttacks = Math.max(4, config.getIntElse("attack-patterns.mace-min-attacks", 10));
        bandFraction = config.getDoubleElse("attack-patterns.mace-band-fraction", 0.9D);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        boolean attack;
        if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            attack = true;
        } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            attack = new WrapperPlayClientInteractEntity(event).getAction()
                    == WrapperPlayClientInteractEntity.InteractAction.ATTACK;
        } else {
            return;
        }
        if (!attack || player.fallDistance < bandMin) {
            return;
        }

        ItemStack held = player.inventory.getHeldItem();
        if (held == null || held.isEmpty() || !held.getType().getName().toString().endsWith(":mace")) {
            return;
        }

        boolean inBand = player.fallDistance <= bandMax;
        narrow[index] = inBand;
        index = (index + 1) % CAPACITY;
        if (size < CAPACITY) {
            size++;
        }

        if (size < minAttacks) {
            return;
        }

        long now = System.nanoTime();
        if (now - lastFlagNanos < FLAG_COOLDOWN_NANOS) {
            return;
        }

        int inBandCount = 0;
        for (int k = 0; k < size; k++) {
            if (narrow[(index - 1 - k + CAPACITY * 2) % CAPACITY]) {
                inBandCount++;
            }
        }
        double fraction = inBandCount / (double) size;
        if (fraction >= bandFraction) {
            flag(String.format(Locale.ROOT, "%d/%d smash attacks in %.2f-%.2f fall window",
                    inBandCount, size, bandMin, bandMax));
            lastFlagNanos = now;
        }
    }

    public double getBandFraction() {
        if (size == 0) {
            return 0.0D;
        }
        int inBandCount = 0;
        for (boolean value : narrow) {
            if (value) {
                inBandCount++;
            }
        }
        return inBandCount / (double) size;
    }
}
