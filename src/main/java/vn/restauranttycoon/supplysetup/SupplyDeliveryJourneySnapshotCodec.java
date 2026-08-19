package vn.restauranttycoon.supplysetup;

import java.util.Objects;

public final class SupplyDeliveryJourneySnapshotCodec {
    private SupplyDeliveryJourneySnapshotCodec() {}

    public static SupplyDeliveryJourneySnapshot decode(String payload) {
        return SupplyDeliveryJourneySnapshotParser.parse(Objects.requireNonNull(payload, "payload"));
    }
}
