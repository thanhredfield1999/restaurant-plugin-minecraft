package vn.restauranttycoon.plot;

import java.util.Optional;
import java.util.UUID;

public record PlotAssignment(
        String plotId,
        Optional<UUID> accountId,
        String serverId,
        long fenceToken,
        long assignmentRevision
) {
}
