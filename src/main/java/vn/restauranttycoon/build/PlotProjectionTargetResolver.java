package vn.restauranttycoon.build;

import vn.restauranttycoon.worldoperation.WorldOperationClaim;

@FunctionalInterface
public interface PlotProjectionTargetResolver {
    PlotProjectionTarget resolve(WorldOperationClaim claim);
}
