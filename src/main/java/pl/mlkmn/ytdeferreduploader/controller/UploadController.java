package pl.mlkmn.ytdeferreduploader.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.service.SettingsService;
import pl.mlkmn.ytdeferreduploader.service.VideoService;
import pl.mlkmn.ytdeferreduploader.service.YouTubePlaylistService;

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
    public String handleUpload(@RequestParam("file") MultipartFile file,
                               @RequestParam("title") String title,
                               @RequestParam(value = "description", required = false) String description,
                               @RequestParam(value = "tags", required = false) String tags,
                               @RequestParam(value = "privacyStatus", required = false) String privacyStatus,
                               @RequestParam(value = "playlistId", required = false) String playlistId,
                               RedirectAttributes redirectAttributes) {
        try {
            UploadJob job = videoService.handleUpload(file, title, description, tags, privacyStatus, playlistId);
            redirectAttributes.addFlashAttribute("success",
                    "Video \"" + job.getTitle() + "\" queued for upload (Job #" + job.getId() + ")");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/upload";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Upload failed: " + e.getMessage());
            return "redirect:/upload";
        }
        return "redirect:/queue";
    }
}
