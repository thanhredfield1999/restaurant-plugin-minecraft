package vn.restauranttycoon.build;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class StageManifest {
    private final Map<StageKey, AuthoredStage> stages;

    public StageManifest(Collection<AuthoredStage> stages) {
        Objects.requireNonNull(stages, "stages");
        Map<StageKey, AuthoredStage> indexed = new LinkedHashMap<>();
        for (AuthoredStage stage : stages) {
            Objects.requireNonNull(stage, "stages contains null");
            StageKey key = new StageKey(stage.plotId(), stage.stageRevision());
            if (indexed.put(key, stage) != null) {
                throw new IllegalArgumentException(
                        "Duplicate authored stage: " + stage.plotId() + " revision " + stage.stageRevision());
            }
        }
        this.stages = Map.copyOf(indexed);
    }

    public Optional<AuthoredStage> find(String plotId, long stageRevision) {
        return Optional.ofNullable(stages.get(new StageKey(plotId, stageRevision)));
    }

    private record StageKey(String plotId, long stageRevision) {
    }
}
