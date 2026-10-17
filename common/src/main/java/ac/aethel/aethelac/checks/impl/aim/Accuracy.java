package ac.aethel.aethelac.checks.impl.aim;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.aethel.aethelac.utils.data.packetentity.PacketEntity;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Detects hitbox-center locking.
 *
 * <p>A human clicking a target hits a roughly uniform point on the visible
 * hitbox; the crosshair ray passes within a couple of centimetres of the
 * exact hitbox center only by chance. Aim modules that lock the center -
 * including heavily humanized ones with easing and jitter - keep the ray
 * pinned to the center across most attacks.</p>
 */
@CheckData(name = "Accuracy", stableKey = "aethel.aim.accuracy",
        description = "Crosshair pinned to the hitbox center", experimental = true)
public class Accuracy extends Check implements PacketReceiveListener {
    private static final long FLAG_COOLDOWN_NANOS = 5_000_000_000L;
    private static final int CAPACITY = 16;

    private final boolean[] precision = new boolean[CAPACITY];
    private int attackCount;
    private int attackIndex;

    private long lastFlagNanos;

    private double centerOffset;
    private int minAttacks;
    private double precisionFraction;

    public Accuracy(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        centerOffset = config.getDoubleElse("aim-analysis.accuracy-center-offset", 0.025D);
        minAttacks = Math.max(8, config.getIntElse("aim-analysis.accuracy-min-attacks", 12));
        precisionFraction = config.getDoubleElse("aim-analysis.accuracy-precision-fraction", 0.75D);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
            return;
        }

        WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
        if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            return;
        }

        PacketEntity target = player.compensatedEntities.getEntity(packet.getEntityId());
        if (target == null || target == player.compensatedEntities.self) {
            return;
        }

        SimpleCollisionBox box = target.getPossibleCollisionBoxes();
        double centerX = (box.minX + box.maxX) / 2.0D;
        double centerY = (box.minY + box.maxY) / 2.0D;
        double centerZ = (box.minZ + box.maxZ) / 2.0D;

        double eyeX = player.x;
        double eyeY = player.y + player.getEyeHeight();
        double eyeZ = player.z;

        double toX = centerX - eyeX;
        double toY = centerY - eyeY;
        double toZ = centerZ - eyeZ;

        double distanceSq = toX * toX + toY * toY + toZ * toZ;
        if (distanceSq < 0.25D || distanceSq > 20.25D) {
            return;
        }

        double yawRad = Math.toRadians(player.yaw);
        double pitchRad = Math.toRadians(player.pitch);
        double dirX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double dirY = -Math.sin(pitchRad);
        double dirZ = Math.cos(yawRad) * Math.cos(pitchRad);

        double dot = toX * dirX + toY * dirY + toZ * dirZ;
        if (dot <= 0.0D) {
            return;
        }

        double perpendicular = Math.sqrt(Math.max(0.0D, distanceSq - dot * dot));

        precision[attackIndex] = perpendicular <= centerOffset;
        attackIndex = (attackIndex + 1) % CAPACITY;
        if (attackCount < CAPACITY) {
            attackCount++;
        }

        if (attackCount < minAttacks || System.nanoTime() - lastFlagNanos < FLAG_COOLDOWN_NANOS) {
            return;
        }

        int precise = 0;
        for (int i = 0; i < attackCount; i++) {
            if (precision[i]) {
                precise++;
            }
        }

        if ((double) precise / attackCount >= precisionFraction) {
            flag(String.format(Locale.ROOT,
                    "%d of %d attacks within %.3f blocks of the hitbox center", precise, attackCount, centerOffset));
            lastFlagNanos = System.nanoTime();
            attackCount = 0;
            attackIndex = 0;
        }
    }
}
