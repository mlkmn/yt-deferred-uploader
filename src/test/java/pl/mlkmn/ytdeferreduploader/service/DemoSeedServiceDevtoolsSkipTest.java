package pl.mlkmn.ytdeferreduploader.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoSeedServiceDevtoolsSkipTest {

    private final UploadJobRepository repo = mock(UploadJobRepository.class);
    private final Environment env = mock(Environment.class);
    private final TransactionTemplate txTemplate =
            new TransactionTemplate(mock(PlatformTransactionManager.class));

    @Test
    void seedOnStartup_devtoolsActive_doesNotTouchRepository() {
        when(env.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        DemoSeedService service = new DemoSeedService(repo, env, txTemplate);

        service.seedOnStartup();

        verify(repo, never()).deleteAll();
        verify(repo, never()).saveAll(any());
    }

    @Test
    void resetOnSchedule_devtoolsActive_doesNotTouchRepository() {
        when(env.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        DemoSeedService service = new DemoSeedService(repo, env, txTemplate);

        service.resetOnSchedule();

        verify(repo, never()).deleteAll();
        verify(repo, never()).saveAll(any());
    }

    @Test
    void seedOnStartup_devtoolsInactive_seedsAsBefore() {
        when(env.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        DemoSeedService service = new DemoSeedService(repo, env, txTemplate);

        service.seedOnStartup();

        verify(repo, times(1)).deleteAll();
        verify(repo, times(1)).saveAll(any());
    }

    @Test
    void resetOnSchedule_devtoolsInactive_seedsAsBefore() {
        when(env.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        DemoSeedService service = new DemoSeedService(repo, env, txTemplate);

        service.resetOnSchedule();

        verify(repo, times(1)).deleteAll();
        verify(repo, times(1)).saveAll(any());
    }
}
