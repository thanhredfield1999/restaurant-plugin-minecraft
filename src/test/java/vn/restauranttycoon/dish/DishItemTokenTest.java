package vn.restauranttycoon.dish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DishItemTokenTest {
    @Test
    void roundTripsCanonicalTokenFields() {
        DishItemToken token = new DishItemToken(
                UUID.randomUUID(), UUID.randomUUID(), 7, "grilled_cod", 3);

        assertEquals(token, DishItemToken.decode(token.encode()).orElseThrow());
    }

    @Test
    void rejectsUnknownSchemaAndMalformedPayload() {
        DishItemToken token = new DishItemToken(
                UUID.randomUUID(), UUID.randomUUID(), 1, "bread", 1);
        String encoded = token.encode();

        assertTrue(DishItemToken.decode(encoded.replaceFirst("^1\\|", "2|")).isEmpty());
        assertTrue(DishItemToken.decode("not-a-token").isEmpty());
        assertTrue(DishItemToken.decode(encoded + "|extra").isEmpty());
    }
}
