package pl.mlkmn.ytdeferreduploader.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for issue #30. Uses the real repository (not a mocked saveAll like
 * DemoSeedServiceAtomicityTest) so Hibernate's real flush ordering is exercised.
 * NOT_SUPPORTED: the test must not own a transaction, so each seed() commits and the
 * second run sees a populated table - which is what reproduces the collision.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DemoSeedServiceReseedTest {

    @Autowired
    private UploadJobRepository repository;

    @Autowired
    private PlatformTransactionManager txManager;

    private final Environment environment = mock(Environment.class);

    @AfterEach
    void cleanUp() {
        repository.deleteAllInBatch();
    }

    @Test
    void seed_runTwice_replacesRowsWithoutConstraintViolation() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        DemoSeedService service = new DemoSeedService(
                repository, environment, new TransactionTemplate(txManager));

        service.seed();

        assertThatCode(service::seed).doesNotThrowAnyException();
        assertThat(repository.findAll())
                .extracting(UploadJob::getDriveFileId)
                .containsExactlyInAnyOrder("demo-1", "demo-2", "demo-3", "demo-4");
    }
}
