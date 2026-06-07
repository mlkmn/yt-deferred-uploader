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
 * Regression test for issue #30: a scheduled reset re-seeds an already-populated
 * table. Within one Hibernate flush, INSERTs are ordered ahead of DELETEs, so the
 * new rows would be inserted before deleteAll()'s deletes reach the database -
 * colliding with the still-present demo-3 row on the drive_file_id unique index.
 *
 * Uses the real repository (no mocked saveAll) so the actual flush ordering against
 * a real schema is exercised - the gap that DemoSeedServiceAtomicityTest left.
 *
 * NOT_SUPPORTED propagation: the test must not own a transaction, so each seed()
 * commits in its own transaction and the second run sees a committed, populated
 * table (which is what reproduces the collision).
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
