package pl.mlkmn.ytdeferreduploader.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.mlkmn.ytdeferreduploader.model.UploadJob;
import pl.mlkmn.ytdeferreduploader.model.UploadStatus;
import pl.mlkmn.ytdeferreduploader.repository.UploadJobRepository;

@Controller
public class QueueController {

    private final UploadJobRepository uploadJobRepository;

    public QueueController(UploadJobRepository uploadJobRepository) {
        this.uploadJobRepository = uploadJobRepository;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/upload";
    }

    @GetMapping("/queue")
    public String showQueue(Model model) {
        var jobs = uploadJobRepository.findAllByOrderByCreatedAtDesc();
        model.addAttribute("jobs", jobs);
        model.addAttribute("hasActiveJobs", jobs.stream()
                .anyMatch(j -> j.getStatus() == UploadStatus.PENDING || j.getStatus() == UploadStatus.UPLOADING));
        return "queue";
    }

    @GetMapping("/queue/table")
    public String queueTableFragment(Model model) {
        var jobs = uploadJobRepository.findAllByOrderByCreatedAtDesc();
        model.addAttribute("jobs", jobs);
        model.addAttribute("hasActiveJobs", jobs.stream()
                .anyMatch(j -> j.getStatus() == UploadStatus.PENDING || j.getStatus() == UploadStatus.UPLOADING));
        return "queue :: jobTable";
    }

    @PostMapping("/queue/{id}/cancel")
    public String cancelJob(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        UploadJob job = uploadJobRepository.findById(id).orElse(null);
        if (job == null) {
            redirectAttributes.addFlashAttribute("error", "Job not found");
        } else if (job.getStatus() != UploadStatus.PENDING) {
            redirectAttributes.addFlashAttribute("error",
                    "Only PENDING jobs can be cancelled (current: " + job.getStatus() + ")");
        } else {
            job.setStatus(UploadStatus.CANCELLED);
            uploadJobRepository.save(job);
            redirectAttributes.addFlashAttribute("success",
                    "Job #" + id + " cancelled");
        }
        return "redirect:/queue";
    }
}
