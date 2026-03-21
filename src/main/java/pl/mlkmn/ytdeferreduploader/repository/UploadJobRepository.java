package pl.mlkmn.ytdeferreduploader.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UploadJobRepository extends JpaRepository<UploadJob, Long> {

    List<UploadJob> findByStatusOrderByCreatedAtAsc(UploadStatus status);

    Optional<UploadJob> findFirstByStatusAndScheduledAtBeforeOrderByCreatedAtAsc(
            UploadStatus status, Instant before);

    List<UploadJob> findAllByOrderByCreatedAtDesc();

    boolean existsByDriveFileId(String driveFileId);

    List<UploadJob> findByStatusInAndUpdatedAtBefore(List<UploadStatus> statuses, Instant before);
}
