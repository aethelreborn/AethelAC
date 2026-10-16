package ac.aethel.aethelac.events.packets;

import ac.aethel.aethelac.AethelAPI;
import ac.grim.grimac.api.event.events.GrimTransactionReceivedEvent;
import ac.grim.grimac.api.event.events.GrimTransactionSendEvent;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.data.ShortToLongPair;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientWindowConfirmation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowConfirmation;
import org.jetbrains.annotations.NotNull;

public class PacketPingListener extends PacketListenerAbstract {

    private static final GrimTransactionSendEvent.Channel SEND_CHANNEL = AethelAPI.INSTANCE.getEventBus().get(GrimTransactionSendEvent.class);
    private static final GrimTransactionReceivedEvent.Channel RECEIVED_CHANNEL = AethelAPI.INSTANCE.getEventBus().get(GrimTransactionReceivedEvent.class);

    // Must listen on LOWEST (or maybe low) to stop Tuinity packet limiter from kicking players for transaction/pong spam
    public PacketPingListener() {
        super(PacketListenerPriority.LOWEST);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.WINDOW_CONFIRMATION) {
            AethelPlayer player = AethelAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            WrapperPlayClientWindowConfirmation packet = new WrapperPlayClientWindowConfirmation(event);
            onReceiveTransaction(player, event, packet.getActionId());
        } else if (event.getPacketType() == PacketType.Play.Client.PONG) {
            AethelPlayer player = AethelAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            WrapperPlayClientPong packet = new WrapperPlayClientPong(event);
            onReceiveTransaction(player, event, packet.getId());
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.WINDOW_CONFIRMATION) {
            AethelPlayer player = AethelAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            WrapperPlayServerWindowConfirmation packet = new WrapperPlayServerWindowConfirmation(event);
            onSendTransaction(player, event, packet.getActionId());
        } else if (event.getPacketType() == PacketType.Play.Server.PING) {
            AethelPlayer player = AethelAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            WrapperPlayServerPing packet = new WrapperPlayServerPing(event);
            onSendTransaction(player, event, packet.getId());
        }
    }

    private static void onReceiveTransaction(@NotNull AethelPlayer player, @NotNull PacketReceiveEvent event, int id) {
        player.packetStateData.lastTransactionPacketWasValid = false;

        short shortId = (short) id;
        if (id != shortId // we only use the short range
                || id > 0 // we only use negative ids
                || !player.addTransactionResponse(shortId)) return;

        player.packetStateData.lastTransactionPacketWasValid = true;
        boolean shouldCancel = !AethelAPI.INSTANCE.getConfigManager().isDisablePongCancelling();
        if (shouldCancel) {
            // Not needed for vanilla as vanilla ignores this packet, needed for packet limiters
            event.setCancelled(true);
        }
        RECEIVED_CHANNEL.fire(player, id, shouldCancel, event.getTimestamp());
    }

    private static void onSendTransaction(@NotNull AethelPlayer player, @NotNull PacketSendEvent event, int id) {
        player.packetStateData.lastServerTransWasValid = false;

        short shortId = (short) id;
        if (id != shortId // we only use the short range
                || id > 0 // we only use negative ids
                || !player.didWeSendThatTrans.remove(shortId)) return;

        player.packetStateData.lastServerTransWasValid = true;
        player.transactionsSent.add(new ShortToLongPair(shortId, System.nanoTime()));
        if (player.getLastTransactionSent() == 0) {
            player.getPlayerClockAtLeast(); // update clock
        }
        player.lastTransactionSent.getAndIncrement();
        SEND_CHANNEL.fire(player, shortId, event.getTimestamp());
    }
}
