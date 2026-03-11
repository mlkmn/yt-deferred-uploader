package pl.mlkmn.ytdeferreduploader.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.service.VideoService;

@Controller
public class UploadController {

    private final VideoService videoService;

    public UploadController(VideoService videoService) {
        this.videoService = videoService;
    }

    @GetMapping("/upload")
    public String showUploadForm() {
        return "upload";
    }

    @PostMapping("/upload")
    public String handleUpload(@RequestParam("file") MultipartFile file,
                               @RequestParam("title") String title,
                               @RequestParam(value = "description", required = false) String description,
                               @RequestParam(value = "tags", required = false) String tags,
                               @RequestParam(value = "privacyStatus", required = false) String privacyStatus,
                               RedirectAttributes redirectAttributes) {
        try {
            UploadJob job = videoService.handleUpload(file, title, description, tags, privacyStatus);
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
