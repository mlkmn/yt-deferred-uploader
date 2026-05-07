package pl.mlkmn.ytdeferreduploader.devtools;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("devtools")
@ConditionalOnProperty(name = "app.mode", havingValue = "DEMO")
public class MockOutcomeStore {

    private final ConcurrentHashMap<Long, MockOutcome> outcomes = new ConcurrentHashMap<>();

    public void register(Long jobId, MockOutcome outcome) {
        outcomes.put(jobId, outcome);
    }

    public Optional<MockOutcome> consume(Long jobId) {
        return Optional.ofNullable(outcomes.remove(jobId));
    }
}
