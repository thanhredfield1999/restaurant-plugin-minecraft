package vn.restauranttycoon.supply;

import java.util.Objects;
import vn.restauranttycoon.supplysetup.SupplySetupPosition;

public final class SupplyRuntimeMovementTargetResolver {
    private SupplyRuntimeMovementTargetResolver() {
    }

    public static Target resolve(SupplyRuntimeProjection projection, boolean entityAlreadyPresent) {
        Objects.requireNonNull(projection, "projection");
        if (!entityAlreadyPresent) {
            return new Target(TargetKind.SPAWN, SupplyRuntimeCheckpointResolver.resolve(
                    projection.journey(), projection.checkpointStage(), projection.checkpointIndex()));
        }
        if ("DELIVERY_DESPAWN".equals(projection.checkpointStage())) {
            return new Target(TargetKind.MOVE, SupplyRuntimeCheckpointResolver.resolve(
                    projection.journey(), projection.checkpointStage(), projection.checkpointIndex()));
        }
        SupplyRuntimeCheckpoint next = SupplyRuntimeNextCheckpointResolver.resolve(
                projection.journey(), projection.checkpointStage(), projection.checkpointIndex())
                .orElseThrow(() -> new IllegalStateException("terminal checkpoint has no movement target"));
        return new Target(TargetKind.MOVE, SupplyRuntimeCheckpointResolver.resolve(
                projection.journey(), next.stage(), next.index()));
    }

    public enum TargetKind {
        SPAWN,
        MOVE,
        FINALIZE
    }

    public record Target(TargetKind kind, SupplySetupPosition position) {
        public Target {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(position, "position");
        }
    }
}
