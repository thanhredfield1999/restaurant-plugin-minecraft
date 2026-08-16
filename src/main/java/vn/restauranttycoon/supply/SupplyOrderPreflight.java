package vn.restauranttycoon.supply;

import java.util.List;
import java.util.Objects;

public record SupplyOrderPreflight(Status status, List<String> details) {
    public SupplyOrderPreflight {
        Objects.requireNonNull(status, "status");
        details = List.copyOf(Objects.requireNonNull(details, "details"));
        if ((status == Status.READY) != details.isEmpty()) {
            throw new IllegalArgumentException("Ready preflight must have no details and failures must explain why");
        }
    }

    public enum Status {
        READY,
        NOT_OWNER,
        SETUP_INCOMPLETE
    }
}
