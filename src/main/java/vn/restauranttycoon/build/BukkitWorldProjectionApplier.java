package vn.restauranttycoon.build;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;
import vn.restauranttycoon.worldoperation.StalePlotFenceException;
import vn.restauranttycoon.worldoperation.WorldOperationClaim;
import vn.restauranttycoon.worldoperation.WorldOperationFenceVerifier;
import vn.restauranttycoon.worldoperation.WorldProjectionApplier;

public final class BukkitWorldProjectionApplier implements WorldProjectionApplier {
    private static final String AIR = "minecraft:air";

    private final JavaPlugin plugin;
    private final StageManifest manifest;
    private final PlotProjectionTargetResolver targets;
    private final WorldOperationFenceVerifier fenceVerifier;
    private final int blocksPerTick;

    public BukkitWorldProjectionApplier(
            JavaPlugin plugin,
            StageManifest manifest,
            PlotProjectionTargetResolver targets,
            WorldOperationFenceVerifier fenceVerifier,
            int blocksPerTick
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.targets = Objects.requireNonNull(targets, "targets");
        this.fenceVerifier = Objects.requireNonNull(fenceVerifier, "fenceVerifier");
        if (blocksPerTick < 1 || blocksPerTick > 10_000) {
            throw new IllegalArgumentException("blocksPerTick must be between 1 and 10000");
        }
        this.blocksPerTick = blocksPerTick;
    }

    @Override
    public CompletableFuture<Void> applyAndValidate(WorldOperationClaim claim) {
        Objects.requireNonNull(claim, "claim");
        AuthoredStage stage = manifest.find(claim.plotId(), claim.targetStageRevision())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Missing authored stage " + claim.plotId()
                                + " revision " + claim.targetStageRevision()));
        CompletableFuture<Void> result = new CompletableFuture<>();
        fenceVerifier.verify(claim).whenComplete((ignored, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
                return;
            }
            try {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        runBatch(claim, snapshot(stage), 0, false, result);
                    } catch (RuntimeException exception) {
                        result.completeExceptionally(exception);
                    }
                });
            } catch (RuntimeException exception) {
                result.completeExceptionally(exception);
            }
        });
        return result;
    }

    private List<ExpectedBlock> snapshot(AuthoredStage stage) {
        Map<BlockOffset, String> authored = stage.blockDataByOffset();
        List<ExpectedBlock> snapshot = new ArrayList<>();
        for (int y = 0; y < stage.volume().sizeY(); y++) {
            for (int z = 0; z < stage.volume().sizeZ(); z++) {
                for (int x = 0; x < stage.volume().sizeX(); x++) {
                    BlockOffset offset = new BlockOffset(x, y, z);
                    String encoded = authored.getOrDefault(offset, AIR);
                    snapshot.add(new ExpectedBlock(offset, Bukkit.createBlockData(encoded)));
                }
            }
        }
        return List.copyOf(snapshot);
    }

    private void scheduleBatch(
            WorldOperationClaim claim,
            List<ExpectedBlock> snapshot,
            int start,
            boolean validating,
            CompletableFuture<Void> result
    ) {
        if (!plugin.isEnabled()) {
            result.completeExceptionally(new IllegalStateException("Plugin disabled during projection"));
            return;
        }
        fenceVerifier.verify(claim).whenComplete((ignored, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
                return;
            }
            try {
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> runBatch(claim, snapshot, start, validating, result));
            } catch (RuntimeException exception) {
                result.completeExceptionally(exception);
            }
        });
    }

    private void runBatch(
            WorldOperationClaim claim,
            List<ExpectedBlock> snapshot,
            int start,
            boolean validating,
            CompletableFuture<Void> result
    ) {
        try {
            PlotProjectionTarget target = currentTarget(claim);
            int end = Math.min(start + blocksPerTick, snapshot.size());
            for (int index = start; index < end; index++) {
                ExpectedBlock expected = snapshot.get(index);
                Block block = target.world().getBlockAt(
                        target.originX() + expected.offset().x(),
                        target.originY() + expected.offset().y(),
                        target.originZ() + expected.offset().z());
                if (validating) {
                    if (!block.getBlockData().equals(expected.data())) {
                        throw new IllegalStateException(
                                "Projection validation failed at " + expected.offset());
                    }
                } else if (!block.getBlockData().equals(expected.data())) {
                    block.setBlockData(expected.data(), false);
                }
            }
            if (end < snapshot.size()) {
                scheduleBatch(claim, snapshot, end, validating, result);
            } else if (!validating) {
                scheduleBatch(claim, snapshot, 0, true, result);
            } else {
                result.complete(null);
            }
        } catch (RuntimeException exception) {
            result.completeExceptionally(exception);
        }
    }

    private PlotProjectionTarget currentTarget(WorldOperationClaim claim) {
        PlotProjectionTarget target = targets.resolve(claim);
        if (target.fenceToken() != claim.requiredFenceToken()) {
            throw new StalePlotFenceException(claim);
        }
        return target;
    }

    private record ExpectedBlock(BlockOffset offset, BlockData data) {
    }
}
