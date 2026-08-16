package vn.restauranttycoon.supplysetup;

import java.util.List;
import java.util.Objects;

public record SupplySetupReadiness(List<SupplySetupReadinessIssue> issues) {
    public SupplySetupReadiness {
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    }

    public boolean structurallyComplete() {
        return issues.isEmpty();
    }
}
