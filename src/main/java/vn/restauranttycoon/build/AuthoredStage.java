package vn.restauranttycoon.build;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AuthoredStage(
        String plotId,
        long stageRevision,
        StageVolume volume,
        List<StageBlock> blocks
) {
    public AuthoredStage {
        if (plotId == null || !plotId.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("plotId must be a safe identifier of at most 64 characters");
        }
        if (stageRevision < 1) {
            throw new IllegalArgumentException("stageRevision must be positive");
        }
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(blocks, "blocks");
        Map<BlockOffset, StageBlock> unique = new LinkedHashMap<>();
        for (StageBlock block : blocks) {
            Objects.requireNonNull(block, "blocks contains null");
            if (!volume.contains(block.offset())) {
                throw new IllegalArgumentException("Block lies outside stage volume: " + block.offset());
            }
            if (unique.put(block.offset(), block) != null) {
                throw new IllegalArgumentException("Duplicate block offset: " + block.offset());
            }
        }
        blocks = List.copyOf(unique.values());
    }

    public Map<BlockOffset, String> blockDataByOffset() {
        Map<BlockOffset, String> result = new LinkedHashMap<>();
        blocks.forEach(block -> result.put(block.offset(), block.blockData()));
        return Map.copyOf(result);
    }
}
