package pl.mlkmn.ytdeferreduploader.devtools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pl.mlkmn.ytdeferreduploader.model.PrivacyStatus;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "devtools"})
@TestPropertySource(properties = "app.mode=DEMO")
@WithMockUser(roles = "ADMIN")
class DevtoolsJobControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UploadJobRepository jobRepository;
    @Autowired private MockOutcomeStore outcomeStore;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        outcomeStore.clear();
    }

    @Test
    void postMockJob_persistsPendingJobAndRegistersOutcome() throws Exception {
        mockMvc.perform(post("/devtools/mock-job")
                        .with(csrf())
                        .param("title", "Mock failure")
                        .param("privacyStatus", "PRIVATE")
                        .param("fileSizeMb", "120")
                        .param("outcome", "PERMANENT_FAILURE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/queue"))
                .andExpect(flash().attributeExists("success"));

        List<UploadJob> jobs = jobRepository.findAll();
        assertEquals(1, jobs.size(), "exactly one job persisted");
        UploadJob job = jobs.get(0);
        assertEquals("Mock failure", job.getTitle());
        assertEquals(PrivacyStatus.PRIVATE, job.getPrivacyStatus());
        assertEquals(120L * 1_048_576L, job.getFileSizeBytes());
        assertEquals("Mock failure.mp4", job.getDriveFileName());
        assertNull(job.getDriveFileId(), "driveFileId stays null so legacy upload branch is used");
        assertNull(job.getFilePath(), "filePath stays null");
        assertEquals(UploadStatus.PENDING, job.getStatus());

        assertEquals(Optional.of(MockOutcome.PERMANENT_FAILURE),
                outcomeStore.consume(job.getId()));
    }

    @Test
    void postMockJob_defaultsFileSizeMbTo50WhenOmitted() throws Exception {
        mockMvc.perform(post("/devtools/mock-job")
                        .with(csrf())
                        .param("title", "Mock success")
                        .param("privacyStatus", "UNLISTED")
                        .param("outcome", "SUCCESS"))
                .andExpect(status().is3xxRedirection());

        UploadJob job = jobRepository.findAll().get(0);
        assertEquals(50L * 1_048_576L, job.getFileSizeBytes());
        assertEquals(Optional.of(MockOutcome.SUCCESS),
                outcomeStore.consume(job.getId()));
    }
}
