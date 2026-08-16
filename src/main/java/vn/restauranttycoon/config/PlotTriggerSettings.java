package vn.restauranttycoon.config;

public record PlotTriggerSettings(
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {
    public PlotTriggerSettings {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException(
                    "plot trigger min coordinates must not exceed max coordinates");
        }
    }

    public boolean contains(String worldName, PlotSettings plot, double x, double y, double z) {
        return plot.worldName().equals(worldName)
                && x >= minX && x < maxX + 1.0
                && y >= minY && y < maxY + 1.0
                && z >= minZ && z < maxZ + 1.0;
    }
}
