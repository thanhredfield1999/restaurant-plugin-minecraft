package vn.restauranttycoon.supply;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded in-memory session index; no world/entity scan. */
public final class SupplyRuntimeSessionRegistry {
    private final int maxSessions;
    private final Map<UUID, SupplyRuntimeSession> sessions = new HashMap<>();

    public SupplyRuntimeSessionRegistry(int maxSessions) {
        if (maxSessions < 1) throw new IllegalArgumentException("maxSessions must be positive");
        this.maxSessions = maxSessions;
    }

    public boolean open(SupplyRuntimeClaim claim, SupplyRuntimeProjection projection, UUID entityId) {
        SupplyRuntimeSession session = new SupplyRuntimeSession(claim, projection, entityId);
        SupplyRuntimeSession existing = sessions.get(claim.shipmentId());
        if (existing != null) return existing.equals(session);
        if (sessions.size() >= maxSessions) return false;
        sessions.put(claim.shipmentId(), session);
        return true;
    }

    public boolean replaceClaim(UUID shipmentId, SupplyRuntimeClaim claim) {
        SupplyRuntimeSession existing = sessions.get(Objects.requireNonNull(shipmentId, "shipmentId"));
        if (existing == null) return false;
        sessions.put(shipmentId, new SupplyRuntimeSession(claim, existing.projection(), existing.entityId()));
        return true;
    }

    public Optional<SupplyRuntimeSession> get(UUID shipmentId) {
        return Optional.ofNullable(sessions.get(Objects.requireNonNull(shipmentId, "shipmentId")));
    }

    public java.util.List<SupplyRuntimeSession> snapshot() {
        return java.util.List.copyOf(sessions.values());
    }

    public void remove(UUID shipmentId) {
        sessions.remove(Objects.requireNonNull(shipmentId, "shipmentId"));
    }

    public int size() {
        return sessions.size();
    }
}
