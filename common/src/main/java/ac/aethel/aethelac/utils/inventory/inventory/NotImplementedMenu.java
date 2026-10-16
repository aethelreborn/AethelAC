package ac.aethel.aethelac.utils.inventory.inventory;

import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.inventory.Inventory;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;

public class NotImplementedMenu extends AbstractContainerMenu {
    public NotImplementedMenu(AethelPlayer player, Inventory playerInventory) {
        super(player, playerInventory);
        player.inventory.isPacketInventoryActive = false;
        player.inventory.needResend = true;
    }

    @Override
    public void doClick(int button, int slotID, WrapperPlayClientClickWindow.WindowClickType clickType) {

    }
}
