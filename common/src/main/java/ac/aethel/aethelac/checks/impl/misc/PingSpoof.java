package ac.aethel.aethelac.checks.impl.misc;

import ac.aethel.aethelac.checks.Check;
import ac.aethel.aethelac.checks.CheckData;
import ac.aethel.aethelac.checks.type.PacketReceiveListener;
import ac.aethel.aethelac.checks.type.PacketSendListener;
import ac.aethel.aethelac.player.AethelPlayer;
import ac.aethel.aethelac.utils.anticheat.LogUtil;
import ac.aethel.aethelac.utils.reflection.GeyserUtil;
import ac.grim.grimac.api.config.ConfigManager;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerKeepAlive;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Detects keepalive latency spoofing: clients that delay only their replies
 * to the server's keepalive packets, which is the stream most servers (and
 * tab lists) use to display and trust a player's latency.
 *
 * <p>The check times our own keepalive round trips (the server-sent id is
 * answered by the client with the same id) and pairs every one of them with
 * the transaction round trips Grim already measures for the player inside a
 * centred window around the keepalive sample. Both streams ride the same TCP
 * connection and a vanilla client answers both promptly, so link latency,
 * buffer bloat and client-side stalls move them together - comparing inside
 * a shared window keeps congestion that covers both streams out of the
 * statistic no matter how the streams' overall sampling rates differ. A
 * spoofing module only delays the replies it knows the server times - the
 * keepalive echoes - so every pair runs ahead by roughly the added delay.
 * Judgement requires enough pairs inside the window and a median pair
 * divergence of at least {@code min-divergence-ms}, repeated over
 * {@code min-events} events.</p>
 *
 * <p>One guard rail keeps client-side timing artifacts out: a very low frame
 * rate quantizes the echo timing into frame-sized steps, which can delay a
 * keepalive reply for most of a frame, so no judgement happens unless the
 * keepalive stream's own quartile spread stays under {@code max-spread-ms}
 * over the window. (Quantization of the transaction stream needs no gate -
 * it can only *lower* the paired divergence, never raise it.) A perfectly
 * steady keepalive stream (spread under {@code min-keepalive-spread-ms}) is
 * likewise skipped: identical round trips across a whole window mean the
 * reply timing is locked to a deterministic client clock - typically a
 * frame-rate cap that divides the keepalive interval - which cannot be told
 * apart from a fixed added delay. The gates only widen the undetected band;
 * they never produce a flag.</p>
 *
 * <p>Known holes: a client that delays *both* the keepalive and the
 * transaction replies by the same amount is invisible from the server side
 * (that would need TCP-level round trips, which a plugin cannot read);
 * clients whose own timing spread exceeds {@code max-spread-ms} (very low
 * frame rates, jittery links) and clients on links tighter than
 * {@code min-keepalive-spread-ms} are never judged. Transaction-side-only
 * delay is deliberately not judged: it only makes the player look laggier,
 * and it lowers every paired divergence. Bedrock (Geyser / Floodgate)
 * players are exempt because their stack answers both streams through a
 * proxy with different timing.</p>
 *
 * <p>If the optional Bedrock-detection API fails, this check is skipped for
 * that player and the failure is logged once rather than guessing their
 * client type.</p>
 */
@CheckData(name = "PingSpoof", stableKey = "aethel.misc.ping_spoof",
        description = "Keepalive round trips diverge from surrounding transaction round trips",
        experimental = true)
public class PingSpoof extends Check implements PacketReceiveListener, PacketSendListener {

    private static final AtomicBoolean GEYSER_FAILURE_LOGGED = new AtomicBoolean();
    private static final int KEEPALIVE_RING = 16;
    private static final int TRANSACTION_RING = 64;
    private static final int PENDING_CAPACITY = 4;
    private static final int MIN_PAIR_TRANSACTIONS = 4;
    private static final long EVENT_SPACING_NANOS = 1_000_000_000L;
    private static final long FLAG_COOLDOWN_NANOS = 10_000_000_000L;

    private final double[] keepaliveValues = new double[KEEPALIVE_RING];
    private final long[] keepaliveStamps = new long[KEEPALIVE_RING];
    private int keepaliveIndex;
    private int keepaliveSize;

    private final double[] pairValues = new double[KEEPALIVE_RING];
    private final long[] pairStamps = new long[KEEPALIVE_RING];
    private int pairIndex;
    private int pairSize;

    private final double[] transactionValues = new double[TRANSACTION_RING];
    private final long[] transactionStamps = new long[TRANSACTION_RING];
    private int transactionIndex;
    private int transactionSize;

    private final double[] pendingRtt = new double[PENDING_CAPACITY];
    private final long[] pendingStamp = new long[PENDING_CAPACITY];
    private int pendingHead;
    private int pendingCount;

    private final double[] keepaliveScratch = new double[KEEPALIVE_RING];
    private final double[] pairScratch = new double[KEEPALIVE_RING];
    private final double[] transactionScratch = new double[TRANSACTION_RING];
    private final long[] pairEvents = new long[16];
    private int pairEventHead;
    private int pairEventCount;

    private long pendingKeepaliveId;
    private long pendingKeepaliveNanos;
    private boolean hasPendingKeepalive;
    private int lastTransactionCount;

    private long lastEventNanos;
    private long lastFlagNanos;

    private int windowSeconds;
    private int minKeepaliveSamples;
    private int minTransactionSamples;
    private int minEvents;
    private long counterNanos;
    private long pairHalfNanos;
    private double minDivergenceMs;
    private double maxSpreadMs;
    private double minKeepaliveSpreadMs;
    private Boolean bedrock;

    public PingSpoof(AethelPlayer player) {
        super(player);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        windowSeconds = Math.max(5, config.getIntElse("ping-spoof.window-seconds", 60));
        minKeepaliveSamples = Math.max(2, config.getIntElse("ping-spoof.min-keepalive-samples", 4));
        minTransactionSamples = Math.max(4, config.getIntElse("ping-spoof.min-transaction-samples", 16));
        minEvents = Math.max(1, config.getIntElse("ping-spoof.min-events", 3));
        counterNanos = Math.max(1, config.getIntElse("ping-spoof.counter-seconds", 15)) * 1_000_000_000L;
        pairHalfNanos = Math.max(500L, (long) (config.getDoubleElse("ping-spoof.pair-window-seconds", 3.0D) * 500_000_000.0D));
        minDivergenceMs = Math.max(10.0D, config.getDoubleElse("ping-spoof.min-divergence-ms", 100.0D));
        maxSpreadMs = Math.max(10.0D, config.getDoubleElse("ping-spoof.max-spread-ms", 80.0D));
        minKeepaliveSpreadMs = Math.max(0.0D, config.getDoubleElse("ping-spoof.min-keepalive-spread-ms", 2.0D));
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.KEEP_ALIVE) {
            return;
        }
        pendingKeepaliveId = new WrapperPlayServerKeepAlive(event).getId();
        pendingKeepaliveNanos = System.nanoTime();
        hasPendingKeepalive = true;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.KEEP_ALIVE) {
            long id = new WrapperPlayClientKeepAlive(event).getId();
            if (!hasPendingKeepalive || id != pendingKeepaliveId) {
                return;
            }
            hasPendingKeepalive = false;
            long now = System.nanoTime();
            recordKeepalive((now - pendingKeepaliveNanos) / 1_000_000.0D, now);
        } else if (event.getPacketType() == PacketType.Play.Client.PONG
                || event.getPacketType() == PacketType.Play.Client.WINDOW_CONFIRMATION) {
            recordTransaction();
        }
    }

    private void recordKeepalive(double rttMs, long now) {
        // Absurd values are link stalls, not client behaviour - the spread
        // gate would reject the whole window anyway, so keep them out.
        if (rttMs <= 0.0D || rttMs > 10_000.0D) {
            return;
        }
        push(keepaliveValues, keepaliveStamps, keepaliveIndex, rttMs, now);
        keepaliveIndex = (keepaliveIndex + 1) % KEEPALIVE_RING;
        if (keepaliveSize < KEEPALIVE_RING) {
            keepaliveSize++;
        }

        // Pair this round trip with the transactions around it once enough
        // of them have arrived (completed on the transaction stream, which is
        // the dense one).
        if (pendingCount == PENDING_CAPACITY) {
            pendingHead = (pendingHead + 1) % PENDING_CAPACITY;
            pendingCount--;
        }
        int slot = (pendingHead + pendingCount) % PENDING_CAPACITY;
        pendingRtt[slot] = rttMs;
        pendingStamp[slot] = now;
        pendingCount++;
    }

    private void recordTransaction() {
        // Grim updates the transaction ping while answering our own ping /
        // window-confirmation packets; the counter tells us the value belongs
        // to the response we are looking at instead of a stale earlier one.
        int count = player.lastTransactionReceived.get();
        if (count == lastTransactionCount) {
            return;
        }
        lastTransactionCount = count;
        int pingMs = player.getTransactionPing();
        if (pingMs <= 0) {
            return;
        }

        long now = System.nanoTime();
        push(transactionValues, transactionStamps, transactionIndex, pingMs, now);
        transactionIndex = (transactionIndex + 1) % TRANSACTION_RING;
        if (transactionSize < TRANSACTION_RING) {
            transactionSize++;
        }
        completePendingPairs(now);
        evaluate(now);
    }

    private void completePendingPairs(long now) {
        while (pendingCount > 0 && now - pendingStamp[pendingHead] >= pairHalfNanos) {
            double rttMs = pendingRtt[pendingHead];
            long stamp = pendingStamp[pendingHead];
            pendingHead = (pendingHead + 1) % PENDING_CAPACITY;
            pendingCount--;

            int count = collectBetween(transactionValues, transactionStamps,
                    transactionIndex, transactionSize,
                    stamp - pairHalfNanos, stamp + pairHalfNanos, transactionScratch);
            if (count < MIN_PAIR_TRANSACTIONS) {
                continue;
            }
            double divergence = rttMs - median(transactionScratch, count);
            push(pairValues, pairStamps, pairIndex, divergence, stamp);
            pairIndex = (pairIndex + 1) % KEEPALIVE_RING;
            if (pairSize < KEEPALIVE_RING) {
                pairSize++;
            }
        }
    }

    private void evaluate(long now) {
        if (bedrock()) {
            return;
        }
        long cutoff = now - windowSeconds * 1_000_000_000L;
        int pairCount = collectAlive(pairValues, pairStamps, pairIndex, pairSize, cutoff, pairScratch);
        if (pairCount < minKeepaliveSamples) {
            return;
        }
        int keepaliveCount = collectAlive(keepaliveValues, keepaliveStamps,
                keepaliveIndex, keepaliveSize, cutoff, keepaliveScratch);
        if (keepaliveCount < minKeepaliveSamples) {
            return;
        }
        int transactionCount = collectAlive(transactionValues, transactionStamps,
                transactionIndex, transactionSize, cutoff, transactionScratch);
        if (transactionCount < minTransactionSamples) {
            return;
        }

        double keepaliveSpread = spread(keepaliveScratch, keepaliveCount);
        if (keepaliveSpread > maxSpreadMs || keepaliveSpread < minKeepaliveSpreadMs) {
            return;
        }

        double divergence = median(pairScratch, pairCount);
        if (divergence < minDivergenceMs) {
            return;
        }

        if (now - lastEventNanos < EVENT_SPACING_NANOS) {
            return;
        }
        lastEventNanos = now;
        pairEvents[pairEventHead] = now;
        pairEventHead = (pairEventHead + 1) % pairEvents.length;
        if (pairEventCount < pairEvents.length) {
            pairEventCount++;
        }

        int recent = 0;
        for (int i = 0; i < pairEventCount; i++) {
            if (now - pairEvents[i] <= counterNanos) {
                recent++;
            }
        }
        if (recent >= minEvents && now - lastFlagNanos >= FLAG_COOLDOWN_NANOS) {
            lastFlagNanos = now;
            pairEventCount = 0;
            pairEventHead = 0;
            flag(String.format(Locale.ROOT,
                    "keepalive round trips %.0f ms ahead of surrounding transaction round trips "
                            + "(%d pairs, keepalive spread %.0f ms)",
                    divergence, pairCount, keepaliveSpread));
        }
    }

    private static void push(double[] values, long[] stamps, int index, double value, long stamp) {
        values[index] = value;
        stamps[index] = stamp;
    }

    private static int collectAlive(double[] values, long[] stamps, int index, int size,
                                    long cutoff, double[] out) {
        int count = 0;
        for (int i = 0; i < size; i++) {
            int slot = (index - 1 - i + values.length * 2) % values.length;
            if (stamps[slot] < cutoff) {
                continue;
            }
            out[count++] = values[slot];
        }
        return count;
    }

    private static int collectBetween(double[] values, long[] stamps, int index, int size,
                                      long from, long to, double[] out) {
        int count = 0;
        for (int i = 0; i < size; i++) {
            int slot = (index - 1 - i + values.length * 2) % values.length;
            long stamp = stamps[slot];
            if (stamp < from || stamp > to) {
                continue;
            }
            out[count++] = values[slot];
        }
        return count;
    }

    /** Quartile spread (p75 - p25), sorting the used prefix in place. */
    private static double spread(double[] values, int size) {
        if (size < 4) {
            return 0.0D;
        }
        Arrays.sort(values, 0, size);
        return values[(int) (0.75D * (size - 1))] - values[(int) (0.25D * (size - 1))];
    }

    /** Median, sorting the used prefix in place (idempotent after spread). */
    private static double median(double[] values, int size) {
        Arrays.sort(values, 0, size);
        if (size % 2 == 1) {
            return values[size / 2];
        }
        return (values[size / 2 - 1] + values[size / 2]) / 2.0D;
    }

    private boolean bedrock() {
        if (bedrock == null) {
            try {
                bedrock = GeyserUtil.isBedrockPlayer(player.uuid);
            } catch (RuntimeException | LinkageError exception) {
                bedrock = true;
                if (GEYSER_FAILURE_LOGGED.compareAndSet(false, true)) {
                    LogUtil.warn("Could not determine Bedrock status; PingSpoof will skip affected players",
                            exception);
                }
            }
        }
        return bedrock;
    }
}
