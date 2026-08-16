package vn.restauranttycoon.supplysetup;

public record SupplySetupPosition(
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
    public SupplySetupPosition {
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("worldName must not be blank");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("setup position must contain only finite coordinates");
        }
        if (pitch < -90.0f || pitch > 90.0f) {
            throw new IllegalArgumentException("pitch must be between -90 and 90 degrees");
        }
    }
}
