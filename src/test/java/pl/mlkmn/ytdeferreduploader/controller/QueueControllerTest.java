package pl.mlkmn.ytdeferreduploader.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pl.mlkmn.ytdeferreduploader.model.PrivacyStatus;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;
import pl.mlkmn.ytdeferreduploader.service.YouTubeCredentialService;
import pl.mlkmn.ytdeferreduploader.service.YouTubeUploadService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
class QueueControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UploadJobRepository jobRepository;

    @MockitoBean private YouTubeUploadService uploadService;
    @MockitoBean private YouTubeCredentialService credentialService;

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

    // --- Reorder ---

    @Test
    void reorder_updatesSortOrder() throws Exception {
        UploadJob job1 = createJob(UploadStatus.PENDING);
        UploadJob job2 = createJob(UploadStatus.PENDING);
        UploadJob job3 = createJob(UploadStatus.PENDING);

        // Reverse order: job3, job1, job2
        String body = "[" + job3.getId() + "," + job1.getId() + "," + job2.getId() + "]";

        mockMvc.perform(post("/queue/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(csrf()))
                .andExpect(status().isOk());

        assertEquals(1, jobRepository.findById(job1.getId()).orElseThrow().getSortOrder());
        assertEquals(2, jobRepository.findById(job2.getId()).orElseThrow().getSortOrder());
        assertEquals(0, jobRepository.findById(job3.getId()).orElseThrow().getSortOrder());
    }

    @Test
    void reorder_schedulerRespectsOrder() {
        UploadJob job1 = createJob(UploadStatus.PENDING);
        UploadJob job2 = createJob(UploadStatus.PENDING);

        // job2 should come first
        job2.setSortOrder(0);
        job1.setSortOrder(1);
        jobRepository.save(job1);
        jobRepository.save(job2);

        var jobs = jobRepository.findByStatusOrderBySortOrderAscCreatedAtAsc(UploadStatus.PENDING);
        assertEquals(job2.getId(), jobs.get(0).getId());
        assertEquals(job1.getId(), jobs.get(1).getId());
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

    // --- Reorder: edge cases ---

    @Test
    void reorder_withNonExistentIds_skipsUnknown() throws Exception {
        UploadJob job1 = createJob(UploadStatus.PENDING);

        String body = "[999," + job1.getId() + "]";

        mockMvc.perform(post("/queue/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(csrf()))
                .andExpect(status().isOk());

        assertEquals(1, jobRepository.findById(job1.getId()).orElseThrow().getSortOrder());
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
