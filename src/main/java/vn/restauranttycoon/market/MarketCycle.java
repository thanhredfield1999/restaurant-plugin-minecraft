package vn.restauranttycoon.market;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MarketCycle(UUID cycleId, long cycleNumber, Instant startsAt,
                          Instant endsAt, String state) {
    public MarketCycle {
        Objects.requireNonNull(cycleId, "cycleId");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        if (endsAt.compareTo(startsAt) <= 0 || cycleNumber < 0 || !"OPEN".equals(state)) {
            throw new IllegalArgumentException("Invalid open market cycle");
        }
    }
}
