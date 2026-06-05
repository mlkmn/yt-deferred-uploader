package pl.mlkmn.ytdeferreduploader.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for issue #25: the seed must be all-or-nothing. The original
 * bug (self-invocation bypassing @Transactional) let deleteAll() commit on its
 * own, so a failing saveAll() left the table wiped or half-seeded and open to
 * unique-constraint races with the Drive poller.
 *
 * NOT_SUPPORTED propagation: the test must not own a transaction, otherwise the
 * TransactionTemplate inside seed() would just participate in it and the
 * rollback behavior under test would be invisible.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DemoSeedServiceAtomicityTest {

    @MockitoSpyBean
    private UploadJobRepository repository;

    @Autowired
    private PlatformTransactionManager txManager;

    private final Environment environment = mock(Environment.class);

    @Test
    void seed_whenSaveAllFails_rollsBackTheDelete() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        UploadJob survivor = new UploadJob();
        survivor.setTitle("survivor");
        survivor.setDriveFileId("pre-existing");
        survivor.setStatus(UploadStatus.PENDING);
        repository.saveAndFlush(survivor);

        doThrow(new DataIntegrityViolationException("duplicate drive_file_id"))
                .when(repository).saveAll(anyList());
        DemoSeedService service = new DemoSeedService(
                repository, environment, new TransactionTemplate(txManager));

        assertThatThrownBy(service::seed)
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(repository.findAll())
                .extracting(UploadJob::getDriveFileId)
                .containsExactly("pre-existing");
    }
}
