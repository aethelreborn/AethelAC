package ac.aethel.aethelac.checks.impl.crash;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PreViaPacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSelectBundleItem;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "CrashI", stableKey = "aethel.crash.invalid_bundle_slot", description = "Sent a bundle item selection with an invalid negative slot index")
public class CrashI extends Check implements PreViaPacketReceiveListener {
    private static final Verbose V = Verbose.of("selectedItemIndex={sint}");

    public CrashI(AethelPlayer player) {
        super(player);
    }

    @Override
    public boolean isApplicable() {
        return player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_21_2);
    }

    @Override
    public void onPreViaPacketReceive(@NotNull PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.SELECT_BUNDLE_ITEM) {
            int selectedItemIndex;
            try {
                selectedItemIndex = new WrapperPlayClientSelectBundleItem(event).getSelectedItemIndex();
            } catch (IllegalArgumentException e) {
                // thanks packetevents!
                if (e.getMessage().startsWith("Invalid selectedItemIndex: ")) {
                    selectedItemIndex = Integer.parseInt(e.getMessage().substring(27));
                } else {
                    throw e;
                }
            }

            if (selectedItemIndex < -1) {
                flag(V.write(verbose()).sint(selectedItemIndex));
                event.setCancelled(true);
                player.onPacketCancel();
            }
        }
    }
}
