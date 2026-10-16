package ac.aethel.aethelac.manager.init.stop;

import ac.aethel.aethelac.manager.init.Initable;

public interface StoppableInitable extends Initable {
    void stop();
}
