package pl.mlkmn.ytdeferreduploader.devtools;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockOutcomeStoreTest {

    private final MockOutcomeStore store = new MockOutcomeStore();

    @Test
    void consume_returnsRegisteredOutcome_thenEmptyOnSecondCall() {
        store.register(42L, MockOutcome.PERMANENT_FAILURE);

        Optional<MockOutcome> first = store.consume(42L);
        Optional<MockOutcome> second = store.consume(42L);

        assertEquals(Optional.of(MockOutcome.PERMANENT_FAILURE), first);
        assertTrue(second.isEmpty(), "second consume should be empty (one-shot)");
    }

    @Test
    void consume_unknownId_returnsEmpty() {
        assertTrue(store.consume(999L).isEmpty());
    }

    @Test
    void register_overwritesPreviousOutcome() {
        store.register(7L, MockOutcome.SUCCESS);
        store.register(7L, MockOutcome.PERMANENT_FAILURE);

        assertEquals(Optional.of(MockOutcome.PERMANENT_FAILURE), store.consume(7L));
    }
}
