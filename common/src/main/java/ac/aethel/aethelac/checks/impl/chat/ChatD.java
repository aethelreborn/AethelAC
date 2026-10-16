package ac.aethel.aethelac.checks.impl.chat;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PreViaPacketReceiveListener;
import ac.aethel.aethelac.player.AethelPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.common.client.WrapperCommonClientSettings;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSettings;

@CheckData(name = "ChatD", stableKey = "aethel.exploit.chat_while_hidden", description = "Chatting while chat is hidden")
public class ChatD extends Check implements PreViaPacketReceiveListener {
    private boolean hidden;

    public ChatD(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onPreViaPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CHAT_MESSAGE
                || event.getPacketType() == PacketType.Play.Client.CHAT_COMMAND_UNSIGNED
                || event.getPacketType() == PacketType.Play.Client.CHAT_COMMAND) {
            if (hidden && flag() && shouldModifyPackets()) {
                event.setCancelled(true);
                player.onPacketCancel();
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.CLIENT_SETTINGS || event.getPacketType() == PacketType.Configuration.Client.CLIENT_SETTINGS) {
            hidden = new WrapperPlayClientSettings(event).getChatVisibility() == WrapperCommonClientSettings.ChatVisibility.HIDDEN;
        }
    }
}
