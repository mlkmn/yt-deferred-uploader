package pl.mlkmn.ytdeferreduploader.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for issue #25: a seed failure escaping seedOnStartup() killed
 * the whole application (exceptions from ApplicationReadyEvent listeners fail
 * SpringApplication.run()), producing a Railway crash loop. Seed failures must
 * be contained and logged on both triggers.
 */
class DemoSeedServiceFailureContainmentTest {

    private final UploadJobRepository repo = mock(UploadJobRepository.class);
    private final Environment env = mock(Environment.class);
    private final TransactionTemplate txTemplate =
            new TransactionTemplate(mock(PlatformTransactionManager.class));

    private final DemoSeedService service = new DemoSeedService(repo, env, txTemplate);

    @Test
    void seedOnStartup_whenSeedFails_doesNotPropagate() {
        when(env.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        doThrow(new DataIntegrityViolationException("duplicate")).when(repo).deleteAll();

        assertThatCode(service::seedOnStartup).doesNotThrowAnyException();
    }

    @Test
    void resetOnSchedule_whenSeedFails_doesNotPropagate() {
        when(env.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        doThrow(new DataIntegrityViolationException("duplicate")).when(repo).deleteAll();

        assertThatCode(service::resetOnSchedule).doesNotThrowAnyException();
    }
}
