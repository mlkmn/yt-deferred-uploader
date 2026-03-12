package pl.mlkmn.ytdeferreduploader.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.mlkmn.ytdeferreduploader.service.SettingsService;
import pl.mlkmn.ytdeferreduploader.service.VideoService;
import pl.mlkmn.ytdeferreduploader.service.YouTubePlaylistService;

import java.util.ArrayList;

@Slf4j
@Controller
@RequiredArgsConstructor
public class UploadController {

    private final VideoService videoService;
    private final YouTubePlaylistService playlistService;
    private final SettingsService settingsService;

    @GetMapping("/upload")
    public String showUploadForm(Model model) {
        model.addAttribute("playlists", playlistService.getUserPlaylists());
        model.addAttribute("defaultPlaylist",
                settingsService.getOrDefault(SettingsService.KEY_DEFAULT_PLAYLIST, ""));
        return "upload";
    }

    @PostMapping("/upload")
    public String handleUpload(@RequestParam("file") MultipartFile[] files,
                               @RequestParam(value = "title", required = false) String title,
                               @RequestParam(value = "description", required = false) String description,
                               @RequestParam(value = "tags", required = false) String tags,
                               @RequestParam(value = "privacyStatus", required = false) String privacyStatus,
                               @RequestParam(value = "playlistId", required = false) String playlistId,
                               RedirectAttributes redirectAttributes) {
        var errors = new ArrayList<String>();
        int successCount = 0;

        for (MultipartFile file : files) {
            String fileTitle = (files.length == 1 && title != null && !title.isBlank())
                    ? title : null;
            try {
                videoService.handleUpload(file, fileTitle, description, tags, privacyStatus, playlistId);
                successCount++;
            } catch (IllegalArgumentException e) {
                errors.add(file.getOriginalFilename() + ": " + e.getMessage());
            } catch (Exception e) {
                log.error("Upload failed for file: {}", file.getOriginalFilename(), e);
                errors.add(file.getOriginalFilename() + ": " + e.getMessage());
            }
        }

        if (successCount > 0) {
            redirectAttributes.addFlashAttribute("success",
                    successCount + " video(s) queued for upload");
        }
        if (!errors.isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    "Failed: " + String.join("; ", errors));
            if (successCount == 0) {
                return "redirect:/upload";
            }
        }
        return "redirect:/queue";
    }
}
