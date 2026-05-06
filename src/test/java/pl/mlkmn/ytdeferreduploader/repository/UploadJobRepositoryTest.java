package pl.mlkmn.ytdeferreduploader.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import pl.mlkmn.ytdeferreduploader.model.PrivacyStatus;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UploadJobRepositoryTest {

    @Autowired
    private UploadJobRepository repository;

    private static final List<UploadStatus> ALWAYS_VISIBLE =
            List.of(UploadStatus.PENDING, UploadStatus.UPLOADING, UploadStatus.FAILED);
    private static final List<UploadStatus> RECENT_TAIL =
            List.of(UploadStatus.COMPLETED, UploadStatus.CANCELLED);

    @Test
    void findActiveAndRecent_includesAlwaysVisibleStatuses_regardlessOfTimestamp() {
        save("pending", UploadStatus.PENDING);
        save("uploading", UploadStatus.UPLOADING);
        save("failed", UploadStatus.FAILED);

        Instant futureCutoff = Instant.now().plusSeconds(3600);
        List<UploadJob> result = repository.findActiveAndRecent(
                ALWAYS_VISIBLE, RECENT_TAIL, futureCutoff);

        assertThat(result).extracting(UploadJob::getTitle)
                .containsExactlyInAnyOrder("pending", "uploading", "failed");
    }

    @Test
    void findActiveAndRecent_includesRecentTailWithinWindow() {
        save("recent-completed", UploadStatus.COMPLETED);
        save("recent-cancelled", UploadStatus.CANCELLED);

        Instant pastCutoff = Instant.now().minusSeconds(60);
        List<UploadJob> result = repository.findActiveAndRecent(
                ALWAYS_VISIBLE, RECENT_TAIL, pastCutoff);

        assertThat(result).extracting(UploadJob::getTitle)
                .containsExactlyInAnyOrder("recent-completed", "recent-cancelled");
    }

    @Test
    void findActiveAndRecent_excludesRecentTailOutsideWindow() {
        save("old-completed", UploadStatus.COMPLETED);
        save("old-cancelled", UploadStatus.CANCELLED);

        Instant futureCutoff = Instant.now().plusSeconds(3600);
        List<UploadJob> result = repository.findActiveAndRecent(
                ALWAYS_VISIBLE, RECENT_TAIL, futureCutoff);

        assertThat(result).isEmpty();
    }

    @Test
    void findActiveAndRecent_orderedByCreatedAtDesc() {
        UploadJob first = save("first", UploadStatus.PENDING);
        UploadJob second = save("second", UploadStatus.PENDING);

        List<UploadJob> result = repository.findActiveAndRecent(
                ALWAYS_VISIBLE, RECENT_TAIL, Instant.now().plusSeconds(3600));

        assertThat(result).extracting(UploadJob::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void findByStatusInOrderByCreatedAtDesc_paginatesCorrectly() {
        for (int i = 0; i < 30; i++) {
            save("completed-" + i, UploadStatus.COMPLETED);
        }

        Page<UploadJob> firstPage = repository.findByStatusInOrderByCreatedAtDesc(
                List.of(UploadStatus.COMPLETED, UploadStatus.CANCELLED),
                PageRequest.of(0, 25));
        Page<UploadJob> secondPage = repository.findByStatusInOrderByCreatedAtDesc(
                List.of(UploadStatus.COMPLETED, UploadStatus.CANCELLED),
                PageRequest.of(1, 25));

        assertThat(firstPage.getContent()).hasSize(25);
        assertThat(secondPage.getContent()).hasSize(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(30);
    }

    @Test
    void findByStatusInOrderByCreatedAtDesc_excludesUnlistedStatuses() {
        save("pending", UploadStatus.PENDING);
        save("failed", UploadStatus.FAILED);
        save("completed", UploadStatus.COMPLETED);
        save("cancelled", UploadStatus.CANCELLED);

        Page<UploadJob> page = repository.findByStatusInOrderByCreatedAtDesc(
                List.of(UploadStatus.COMPLETED, UploadStatus.CANCELLED),
                PageRequest.of(0, 25));

        assertThat(page.getContent()).extracting(UploadJob::getTitle)
                .containsExactlyInAnyOrder("completed", "cancelled");
    }

    @Test
    void findByStatusInOrderByCreatedAtDesc_outOfRangePageReturnsEmpty() {
        save("only", UploadStatus.COMPLETED);

        Page<UploadJob> page = repository.findByStatusInOrderByCreatedAtDesc(
                List.of(UploadStatus.COMPLETED, UploadStatus.CANCELLED),
                PageRequest.of(99, 25));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    private UploadJob save(String title, UploadStatus status) {
        UploadJob job = new UploadJob();
        job.setTitle(title);
        job.setPrivacyStatus(PrivacyStatus.PRIVATE);
        job.setFilePath("/tmp/" + title + ".mp4");
        job.setFileSizeBytes(1024L);
        job.setStatus(status);
        return repository.save(job);
    }
}
