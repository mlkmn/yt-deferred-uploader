package pl.mlkmn.ytdeferreduploader.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "upload_jobs")
@Getter
@Setter
public class UploadJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Size(max = 500)
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column(name = "privacy_status")
    private PrivacyStatus privacyStatus = PrivacyStatus.PRIVATE;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    private UploadStatus status = UploadStatus.PENDING;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(name = "youtube_id", length = 20)
    private String youtubeId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count")
    private int retryCount = 0;

    @Column(name = "sort_order")
    private int sortOrder = 0;

    @Setter(lombok.AccessLevel.NONE)
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Setter(lombok.AccessLevel.NONE)
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
