package ac.aethel.aethelac.checks.impl.combat;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.checks.type.PostPredictionListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSpectateEntity;

import java.util.ArrayList;

@CheckData(name = "MultiInteractA", stableKey = "aethel.multiinteract.multiple_targets", description = "Interacted with multiple entities in the same tick", experimental = true)
public class MultiInteractA extends Check implements PacketReceiveListener, PostPredictionListener {
    private static final Verbose V =
            Verbose.of("lastEntity={sint}, entity={sint}, lastSneaking={bool}, sneaking={bool}");

    private final ArrayList<FlagData> flags = new ArrayList<>();
    private int lastEntity;
    private boolean lastSneaking;
    private boolean hasInteracted = false;

    public MultiInteractA(final AethelPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            int entity = packet.getEntityId();
            boolean sneaking = packet.isSneaking().orElse(false);
            onInteract(event, entity, sneaking);
        }

        if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            WrapperPlayClientAttack packet = new WrapperPlayClientAttack(event);
            onInteract(event, packet.getEntityId(), lastSneaking);
        }

        if (event.getPacketType() == PacketType.Play.Client.SPECTATE_ENTITY) {
            WrapperPlayClientSpectateEntity packet = new WrapperPlayClientSpectateEntity(event);
            onInteract(event, packet.getEntityId(), lastSneaking);
        }

        if (!player.cameraEntity.isSelf() || isTickPacket(event.getPacketType())) {
            hasInteracted = false;
        }
    }

    private void onInteract(PacketReceiveEvent event, int entity, boolean sneaking) {
        if (hasInteracted && (entity != lastEntity || sneaking != lastSneaking)) {
            if (!player.canSkipTicks()) {
                if (flag(V.write(verbose()).sint(lastEntity).sint(entity).bool(lastSneaking).bool(sneaking)) && shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            } else {
                flags.add(new FlagData(lastEntity, entity, lastSneaking, sneaking));
            }
        }

        lastEntity = entity;
        lastSneaking = sneaking;
        hasInteracted = true;
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (!player.canSkipTicks()) return;

        if (player.isTickingReliablyFor(3)) {
            for (FlagData data : flags) {
                flag(V.write(verbose()).sint(data.lastEntity()).sint(data.entity()).bool(data.lastSneaking()).bool(data.sneaking()));
            }
        }

        flags.clear();
    }

    private record FlagData(int lastEntity, int entity, boolean lastSneaking, boolean sneaking) {}
}
