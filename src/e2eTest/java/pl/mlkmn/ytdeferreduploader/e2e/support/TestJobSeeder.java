package pl.mlkmn.ytdeferreduploader.e2e.support;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pl.mlkmn.ytdeferreduploader.model.PrivacyStatus;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;

@Component
@Profile("e2e")
@RequiredArgsConstructor
public class TestJobSeeder {

    private final UploadJobRepository repository;

    public UploadJob seedPending(String title) {
        return save(title, UploadStatus.PENDING, null, null);
    }

    public UploadJob seedUploading(String title) {
        return save(title, UploadStatus.UPLOADING, null, null);
    }

    public UploadJob seedFailed(String title, String errorMessage) {
        return save(title, UploadStatus.FAILED, errorMessage, null);
    }

    public UploadJob seedCompleted(String title, String youtubeId) {
        return save(title, UploadStatus.COMPLETED, null, youtubeId);
    }

    public UploadJob seedCancelled(String title) {
        return save(title, UploadStatus.CANCELLED, null, null);
    }

    public void markCompleted(Long jobId) {
        UploadJob job = repository.findById(jobId).orElseThrow();
        job.setStatus(UploadStatus.COMPLETED);
        repository.save(job);
    }

    public void clearAll() {
        repository.deleteAll();
    }

    private UploadJob save(String title, UploadStatus status, String errorMessage, String youtubeId) {
        UploadJob job = new UploadJob();
        job.setTitle(title);
        job.setPrivacyStatus(PrivacyStatus.PRIVATE);
        job.setFilePath("/seeded/" + title + ".mp4");
        job.setFileSizeBytes(1024L * 1024L);
        job.setStatus(status);
        job.setErrorMessage(errorMessage);
        job.setYoutubeId(youtubeId);
        return repository.save(job);
    }
}
