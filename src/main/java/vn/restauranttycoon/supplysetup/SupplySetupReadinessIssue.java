package vn.restauranttycoon.supplysetup;

import java.util.Objects;
import java.util.Optional;

public record SupplySetupReadinessIssue(
        Type type,
        Optional<SupplySetupPointType> pointType
) {
    public SupplySetupReadinessIssue {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(pointType, "pointType");
        boolean identifiesPoint = type == Type.MISSING_POINT || type == Type.DUPLICATE_POINT;
        if (identifiesPoint != pointType.isPresent()) {
            throw new IllegalArgumentException(
                    "missing-point and duplicate-point issues must identify a point type");
        }
    }

    public static SupplySetupReadinessIssue missing(SupplySetupPointType pointType) {
        return new SupplySetupReadinessIssue(
                Type.MISSING_POINT,
                Optional.of(Objects.requireNonNull(pointType, "pointType")));
    }

    public static SupplySetupReadinessIssue duplicate(SupplySetupPointType pointType) {
        return new SupplySetupReadinessIssue(
                Type.DUPLICATE_POINT,
                Optional.of(Objects.requireNonNull(pointType, "pointType")));
    }

    public static SupplySetupReadinessIssue foreignOwner() {
        return new SupplySetupReadinessIssue(Type.FOREIGN_OWNER, Optional.empty());
    }

    public static SupplySetupReadinessIssue multipleWorlds() {
        return new SupplySetupReadinessIssue(Type.MULTIPLE_WORLDS, Optional.empty());
    }

    public enum Type {
        MISSING_POINT,
        DUPLICATE_POINT,
        FOREIGN_OWNER,
        MULTIPLE_WORLDS
    }
}
