package vn.restauranttycoon.supply;

import java.sql.SQLException;
import java.util.Objects;

/** Gọi transition CAS durable; caller chịu trách nhiệm chọn database executor. */
public final class SupplyRuntimeRepositoryTransitionExecutor implements SupplyRuntimeTransitionExecutor {
    private final SupplyFulfillmentRepository repository;

    public SupplyRuntimeRepositoryTransitionExecutor(SupplyFulfillmentRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public SupplyRuntimeTransitionResult execute(SupplyRuntimeTransitionRequest request)
            throws SQLException {
        Objects.requireNonNull(request, "request");
        SupplyRuntimeClaim claim = request.claim();
        SupplyRuntimeTransitionCommand command = request.command();
        return repository.transitionCheckpoint(
                claim,
                command.expectedRevision(),
                command.expectedStage(),
                command.expectedIndex(),
                command.nextStage(),
                command.nextIndex(),
                command.operationId());
    }
}
