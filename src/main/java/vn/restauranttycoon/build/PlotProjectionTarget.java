package vn.restauranttycoon.build;

import java.util.Objects;
import org.bukkit.World;

public record PlotProjectionTarget(
        String plotId,
        long fenceToken,
        World world,
        int originX,
        int originY,
        int originZ
) {
    public PlotProjectionTarget {
        Objects.requireNonNull(plotId, "plotId");
        Objects.requireNonNull(world, "world");
        if (fenceToken < 1) {
            throw new IllegalArgumentException("fenceToken must be positive");
        }
    }
}
