package ac.aethel.aethelac.checks.impl.multiactions;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "MultiActionsC", stableKey = "aethel.multiactions.inventory_click_while_moving", description = "Clicked in inventory while moving")
public class MultiActionsC extends Check implements PacketReceiveListener {
    private static final Verbose V = Verbose.of("sprinting={bool}, sneaking={bool}, input={bool}");

    public MultiActionsC(AethelPlayer player) {
        super(player);
    }

    @Contract(pure = true)
    public static boolean isVerboseSprinting(@NotNull AethelPlayer player) {
        return player.isSprinting && (!player.isSwimming || !player.clientClaimsLastOnGround);
    }

    @Contract(pure = true)
    public static boolean isVerboseSneaking(@NotNull AethelPlayer player) {
        return player.isSneaking && (player.getClientVersion().isOlderThan(ClientVersion.V_1_15) || player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_21_9));
    }

    @Contract(pure = true)
    public static boolean isVerboseInput(@NotNull AethelPlayer player) {
        return player.supportsEndTick() && player.packetStateData.knownInput.moving();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.CLICK_WINDOW) return;
        if (player.serverOpenedInventoryThisTick) return;

        boolean sprinting = isVerboseSprinting(player);
        boolean sneaking = isVerboseSneaking(player);
        boolean input = isVerboseInput(player);
        if (!sprinting && !sneaking && !input) return;

        if (flag(V.write(verbose()).bool(sprinting).bool(sneaking).bool(input)) && shouldModifyPackets()) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }
}
