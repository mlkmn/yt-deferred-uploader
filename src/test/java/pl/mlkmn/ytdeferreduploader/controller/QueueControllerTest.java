package pl.mlkmn.ytdeferreduploader.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pl.mlkmn.ytdeferreduploader.model.PrivacyStatus;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;
import pl.mlkmn.ytdeferreduploader.service.GoogleDriveService;
import pl.mlkmn.ytdeferreduploader.service.YouTubeCredentialService;
import pl.mlkmn.ytdeferreduploader.service.YouTubeUploadService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
class QueueControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UploadJobRepository jobRepository;

    @MockitoBean private YouTubeUploadService uploadService;
    @MockitoBean private YouTubeCredentialService credentialService;
    @MockitoBean private GoogleDriveService driveService;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
    }

    // --- Cancel ---

    @Test
    void cancel_pendingJob_succeeds() throws Exception {
        UploadJob job = createJob(UploadStatus.PENDING);

        mockMvc.perform(post("/queue/{id}/cancel", job.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        assertEquals(UploadStatus.CANCELLED, jobRepository.findById(job.getId()).orElseThrow().getStatus());
    }

    @Test
    void cancel_nonPendingJob_fails() throws Exception {
        UploadJob job = createJob(UploadStatus.COMPLETED);

        mockMvc.perform(post("/queue/{id}/cancel", job.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        assertEquals(UploadStatus.COMPLETED, jobRepository.findById(job.getId()).orElseThrow().getStatus());
    }

    @Test
    void cancel_nonExistentJob_showsError() throws Exception {
        mockMvc.perform(post("/queue/{id}/cancel", 999L).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
    }

    // --- Retry ---

    @Test
    void retry_failedJob_resetsToPending() throws Exception {
        UploadJob job = createJob(UploadStatus.FAILED);
        job.setRetryCount(3);
        job.setErrorMessage("some error");
        jobRepository.save(job);

        mockMvc.perform(post("/queue/{id}/retry", job.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        UploadJob result = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(UploadStatus.PENDING, result.getStatus());
        assertEquals(0, result.getRetryCount());
        assertNull(result.getErrorMessage());
        assertNotNull(result.getScheduledAt());
    }

    @Test
    void retry_cancelledJob_resetsToPending() throws Exception {
        UploadJob job = createJob(UploadStatus.CANCELLED);

        mockMvc.perform(post("/queue/{id}/retry", job.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        assertEquals(UploadStatus.PENDING, jobRepository.findById(job.getId()).orElseThrow().getStatus());
    }

    @Test
    void retry_pendingJob_fails() throws Exception {
        UploadJob job = createJob(UploadStatus.PENDING);

        mockMvc.perform(post("/queue/{id}/retry", job.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        assertEquals(UploadStatus.PENDING, jobRepository.findById(job.getId()).orElseThrow().getStatus());
    }

    @Test
    void retry_nonExistentJob_showsError() throws Exception {
        mockMvc.perform(post("/queue/{id}/retry", 999L).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
    }

    // --- Delete ---

    @Test
    void delete_completedJob_removesFromDb() throws Exception {
        UploadJob job = createJob(UploadStatus.COMPLETED);

        mockMvc.perform(post("/queue/{id}/delete", job.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        assertTrue(jobRepository.findById(job.getId()).isEmpty());
    }

    @Test
    void delete_pendingJob_removesFromDb() throws Exception {
        UploadJob job = createJob(UploadStatus.PENDING);

        mockMvc.perform(post("/queue/{id}/delete", job.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        assertTrue(jobRepository.findById(job.getId()).isEmpty());
    }

    @Test
    void delete_uploadingJob_blocked() throws Exception {
        UploadJob job = createJob(UploadStatus.UPLOADING);

        mockMvc.perform(post("/queue/{id}/delete", job.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        assertTrue(jobRepository.findById(job.getId()).isPresent());
    }

    @Test
    void delete_nonExistentJob_showsError() throws Exception {
        mockMvc.perform(post("/queue/{id}/delete", 999L).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
    }

    // --- Delete: file handling edge cases ---

    @Test
    void delete_jobWithNullFilePath_removesFromDb() throws Exception {
        UploadJob job = new UploadJob();
        job.setTitle("No File");
        job.setPrivacyStatus(PrivacyStatus.PRIVATE);
        job.setFilePath(null);
        job.setFileSizeBytes(0L);
        job.setStatus(UploadStatus.COMPLETED);
        job.setScheduledAt(Instant.now());
        job = jobRepository.save(job);

        mockMvc.perform(post("/queue/{id}/delete", job.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        assertTrue(jobRepository.findById(job.getId()).isEmpty());
    }

    @Test
    void delete_jobWithNonExistentFile_removesFromDb() throws Exception {
        UploadJob job = new UploadJob();
        job.setTitle("Missing File");
        job.setPrivacyStatus(PrivacyStatus.PRIVATE);
        job.setFilePath("/tmp/nonexistent_file_12345.mp4");
        job.setFileSizeBytes(0L);
        job.setStatus(UploadStatus.COMPLETED);
        job.setScheduledAt(Instant.now());
        job = jobRepository.save(job);

        mockMvc.perform(post("/queue/{id}/delete", job.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        assertTrue(jobRepository.findById(job.getId()).isEmpty());
    }

    // --- GET /queue ---

    @Test
    void showQueue_includesActiveAndFailedJobs() throws Exception {
        UploadJob pending = createJob(UploadStatus.PENDING);
        UploadJob uploading = createJob(UploadStatus.UPLOADING);
        UploadJob failed = createJob(UploadStatus.FAILED);

        mockMvc.perform(get("/queue"))
                .andExpect(status().isOk())
                .andExpect(view().name("queue"))
                .andExpect(model().attribute("jobs", hasItems(
                        hasProperty("id", is(pending.getId())),
                        hasProperty("id", is(uploading.getId())),
                        hasProperty("id", is(failed.getId())))))
                .andExpect(model().attribute("hasActiveJobs", true));
    }

    @Test
    void showQueue_includesRecentlyCompletedJobs() throws Exception {
        UploadJob completed = createJob(UploadStatus.COMPLETED);

        mockMvc.perform(get("/queue"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("jobs", hasItem(
                        hasProperty("id", is(completed.getId())))));
    }

    @Test
    void showQueue_hasActiveJobsFalseWhenOnlyTerminalStates() throws Exception {
        createJob(UploadStatus.COMPLETED);
        createJob(UploadStatus.FAILED);

        mockMvc.perform(get("/queue"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasActiveJobs", false));
    }

    @Test
    void queueTable_returnsFragmentView() throws Exception {
        createJob(UploadStatus.PENDING);

        mockMvc.perform(get("/queue/table"))
                .andExpect(status().isOk())
                .andExpect(view().name("queue :: jobTable"));
    }

    // --- GET /queue/archive ---

    @Test
    void archive_returnsCompletedAndCancelledJobs() throws Exception {
        UploadJob completed = createJob(UploadStatus.COMPLETED);
        UploadJob cancelled = createJob(UploadStatus.CANCELLED);
        createJob(UploadStatus.FAILED);
        createJob(UploadStatus.PENDING);

        mockMvc.perform(get("/queue/archive"))
                .andExpect(status().isOk())
                .andExpect(view().name("archive"))
                .andExpect(model().attribute("jobs", hasItems(
                        hasProperty("id", is(completed.getId())),
                        hasProperty("id", is(cancelled.getId())))))
                .andExpect(model().attribute("currentPage", 0));
    }

    @Test
    void archive_excludesFailedJobs() throws Exception {
        createJob(UploadStatus.FAILED);

        mockMvc.perform(get("/queue/archive"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("jobs", empty()));
    }

    @Test
    void archive_paginatesAt25PerPage() throws Exception {
        for (int i = 0; i < 30; i++) {
            createJob(UploadStatus.COMPLETED);
        }

        mockMvc.perform(get("/queue/archive"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("jobs", hasSize(25)))
                .andExpect(model().attribute("totalPages", 2));

        mockMvc.perform(get("/queue/archive").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("jobs", hasSize(5)))
                .andExpect(model().attribute("currentPage", 1));
    }

    @Test
    void archive_outOfRangePageRendersEmpty() throws Exception {
        createJob(UploadStatus.COMPLETED);

        mockMvc.perform(get("/queue/archive").param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("jobs", empty()));
    }

    private UploadJob createJob(UploadStatus status) {
        UploadJob job = new UploadJob();
        job.setTitle("Test Video");
        job.setPrivacyStatus(PrivacyStatus.PRIVATE);
        job.setFilePath("/tmp/test.mp4");
        job.setFileSizeBytes(1024L);
        job.setStatus(status);
        job.setScheduledAt(Instant.now().minusSeconds(60));
        return jobRepository.save(job);
    }
}
