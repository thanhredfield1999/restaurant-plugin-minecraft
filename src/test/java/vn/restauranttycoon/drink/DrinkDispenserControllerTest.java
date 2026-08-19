package vn.restauranttycoon.drink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DrinkDispenserControllerTest {
    private static final String STATION = "water_1";
    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OTHER_PLAYER = UUID.randomUUID();

    @Test
    void startsFillingWhenAnEmptyHandPushesTheLeverDown() {
        DrinkDispenserController controller = new DrinkDispenserController(60);

        DrinkDispenserDecision decision = controller.interact(STATION, PLAYER, false, true, 100);

        assertEquals(DrinkDispenserOutcome.STARTED, decision.outcome());
        assertTrue(decision.keepLeverDown());
        assertFalse(decision.grantDrink());
    }

    @Test
    void allowsStartingWithAnOccupiedMainHandBecauseOnlyPickupRequiresAnEmptyHand() {
        DrinkDispenserController controller = new DrinkDispenserController(60);

        DrinkDispenserDecision decision = controller.interact(STATION, PLAYER, false, false, 100);

        assertEquals(DrinkDispenserOutcome.STARTED, decision.outcome());
        assertTrue(decision.keepLeverDown());
        assertFalse(decision.grantDrink());
        assertTrue(controller.hasActiveFill(STATION));
    }

    @Test
    void keepsLeverDownWhenRaisedBeforeTheDrinkIsFull() {
        DrinkDispenserController controller = new DrinkDispenserController(60);
        controller.interact(STATION, PLAYER, false, true, 100);

        DrinkDispenserDecision decision = controller.interact(STATION, PLAYER, true, true, 159);

        assertEquals(DrinkDispenserOutcome.STILL_FILLING, decision.outcome());
        assertTrue(decision.keepLeverDown());
        assertFalse(decision.grantDrink());
    }

    @Test
    void grantsDrinkOnlyWhenOwnerRaisesLeverAfterFillDuration() {
        DrinkDispenserController controller = new DrinkDispenserController(60);
        controller.interact(STATION, PLAYER, false, true, 100);

        DrinkDispenserDecision decision = controller.interact(STATION, PLAYER, true, true, 160);

        assertEquals(DrinkDispenserOutcome.DISPENSED, decision.outcome());
        assertFalse(decision.keepLeverDown());
        assertTrue(decision.grantDrink());
        assertFalse(controller.hasActiveFill(STATION));
    }

    @Test
    void keepsReadyDrinkWhenOwnersHandIsOccupied() {
        DrinkDispenserController controller = new DrinkDispenserController(60);
        controller.interact(STATION, PLAYER, false, true, 100);

        DrinkDispenserDecision blocked = controller.interact(STATION, PLAYER, true, false, 160);
        DrinkDispenserDecision retried = controller.interact(STATION, PLAYER, true, true, 200);

        assertEquals(DrinkDispenserOutcome.HAND_MUST_BE_EMPTY, blocked.outcome());
        assertTrue(blocked.keepLeverDown());
        assertFalse(blocked.grantDrink());
        assertEquals(DrinkDispenserOutcome.DISPENSED, retried.outcome());
        assertTrue(retried.grantDrink());
    }

    @Test
    void preventsAnotherPlayerFromTakingTheDrink() {
        DrinkDispenserController controller = new DrinkDispenserController(60);
        controller.interact(STATION, PLAYER, false, true, 100);

        DrinkDispenserDecision denied = controller.interact(STATION, OTHER_PLAYER, true, true, 200);
        DrinkDispenserDecision ownerPickup = controller.interact(STATION, PLAYER, true, true, 201);

        assertEquals(DrinkDispenserOutcome.OWNED_BY_ANOTHER_PLAYER, denied.outcome());
        assertTrue(denied.keepLeverDown());
        assertFalse(denied.grantDrink());
        assertEquals(DrinkDispenserOutcome.DISPENSED, ownerPickup.outcome());
    }

    @Test
    void preservesExistingFillIfLeverWasExternallyRaised() {
        DrinkDispenserController controller = new DrinkDispenserController(60);
        controller.interact(STATION, PLAYER, false, true, 100);

        DrinkDispenserDecision decision = controller.interact(STATION, PLAYER, false, true, 200);

        assertEquals(DrinkDispenserOutcome.STILL_FILLING, decision.outcome());
        assertTrue(decision.keepLeverDown());
        assertFalse(decision.grantDrink());
        assertTrue(controller.hasActiveFill(STATION));
    }

    @Test
    void resetsPoweredLeverWithoutStateAfterRestartWithoutGranting() {
        DrinkDispenserController controller = new DrinkDispenserController(60);

        DrinkDispenserDecision decision = controller.interact(STATION, PLAYER, true, true, 100);

        assertEquals(DrinkDispenserOutcome.RESET, decision.outcome());
        assertFalse(decision.keepLeverDown());
        assertFalse(decision.grantDrink());
    }

    @Test
    void clearingAStationInvalidatesItsPendingDrink() {
        DrinkDispenserController controller = new DrinkDispenserController(60);
        controller.interact(STATION, PLAYER, false, true, 100);

        controller.clear(STATION);

        assertFalse(controller.hasActiveFill(STATION));
        assertEquals(
                DrinkDispenserOutcome.RESET,
                controller.interact(STATION, PLAYER, true, true, 200).outcome());
    }

    @Test
    void exposesBoundedFillProgressForStationHolograms() {
        DrinkDispenserController controller = new DrinkDispenserController(100);

        assertEquals(DrinkStationProgress.idle(), controller.progress(STATION, 100));
        controller.interact(STATION, PLAYER, false, true, 100);

        assertEquals(new DrinkStationProgress(true, 0), controller.progress(STATION, 100));
        assertEquals(new DrinkStationProgress(true, 59), controller.progress(STATION, 159));
        assertEquals(new DrinkStationProgress(true, 100), controller.progress(STATION, 250));
    }
}
