package ac.aethel.aethelac.checks.impl.sprint;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.checks.type.PostPredictionListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;

@CheckData(name = "SprintE", stableKey = "aethel.sprint.wall", description = "Sprinting while colliding with a wall", setback = 5, experimental = true)
public class SprintE extends Check implements PacketReceiveListener, PostPredictionListener {
    private boolean startedSprintingThisTick, wasHardHorizontalCollision;

    public SprintE(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
            if (new WrapperPlayClientEntityAction(event).getAction() == WrapperPlayClientEntityAction.Action.START_SPRINTING) {
                startedSprintingThisTick = true;
            }
        }
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (!predictionComplete.isChecked()) return;

        if (wasHardHorizontalCollision && !startedSprintingThisTick && !player.uncertaintyHandler.isNearGlitchyBlock
                && !player.inVehicle() && !player.uncertaintyHandler.lastVehicleSwitch.hasOccurredSince(0)
                && (!player.wasTouchingWater || player.getClientVersion().isOlderThan(ClientVersion.V_1_13))
                && player.wasLastPredictionCompleteChecked) {
            if (player.isSprinting) {
                flagWithSetback();
            } else {
                reward();
            }
        }

        wasHardHorizontalCollision = player.horizontalCollision && !player.softHorizontalCollision && player.wasLastPredictionCompleteChecked;
        startedSprintingThisTick = false;
    }
}
