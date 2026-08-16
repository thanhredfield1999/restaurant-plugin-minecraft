package vn.restauranttycoon.dish;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class DishItemPdcCodecTest {
    @Test
    void roundTripsTokenThroughItemMetaPersistentData() {
        NamespacedKey key = new NamespacedKey("restauranttycoon", "dish_entitlement");
        DishItemPdcCodec codec = new DishItemPdcCodec(key);
        ItemMeta meta = itemMeta(new HashMap<>());
        DishItemToken token = new DishItemToken(
                UUID.randomUUID(), UUID.randomUUID(), 7, "grilled_cod", 3);

        codec.write(meta, token);

        assertEquals(DishItemPdcCodec.ReadResult.valid(token), codec.read(meta));
    }

    @Test
    void distinguishesAbsentMalformedAndWrongPrimitiveType() {
        NamespacedKey key = new NamespacedKey("restauranttycoon", "dish_entitlement");
        DishItemPdcCodec codec = new DishItemPdcCodec(key);
        Map<NamespacedKey, Object> values = new HashMap<>();
        ItemMeta meta = itemMeta(values);

        assertEquals(DishItemPdcCodec.ReadResult.absent(), codec.read(meta));

        values.put(key, "not-a-token");
        assertEquals(DishItemPdcCodec.ReadResult.invalid(), codec.read(meta));

        values.put(key, 1);
        assertEquals(DishItemPdcCodec.ReadResult.invalid(), codec.read(meta));
    }

    private static ItemMeta itemMeta(Map<NamespacedKey, Object> values) {
        PersistentDataContainer container = (PersistentDataContainer) Proxy.newProxyInstance(
                DishItemPdcCodecTest.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "set" -> {
                        values.put((NamespacedKey) arguments[0], arguments[2]);
                        yield null;
                    }
                    case "has" -> arguments.length == 1
                            ? values.containsKey((NamespacedKey) arguments[0])
                            : values.containsKey((NamespacedKey) arguments[0])
                                    && arguments[1] == PersistentDataType.STRING
                                    && values.get((NamespacedKey) arguments[0]) instanceof String;
                    case "get" -> values.get((NamespacedKey) arguments[0]);
                    case "remove" -> {
                        values.remove((NamespacedKey) arguments[0]);
                        yield null;
                    }
                    case "isEmpty" -> values.isEmpty();
                    case "getKeys" -> values.keySet();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (ItemMeta) Proxy.newProxyInstance(
                DishItemPdcCodecTest.class.getClassLoader(),
                new Class<?>[]{ItemMeta.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getPersistentDataContainer")) {
                        return container;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
