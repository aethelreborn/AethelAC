package ac.aethel.aethelac.platform.fabric.initables;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.manager.init.start.StartableInitable;
import ac.aethel.aethelac.manager.init.stop.StoppableInitable;
import ac.aethel.aethelac.platform.fabric.utils.metrics.MetricsFabric;
import ac.aethel.aethelac.utils.anticheat.Constants;

public class FabricBStats implements StartableInitable, StoppableInitable {

    private MetricsFabric metricsFabric;

    @Override
    public void start() {
        if (Constants.BSTATS_PLUGIN_ID <= 0) return;
        try {
            metricsFabric = new MetricsFabric(AethelAPI.INSTANCE.getGrimPlugin(), Constants.BSTATS_PLUGIN_ID);
        } catch (Exception ignored) {}
    }

    @Override
    public void stop() {
        if (metricsFabric != null)
            metricsFabric.shutdown();
    }
}
