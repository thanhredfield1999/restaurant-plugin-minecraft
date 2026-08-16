package vn.restauranttycoon.supplysetup;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record SupplyDeliveryConvoySimulation(
        UUID shipmentId,
        SupplyDeliveryJourneyPlan plan,
        int currentStepIndex,
        SupplyDeliveryConvoyState state
) {
    public SupplyDeliveryConvoySimulation {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(state, "state");
        if (currentStepIndex < 0 || currentStepIndex > plan.steps().size()) {
            throw new IllegalArgumentException("convoy step index is outside journey plan");
        }
        boolean journeyEnded = currentStepIndex == plan.steps().size();
        if ((state == SupplyDeliveryConvoyState.COMPLETED) != journeyEnded) {
            throw new IllegalArgumentException("convoy completion does not match journey progress");
        }
        if (state == SupplyDeliveryConvoyState.WAITING_FOR_HANDOFF
                && plan.steps().get(currentStepIndex).stage()
                        != SupplyDeliveryJourneyStage.UNLOAD_POINT) {
            throw new IllegalArgumentException("convoy can only wait for handoff at unload point");
        }
    }

    public static SupplyDeliveryConvoySimulation start(
            UUID shipmentId,
            SupplyDeliveryJourneyPlan plan
    ) {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(plan, "plan");
        if (plan.steps().isEmpty()) {
            throw new IllegalArgumentException("journey plan must contain steps");
        }
        return new SupplyDeliveryConvoySimulation(
                shipmentId,
                plan,
                0,
                SupplyDeliveryConvoyState.NAVIGATING);
    }

    public SupplyDeliveryJourneyStep currentStep() {
        return plan.steps().get(currentStepIndex);
    }

    public Optional<SupplyDeliveryJourneyStep> currentStepOptional() {
        if (currentStepIndex >= plan.steps().size()) {
            return Optional.empty();
        }
        return Optional.of(plan.steps().get(currentStepIndex));
    }

    public SupplyDeliveryConvoySimulation arriveAtCurrentStep() {
        if (state != SupplyDeliveryConvoyState.NAVIGATING) {
            throw new IllegalStateException("convoy is not navigating");
        }
        if (currentStep().stage() == SupplyDeliveryJourneyStage.DELIVERY_DESPAWN) {
            return new SupplyDeliveryConvoySimulation(
                    shipmentId,
                    plan,
                    currentStepIndex + 1,
                    SupplyDeliveryConvoyState.COMPLETED);
        }
        if (currentStep().stage() == SupplyDeliveryJourneyStage.UNLOAD_POINT) {
            return new SupplyDeliveryConvoySimulation(
                    shipmentId,
                    plan,
                    currentStepIndex,
                    SupplyDeliveryConvoyState.WAITING_FOR_HANDOFF);
        }
        return new SupplyDeliveryConvoySimulation(
                shipmentId,
                plan,
                currentStepIndex + 1,
                SupplyDeliveryConvoyState.NAVIGATING);
    }

    public SupplyDeliveryConvoySimulation confirmHandoff() {
        if (state != SupplyDeliveryConvoyState.WAITING_FOR_HANDOFF) {
            throw new IllegalStateException("convoy is not waiting for handoff");
        }
        return new SupplyDeliveryConvoySimulation(
                shipmentId,
                plan,
                currentStepIndex + 1,
                SupplyDeliveryConvoyState.NAVIGATING);
    }
}
