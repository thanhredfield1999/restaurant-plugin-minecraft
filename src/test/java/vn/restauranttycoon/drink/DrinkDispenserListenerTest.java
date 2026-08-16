package vn.restauranttycoon.drink;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.config.DrinkDispenserSettings;
import vn.restauranttycoon.config.DrinkStationSettings;
import vn.restauranttycoon.config.DrinkType;

class DrinkDispenserListenerTest {
    private static final String STATION_ID = "water_1";

    @Test
    void invalidatesActiveFillWhenConfiguredBlockIsReplaced() {
        DrinkDispenserController controller = new DrinkDispenserController(60);
        DrinkDispenserListener listener = listener(controller);
        controller.interact(STATION_ID, UUID.randomUUID(), false, false, 100);

        listener.invalidateStation("world", 10, 64, -5);

        assertFalse(controller.hasActiveFill(STATION_ID));
    }

    @Test
    void ignoresReplacementOutsideConfiguredStations() {
        DrinkDispenserController controller = new DrinkDispenserController(60);
        DrinkDispenserListener listener = listener(controller);
        controller.interact(STATION_ID, UUID.randomUUID(), false, false, 100);

        listener.invalidateStation("world", 11, 64, -5);

        assertTrue(controller.hasActiveFill(STATION_ID));
    }

    @Test
    void defersPhysicsValidationUntilTheBlockMutationHasCompleted() {
        DrinkDispenserController controller = new DrinkDispenserController(60);
        AtomicReference<Runnable> deferred = new AtomicReference<>();
        DrinkDispenserListener listener = listener(controller, deferred::set);
        controller.interact(STATION_ID, UUID.randomUUID(), false, false, 100);

        listener.validateStationAfterPhysics("world", 10, 64, -5, () -> false);

        assertTrue(controller.hasActiveFill(STATION_ID));
        deferred.get().run();
        assertFalse(controller.hasActiveFill(STATION_ID));
    }

    @Test
    void preservesFillWhenDeferredPhysicsValidationStillSeesTheLever() {
        DrinkDispenserController controller = new DrinkDispenserController(60);
        AtomicReference<Runnable> deferred = new AtomicReference<>();
        DrinkDispenserListener listener = listener(controller, deferred::set);
        controller.interact(STATION_ID, UUID.randomUUID(), false, false, 100);

        listener.validateStationAfterPhysics("world", 10, 64, -5, () -> true);
        deferred.get().run();

        assertTrue(controller.hasActiveFill(STATION_ID));
    }

    @Test
    void validatesAdjacentStationWhenPhysicsEventIsRaisedForItsSupportBlock() {
        DrinkDispenserController controller = new DrinkDispenserController(60);
        AtomicReference<Runnable> deferred = new AtomicReference<>();
        DrinkDispenserListener listener = listener(controller, deferred::set);
        controller.interact(STATION_ID, UUID.randomUUID(), false, false, 100);

        listener.validateStationsAroundPhysicsRoot(
                "world",
                9,
                64,
                -5,
                (worldName, x, y, z) -> false);

        assertTrue(controller.hasActiveFill(STATION_ID));
        deferred.get().run();
        assertFalse(controller.hasActiveFill(STATION_ID));
    }

    private DrinkDispenserListener listener(DrinkDispenserController controller) {
        return listener(controller, Runnable::run);
    }

    private DrinkDispenserListener listener(
            DrinkDispenserController controller,
            java.util.function.Consumer<Runnable> deferredExecutor
    ) {
        DrinkStationSettings station = new DrinkStationSettings(
                STATION_ID,
                "world",
                10,
                64,
                -5,
                DrinkType.WATER);
        return new DrinkDispenserListener(
                new DrinkDispenserSettings(3, List.of(station)),
                controller,
                () -> 0L,
                deferredExecutor);
    }
}
