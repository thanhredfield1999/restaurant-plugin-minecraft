package vn.restauranttycoon.onboarding;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class OnboardingLifecycleTest {
    @Test
    void newerJoinAndLogoutInvalidateOlderCallbacks() {
        OnboardingLifecycle lifecycle = new OnboardingLifecycle();
        UUID playerId = UUID.randomUUID();
        OnboardingLifecycle.Ticket first = lifecycle.open(playerId);
        OnboardingLifecycle.Ticket second = lifecycle.open(playerId);

        assertFalse(lifecycle.isCurrent(first));
        assertTrue(lifecycle.isCurrent(second));
        lifecycle.invalidate(playerId);
        assertFalse(lifecycle.isCurrent(second));
    }

    @Test
    void closeInvalidatesEveryPlayerAndRejectsNewWork() {
        OnboardingLifecycle lifecycle = new OnboardingLifecycle();
        OnboardingLifecycle.Ticket ticket = lifecycle.open(UUID.randomUUID());

        lifecycle.close();

        assertFalse(lifecycle.isCurrent(ticket));
        assertThrows(IllegalStateException.class, () -> lifecycle.open(UUID.randomUUID()));
    }
}
