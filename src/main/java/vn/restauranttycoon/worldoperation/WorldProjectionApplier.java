package vn.restauranttycoon.worldoperation;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface WorldProjectionApplier {
    CompletableFuture<Void> applyAndValidate(WorldOperationClaim claim);
}
