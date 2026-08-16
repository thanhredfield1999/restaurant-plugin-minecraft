package vn.restauranttycoon.drink;

public record DrinkDispenserDecision(
        DrinkDispenserOutcome outcome,
        boolean keepLeverDown,
        boolean grantDrink
) {
}
