package pl.mlkmn.ytdeferreduploader.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;
import pl.mlkmn.ytdeferreduploader.service.SettingsService;

@Controller
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    private final SettingsService settingsService;
    private final GoogleAuthorizationCodeFlow authFlow;
    private final AppProperties appProperties;

    public SettingsController(SettingsService settingsService,
                              GoogleAuthorizationCodeFlow authFlow,
                              AppProperties appProperties) {
        this.settingsService = settingsService;
        this.authFlow = authFlow;
        this.appProperties = appProperties;
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
        model.addAttribute("youtubeConnected",
                settingsService.get(SettingsService.KEY_OAUTH_REFRESH_TOKEN).isPresent());
        return "settings";
    }

    @GetMapping("/settings/oauth/connect")
    public String startOAuth() {
        String authUrl = authFlow.newAuthorizationUrl()
                .setRedirectUri(appProperties.getYoutube().getRedirectUri())
                .build();
        return "redirect:" + authUrl;
    }

    @GetMapping("/settings/oauth/callback")
    public String oauthCallback(@RequestParam("code") String code,
                                RedirectAttributes redirectAttributes) {
        try {
            GoogleTokenResponse tokenResponse = authFlow.newTokenRequest(code)
                    .setRedirectUri(appProperties.getYoutube().getRedirectUri())
                    .execute();
            settingsService.set(SettingsService.KEY_OAUTH_ACCESS_TOKEN, tokenResponse.getAccessToken());
            settingsService.set(SettingsService.KEY_OAUTH_REFRESH_TOKEN, tokenResponse.getRefreshToken());
            redirectAttributes.addFlashAttribute("success", "YouTube account linked successfully");
        } catch (Exception e) {
            log.error("OAuth callback failed", e);
            redirectAttributes.addFlashAttribute("error", "OAuth failed: " + e.getMessage());
        }
        return "redirect:/settings";
    }

    @PostMapping("/settings/oauth/disconnect")
    public String disconnectOAuth(RedirectAttributes redirectAttributes) {
        settingsService.delete(SettingsService.KEY_OAUTH_ACCESS_TOKEN);
        settingsService.delete(SettingsService.KEY_OAUTH_REFRESH_TOKEN);
        redirectAttributes.addFlashAttribute("success", "YouTube account disconnected");
        return "redirect:/settings";
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
