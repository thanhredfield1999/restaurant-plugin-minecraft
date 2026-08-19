package vn.restauranttycoon.supply;

@FunctionalInterface
public interface SupplyRuntimeMainThreadExecutor {
    void execute(Runnable task);
}
