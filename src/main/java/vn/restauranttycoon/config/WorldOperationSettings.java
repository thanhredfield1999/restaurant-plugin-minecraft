package vn.restauranttycoon.config;

public record WorldOperationSettings(
        String instanceId,
        int pollTicks,
        int leaseSeconds,
        int blocksPerTick
) {
    public WorldOperationSettings {
        if (instanceId == null || !instanceId.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException(
                    "world-operations.instance-id must be a safe identifier of at most 64 characters");
        }
        if (pollTicks < 1 || pollTicks > 1200) {
            throw new IllegalArgumentException("world-operations.poll-ticks must be between 1 and 1200");
        }
        if (leaseSeconds < 1 || leaseSeconds > 300) {
            throw new IllegalArgumentException("world-operations.lease-seconds must be between 1 and 300");
        }
        if (blocksPerTick < 1 || blocksPerTick > 10_000) {
            throw new IllegalArgumentException("world-operations.blocks-per-tick must be between 1 and 10000");
        }
    }
}
