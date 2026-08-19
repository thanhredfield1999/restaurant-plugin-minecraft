package vn.restauranttycoon.supply;

public record SupplyVillagerMovementDecision(
        boolean arrived,
        double velocityX,
        double velocityY,
        double velocityZ) {

    public SupplyVillagerMovementDecision {
        if (!Double.isFinite(velocityX) || !Double.isFinite(velocityY)
                || !Double.isFinite(velocityZ)) {
            throw new IllegalArgumentException("velocity must be finite");
        }
    }

    public static SupplyVillagerMovementDecision toward(
            double currentX, double currentY, double currentZ,
            double targetX, double targetY, double targetZ,
            double arrivalRadius, double maxSpeed) {
        requireFinite(currentX, currentY, currentZ, targetX, targetY, targetZ);
        if (!Double.isFinite(arrivalRadius) || arrivalRadius < 0.0) {
            throw new IllegalArgumentException("arrivalRadius must be finite and non-negative");
        }
        if (!Double.isFinite(maxSpeed) || maxSpeed <= 0.0) {
            throw new IllegalArgumentException("maxSpeed must be finite and positive");
        }
        double dx = targetX - currentX;
        double dy = targetY - currentY;
        double dz = targetZ - currentZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= arrivalRadius) {
            return new SupplyVillagerMovementDecision(true, 0.0, 0.0, 0.0);
        }
        double scale = Math.min(maxSpeed, distance) / distance;
        return new SupplyVillagerMovementDecision(false, dx * scale, dy * scale, dz * scale);
    }

    private static void requireFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("coordinate must be finite");
        }
    }
}
