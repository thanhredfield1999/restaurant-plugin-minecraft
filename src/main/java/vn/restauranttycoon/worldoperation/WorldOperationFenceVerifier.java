package vn.restauranttycoon.worldoperation;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface WorldOperationFenceVerifier {
    CompletableFuture<Void> verify(WorldOperationClaim claim);
}
