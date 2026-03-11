package pl.mlkmn.ytdeferreduploader.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.mlkmn.ytdeferreduploader.service.SettingsService;

@Controller
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/settings")
    public String showSettings(Model model) {
        model.addAttribute("defaultDescription",
                settingsService.getOrDefault(SettingsService.KEY_DEFAULT_DESCRIPTION, ""));
        model.addAttribute("defaultTags",
                settingsService.getOrDefault(SettingsService.KEY_DEFAULT_TAGS, ""));
        model.addAttribute("defaultPrivacy",
                settingsService.getOrDefault(SettingsService.KEY_DEFAULT_PRIVACY, "PRIVATE"));
        model.addAttribute("defaultCategory",
                settingsService.getOrDefault(SettingsService.KEY_DEFAULT_CATEGORY, ""));
        return "settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam("defaultDescription") String defaultDescription,
                               @RequestParam("defaultTags") String defaultTags,
                               @RequestParam("defaultPrivacy") String defaultPrivacy,
                               @RequestParam("defaultCategory") String defaultCategory,
                               RedirectAttributes redirectAttributes) {
        settingsService.set(SettingsService.KEY_DEFAULT_DESCRIPTION, defaultDescription);
        settingsService.set(SettingsService.KEY_DEFAULT_TAGS, defaultTags);
        settingsService.set(SettingsService.KEY_DEFAULT_PRIVACY, defaultPrivacy);
        settingsService.set(SettingsService.KEY_DEFAULT_CATEGORY, defaultCategory);
        redirectAttributes.addFlashAttribute("success", "Settings saved");
        return "redirect:/settings";
    }
}
