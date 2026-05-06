package pl.mlkmn.ytdeferreduploader.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("""
        SELECT j FROM UploadJob j
        WHERE j.status IN :alwaysVisible
           OR (j.status IN :recentTail AND j.updatedAt >= :updatedAfter)
        ORDER BY j.createdAt DESC
    """)
    List<UploadJob> findActiveAndRecent(
            @Param("alwaysVisible") List<UploadStatus> alwaysVisible,
            @Param("recentTail") List<UploadStatus> recentTail,
            @Param("updatedAfter") Instant updatedAfter);

    Page<UploadJob> findByStatusInOrderByCreatedAtDesc(List<UploadStatus> statuses, Pageable pageable);
}
