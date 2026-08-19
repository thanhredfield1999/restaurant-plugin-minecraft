package vn.restauranttycoon.build;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Server;
import org.bukkit.World;
import vn.restauranttycoon.config.PlotSettings;
import vn.restauranttycoon.worldoperation.WorldOperationClaim;

public final class ConfiguredPlotRegistry implements PlotProjectionTargetResolver {
    private final Server server;
    private final Map<String, PlotSettings> plots;

    public ConfiguredPlotRegistry(Server server, List<PlotSettings> settings) {
        this.server = Objects.requireNonNull(server, "server");
        Objects.requireNonNull(settings, "settings");
        Map<String, PlotSettings> indexed = new LinkedHashMap<>();
        for (PlotSettings plot : settings) {
            if (indexed.put(plot.plotId(), plot) != null) {
                throw new IllegalArgumentException("Duplicate configured plot: " + plot.plotId());
            }
            if (server.getWorld(plot.worldName()) == null) {
                throw new IllegalArgumentException(
                        "Configured world is not loaded for plot " + plot.plotId() + ": " + plot.worldName());
            }
        }
        this.plots = Map.copyOf(indexed);
    }

    @Override
    public PlotProjectionTarget resolve(WorldOperationClaim claim) {
        PlotSettings plot = Optional.ofNullable(plots.get(claim.plotId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "World operation references an unconfigured plot: " + claim.plotId()));
        World world = Optional.ofNullable(server.getWorld(plot.worldName()))
                .orElseThrow(() -> new IllegalStateException(
                        "Configured world unloaded during projection: " + plot.worldName()));
        return new PlotProjectionTarget(
                plot.plotId(),
                claim.requiredFenceToken(),
                world,
                plot.originX(),
                plot.originY(),
                plot.originZ());
    }
}
