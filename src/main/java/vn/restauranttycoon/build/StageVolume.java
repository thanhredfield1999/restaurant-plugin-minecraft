package vn.restauranttycoon.build;

public record StageVolume(int sizeX, int sizeY, int sizeZ) {
    public static final int MAX_BLOCKS = 100_000;

    public StageVolume {
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1) {
            throw new IllegalArgumentException("Stage volume dimensions must be positive");
        }
        long blockCount = (long) sizeX * sizeY * sizeZ;
        if (blockCount > MAX_BLOCKS) {
            throw new IllegalArgumentException(
                    "Stage volume exceeds the " + MAX_BLOCKS + " block limit");
        }
    }

    public boolean contains(BlockOffset offset) {
        return offset.x() >= 0 && offset.x() < sizeX
                && offset.y() >= 0 && offset.y() < sizeY
                && offset.z() >= 0 && offset.z() < sizeZ;
    }
}
