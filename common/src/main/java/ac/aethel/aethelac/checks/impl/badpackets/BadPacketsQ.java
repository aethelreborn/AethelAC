package ac.aethel.aethelac.checks.impl.badpackets;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.impl.verbose.VerboseCodecs;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;

@CheckData(name = "BadPacketsQ", stableKey = "aethel.badpackets.invalid_horse_jump", description = "Sent a horse jump packet with an invalid entity, action, or boost value")
public class BadPacketsQ extends Check implements PacketReceiveListener {
    private static final Verbose V = Verbose.of("boost={sint}, action={entityaction}, entity={sint}");

    public BadPacketsQ(final AethelPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == Client.ENTITY_ACTION) {
            WrapperPlayClientEntityAction wrapper = new WrapperPlayClientEntityAction(event);
            int boost = wrapper.getJumpBoost();
            WrapperPlayClientEntityAction.Action action = wrapper.getAction();
            int entity = wrapper.getEntityId();
            // you are able to send negative jump boost, how and why!?
            if (Math.abs(boost) > 100
                    || entity != player.entityID
                    || wrapper.getAction() != WrapperPlayClientEntityAction.Action.START_JUMPING_WITH_HORSE && boost != 0) {
                int actionId = VerboseCodecs.enumId(action);
                if (flag(V.write(verbose()).sint(boost).uint(actionId).sint(entity)) && shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            }
        }
    }
}
