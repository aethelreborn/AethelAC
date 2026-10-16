package ac.aethel.aethelac.manager;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.manager.init.Initable;
import ac.aethel.aethelac.manager.init.load.LoadableInitable;
import ac.aethel.aethelac.manager.init.load.PacketEventsInit;
import ac.aethel.aethelac.manager.init.start.*;
import ac.aethel.aethelac.manager.init.stop.StoppableInitable;
import ac.aethel.aethelac.manager.init.stop.TerminatePacketEvents;
import ac.aethel.aethelac.utils.anticheat.LogUtil;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.google.common.collect.ImmutableList;
import lombok.Getter;

import java.util.ArrayList;

public class InitManager {

    private final ImmutableList<LoadableInitable> initializersOnLoad;
    private final ImmutableList<StartableInitable> initializersOnStart;
    private final ImmutableList<StoppableInitable> initializersOnStop;

    @Getter
    private boolean loaded = false;
    @Getter
    private boolean started = false;
    @Getter
    private boolean stopped = false;

    public InitManager(PacketEventsAPI<?> packetEventsAPI, Initable... platformSpecificInitables) {
        ArrayList<LoadableInitable> extraLoadableInitables = new ArrayList<>();
        ArrayList<StartableInitable> extraStartableInitables = new ArrayList<>();
        ArrayList<StoppableInitable> extraStoppableInitables = new ArrayList<>();
        for (Initable initable : platformSpecificInitables) {
            if (initable instanceof LoadableInitable) extraLoadableInitables.add((LoadableInitable) initable);
            if (initable instanceof StartableInitable) extraStartableInitables.add((StartableInitable) initable);
            if (initable instanceof StoppableInitable) extraStoppableInitables.add((StoppableInitable) initable);
        }

        initializersOnLoad = ImmutableList.<LoadableInitable>builder()
                .add(new PacketEventsInit(packetEventsAPI))
                .add(() -> AethelAPI.INSTANCE.getExternalAPI().load())
                .addAll(extraLoadableInitables)
                .build();

        initializersOnStart = ImmutableList.<StartableInitable>builder()
                .add(AethelAPI.INSTANCE.getExternalAPI())
                .add(new PacketManager())
                .add(new ViaBackwardsManager())
                .add(new TickRunner())
                .add(new CommandRegister(AethelAPI.INSTANCE.getCommandService()))
                .add(new UpdateChecker())
                .add(new PacketLimiter())
                .add(AethelAPI.INSTANCE.getAlertManager())
                .add(AethelAPI.INSTANCE.getDiscordManager())
                .add(AethelAPI.INSTANCE.getSpectateManager())
                .add(AethelAPI.INSTANCE.getDataStoreLifecycle())
                .add(new JavaVersion())
                .add(new ViaVersion())
                .add(new TAB())
                .addAll(extraStartableInitables)
                .build();

        initializersOnStop = ImmutableList.<StoppableInitable>builder()
                .add(new TerminatePacketEvents())
                .add(AethelAPI.INSTANCE.getDataStoreLifecycle())
                .addAll(extraStoppableInitables)
                .build();
    }

    public void load() {
        for (LoadableInitable initable : initializersOnLoad) {
            try {
                initable.load();
            } catch (Exception e) {
                LogUtil.error("Failed to load " + initable.getClass().getSimpleName(), e);
            }
        }
        loaded = true;
    }

    public void start() {
        for (StartableInitable initable : initializersOnStart) {
            try {
                initable.start();
            } catch (Exception e) {
                LogUtil.error("Failed to start " + initable.getClass().getSimpleName(), e);
            }
        }
        started = true;
    }

    public void stop() {
        for (StoppableInitable initable : initializersOnStop) {
            try {
                initable.stop();
            } catch (Exception e) {
                LogUtil.error("Failed to stop " + initable.getClass().getSimpleName(), e);
            }
        }
        stopped = true;
    }
}
