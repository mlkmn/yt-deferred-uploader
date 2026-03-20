package pl.mlkmn.ytdeferreduploader.controller;

import com.google.api.client.util.DateTime;
import com.google.api.services.drive.model.File;
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
import pl.mlkmn.ytdeferreduploader.service.GoogleDriveService;
import pl.mlkmn.ytdeferreduploader.service.YouTubeCredentialService;
import pl.mlkmn.ytdeferreduploader.service.YouTubeUploadService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
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

    // --- Add from Drive (Picker) ---

    @Test
    void fromDrive_emptyFileIds_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/queue/from-drive").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No file IDs provided"));
    }

    @Test
    void fromDrive_noBody_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/queue/from-drive").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No file IDs provided"));
    }

    @Test
    void fromDrive_notConnected_returnsBadRequest() throws Exception {
        when(credentialService.isConnected()).thenReturn(false);

        mockMvc.perform(post("/queue/from-drive").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileIds\":[\"abc123\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Google account not connected"));
    }

    @Test
    void fromDrive_validFile_createsJob() throws Exception {
        when(credentialService.isConnected()).thenReturn(true);

        File driveFile = new File();
        driveFile.setId("driveFile1");
        driveFile.setName("VID_20260314_153022.mp4");
        driveFile.setSize(52428800L);
        driveFile.setMimeType("video/mp4");
        driveFile.setModifiedTime(new DateTime(System.currentTimeMillis()));
        when(driveService.getFileMetadata("driveFile1")).thenReturn(driveFile);

        mockMvc.perform(post("/queue/from-drive").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileIds\":[\"driveFile1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queued").value(1))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.errors").value(0));

        var jobs = jobRepository.findAllByOrderByCreatedAtDesc();
        assertEquals(1, jobs.size());
        assertEquals("driveFile1", jobs.getFirst().getDriveFileId());
        assertEquals("VID_20260314_153022.mp4", jobs.getFirst().getDriveFileName());
        assertEquals(UploadStatus.PENDING, jobs.getFirst().getStatus());
        assertEquals(52428800L, jobs.getFirst().getFileSizeBytes());
    }

    @Test
    void fromDrive_duplicateFile_skipped() throws Exception {
        when(credentialService.isConnected()).thenReturn(true);

        // Create an existing job with this drive file ID
        UploadJob existing = new UploadJob();
        existing.setTitle("Existing");
        existing.setDriveFileId("driveFile2");
        existing.setDriveFileName("existing.mp4");
        existing.setPrivacyStatus(PrivacyStatus.PRIVATE);
        existing.setStatus(UploadStatus.PENDING);
        existing.setScheduledAt(Instant.now());
        jobRepository.save(existing);

        mockMvc.perform(post("/queue/from-drive").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileIds\":[\"driveFile2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queued").value(0))
                .andExpect(jsonPath("$.skipped").value(1));
    }

    @Test
    void fromDrive_multipleFiles_mixedResults() throws Exception {
        when(credentialService.isConnected()).thenReturn(true);

        File driveFile1 = new File();
        driveFile1.setId("newFile1");
        driveFile1.setName("video1.mp4");
        driveFile1.setSize(1024L);
        driveFile1.setMimeType("video/mp4");
        when(driveService.getFileMetadata("newFile1")).thenReturn(driveFile1);

        when(driveService.getFileMetadata("badFile")).thenThrow(new IOException("Not found"));

        mockMvc.perform(post("/queue/from-drive").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileIds\":[\"newFile1\",\"badFile\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queued").value(1))
                .andExpect(jsonPath("$.errors").value(1));
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
