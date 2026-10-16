package ac.aethel.aethelac.checks.impl.breaking;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.BlockBreakListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.update.BlockBreak;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import org.jetbrains.annotations.NotNull;

/**
 * Detects finish/abort dig actions that do not correspond to any started dig.
 *
 * <p>Vanilla clients always send {@code START_DIGGING} for a position before
 * finishing or aborting that same position, and a finished dig consumes its
 * start. Anti-cheat aware velocity modules spam phantom finish actions (for
 * example a {@code STOP_DESTROY_BLOCK} at the player's own position) to
 * confuse knockback handling; those never have a matching start.</p>
 */
@CheckData(name = "PhantomBreak", stableKey = "aethel.breaking.phantom_dig", description = "Finished or aborted a dig that was never started", experimental = true)
public class PhantomBreak extends Check implements BlockBreakListener {

    private long lastStartNanos;
    private Vector3i lastStartPos;
    private long lastFlagNanos;

    public PhantomBreak(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        @NotNull DiggingAction action = blockBreak.action;
        @NotNull Vector3i position = blockBreak.position;

        if (action == DiggingAction.START_DIGGING) {
            lastStartNanos = System.nanoTime();
            lastStartPos = position;
            return;
        }
        if (action != DiggingAction.FINISHED_DIGGING && action != DiggingAction.CANCELLED_DIGGING) {
            return;
        }

        if (lastStartNanos != 0L && lastStartPos != null && lastStartPos.equals(position)) {
            // A finished or aborted dig consumes its start; vanilla cannot
            // legitimately finish or abort the same position twice in a row.
            lastStartNanos = 0L;
            lastStartPos = null;
            return;
        }

        long now = System.nanoTime();
        if (now - lastFlagNanos >= 1_000_000_000L) {
            flag(action == DiggingAction.FINISHED_DIGGING
                    ? "finished a dig that was never started"
                    : "aborted a dig that was never started");
            lastFlagNanos = now;
        }
    }
}
