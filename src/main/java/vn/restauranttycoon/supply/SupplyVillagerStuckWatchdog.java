package vn.restauranttycoon.supply;

public final class SupplyVillagerStuckWatchdog {
    private final int maxTicksWithoutProgress;
    private final double minimumProgress;
    private int ticksWithoutProgress;
    private double bestDistance = Double.POSITIVE_INFINITY;

    public SupplyVillagerStuckWatchdog(int maxTicksWithoutProgress, double minimumProgress) {
        if (maxTicksWithoutProgress < 1) {
            throw new IllegalArgumentException("maxTicksWithoutProgress must be positive");
        }
        if (!Double.isFinite(minimumProgress) || minimumProgress <= 0.0) {
            throw new IllegalArgumentException("minimumProgress must be finite and positive");
        }
        this.maxTicksWithoutProgress = maxTicksWithoutProgress;
        this.minimumProgress = minimumProgress;
    }

    public boolean observe(double distance) {
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("distance must be finite and non-negative");
        }
        if (bestDistance - distance >= minimumProgress) {
            bestDistance = distance;
            ticksWithoutProgress = 0;
            return false;
        }
        ticksWithoutProgress++;
        return ticksWithoutProgress >= maxTicksWithoutProgress;
    }

    public int ticksWithoutProgress() {
        return ticksWithoutProgress;
    }

    public void reset() {
        ticksWithoutProgress = 0;
        bestDistance = Double.POSITIVE_INFINITY;
    }
}
