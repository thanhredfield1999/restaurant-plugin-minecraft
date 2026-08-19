package vn.restauranttycoon.build;

import java.util.Locale;
import java.util.Objects;

public record StageBlock(BlockOffset offset, String blockData) {
    public StageBlock {
        Objects.requireNonNull(offset, "offset");
        Objects.requireNonNull(blockData, "blockData");
        blockData = blockData.trim().toLowerCase(Locale.ROOT);
        if (!blockData.matches("[a-z0-9_.-]+:[a-z0-9_./-]+(\\[[a-z0-9_=,]+])?")) {
            throw new IllegalArgumentException("Invalid canonical block data: " + blockData);
        }
    }
}
