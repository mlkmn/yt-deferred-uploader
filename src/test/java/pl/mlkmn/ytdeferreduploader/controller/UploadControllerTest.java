package pl.mlkmn.ytdeferreduploader.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.mlkmn.ytdeferreduploader.service.VideoService;
import pl.mlkmn.ytdeferreduploader.service.YouTubeCredentialService;
import pl.mlkmn.ytdeferreduploader.service.YouTubeUploadService;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VideoService videoService;

    @MockitoBean
    private YouTubeUploadService uploadService;

    @MockitoBean
    private YouTubeCredentialService credentialService;

    @Test
    void singleFile_withTitle_redirectsToQueue() throws Exception {
        when(videoService.handleUpload(any(), eq("My Title"), any(), any(), any(), any(), any()))
                .thenReturn(null);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "video/mp4", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .param("title", "My Title")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/queue"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void singleFile_withoutTitle_passesNullTitle() throws Exception {
        when(videoService.handleUpload(any(), eq(null), any(), any(), any(), any(), any()))
                .thenReturn(null);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "video/mp4", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/queue"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void singleFile_blankTitle_passesNullTitle() throws Exception {
        when(videoService.handleUpload(any(), eq(null), any(), any(), any(), any(), any()))
                .thenReturn(null);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "video/mp4", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .param("title", "   ")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/queue"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void multipleFiles_titleIgnored() throws Exception {
        when(videoService.handleUpload(any(), eq(null), any(), any(), any(), any(), any()))
                .thenReturn(null);

        MockMultipartFile file1 = new MockMultipartFile(
                "file", "vid1.mp4", "video/mp4", new byte[]{1});
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "vid2.mp4", "video/mp4", new byte[]{2});

        mockMvc.perform(multipart("/upload")
                        .file(file1)
                        .file(file2)
                        .param("title", "My Title")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/queue"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void singleFile_illegalArgumentException_redirectsToUpload() throws Exception {
        when(videoService.handleUpload(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("File is empty"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "video/mp4", new byte[]{1});

        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/upload"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void singleFile_unexpectedException_redirectsToUpload() throws Exception {
        when(videoService.handleUpload(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IOException("Disk full"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.mp4", "video/mp4", new byte[]{1});

        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/upload"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void multipleFiles_partialFailure_redirectsToQueueWithBothMessages() throws Exception {
        // First file succeeds, second fails
        when(videoService.handleUpload(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null)
                .thenThrow(new IllegalArgumentException("Bad file"));

        MockMultipartFile file1 = new MockMultipartFile(
                "file", "vid1.mp4", "video/mp4", new byte[]{1});
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "vid2.mp4", "video/mp4", new byte[]{2});

        mockMvc.perform(multipart("/upload")
                        .file(file1)
                        .file(file2)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/queue"))
                .andExpect(flash().attributeExists("success"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void multipleFiles_allFail_redirectsToUpload() throws Exception {
        when(videoService.handleUpload(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Bad file"));

        MockMultipartFile file1 = new MockMultipartFile(
                "file", "vid1.mp4", "video/mp4", new byte[]{1});
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "vid2.mp4", "video/mp4", new byte[]{2});

        mockMvc.perform(multipart("/upload")
                        .file(file1)
                        .file(file2)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/upload"))
                .andExpect(flash().attributeExists("error"));
    }
}
