package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.player.AethelPlayer;

@CheckData(name = "TransactionOrder", stableKey = "aethel.ping.invalid_transaction_order", description = "Sent transaction or ping responses in an invalid order")
public class TransactionOrder extends Check {
    public TransactionOrder(AethelPlayer player) {
        super(player);
    }
}
