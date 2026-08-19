package vn.restauranttycoon.supply;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SupplyVillagerRegistry {
    private static final int MAX_CANDIDATES = 4;
    private final Map<UUID, List<UUID>> entitiesByShipment = new HashMap<>();

    public boolean register(UUID shipmentId, UUID entityId) {
        if (shipmentId == null || entityId == null) throw new NullPointerException();
        List<UUID> entities = entitiesByShipment.computeIfAbsent(shipmentId, ignored -> new ArrayList<>());
        if (entities.contains(entityId)) return true;
        if (entities.size() >= MAX_CANDIDATES) return false;
        entities.add(entityId);
        return true;
    }

    public void unregister(UUID shipmentId, UUID entityId) {
        List<UUID> entities = entitiesByShipment.get(shipmentId);
        if (entities == null) return;
        entities.remove(entityId);
        if (entities.isEmpty()) entitiesByShipment.remove(shipmentId);
    }

    public List<UUID> candidates(UUID shipmentId) {
        List<UUID> entities = entitiesByShipment.get(shipmentId);
        return entities == null ? List.of() : List.copyOf(entities);
    }

    public void clear() {
        entitiesByShipment.clear();
    }
}
