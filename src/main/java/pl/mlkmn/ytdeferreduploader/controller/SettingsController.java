package pl.mlkmn.ytdeferreduploader.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;
import pl.mlkmn.ytdeferreduploader.service.GoogleDriveService;
import pl.mlkmn.ytdeferreduploader.service.QuotaTracker;
import pl.mlkmn.ytdeferreduploader.service.SettingsService;
import pl.mlkmn.ytdeferreduploader.service.YouTubePlaylistService;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final GoogleAuthorizationCodeFlow authFlow;
    private final AppProperties appProperties;
    private final YouTubePlaylistService playlistService;
    private final QuotaTracker quotaTracker;

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
        model.addAttribute("driveFolder",
                settingsService.getOrDefault(SettingsService.KEY_DRIVE_FOLDER, ""));
        boolean youtubeConnected = settingsService.get(SettingsService.KEY_OAUTH_REFRESH_TOKEN).isPresent();
        model.addAttribute("youtubeConnected", youtubeConnected);
        model.addAttribute("defaultPlaylist",
                settingsService.getOrDefault(SettingsService.KEY_DEFAULT_PLAYLIST, ""));
        if (youtubeConnected) {
            model.addAttribute("playlists", playlistService.getUserPlaylists());
        }
        model.addAttribute("quotaExhausted", quotaTracker.isExhausted());

        // Show resolved folder ID for verification
        String folderInput = settingsService.getOrDefault(SettingsService.KEY_DRIVE_FOLDER, "");
        String resolvedFolderId = GoogleDriveService.extractFolderId(folderInput);
        model.addAttribute("resolvedFolderId", resolvedFolderId);

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
            if (tokenResponse.getExpiresInSeconds() != null) {
                settingsService.set(SettingsService.KEY_OAUTH_TOKEN_EXPIRY,
                        String.valueOf(tokenResponse.getExpiresInSeconds()));
            }
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
        settingsService.delete(SettingsService.KEY_OAUTH_TOKEN_EXPIRY);
        redirectAttributes.addFlashAttribute("success", "YouTube account disconnected");
        return "redirect:/settings";
    }

    @PostMapping("/settings/quota/reset")
    public String resetQuota(RedirectAttributes redirectAttributes) {
        quotaTracker.reset();
        redirectAttributes.addFlashAttribute("success", "Quota status reset");
        return "redirect:/settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam("defaultDescription") String defaultDescription,
                               @RequestParam("defaultTags") String defaultTags,
                               @RequestParam("defaultPrivacy") String defaultPrivacy,
                               @RequestParam("defaultCategory") String defaultCategory,
                               @RequestParam(value = "defaultPlaylist", required = false) String defaultPlaylist,
                               @RequestParam(value = "driveFolder", required = false) String driveFolder,
                               RedirectAttributes redirectAttributes) {
        settingsService.set(SettingsService.KEY_DEFAULT_DESCRIPTION, defaultDescription);
        settingsService.set(SettingsService.KEY_DEFAULT_TAGS, defaultTags);
        settingsService.set(SettingsService.KEY_DEFAULT_PRIVACY, defaultPrivacy);
        settingsService.set(SettingsService.KEY_DEFAULT_CATEGORY, defaultCategory);
        settingsService.set(SettingsService.KEY_DEFAULT_PLAYLIST, defaultPlaylist != null ? defaultPlaylist : "");

        // Validate and save Drive folder
        if (driveFolder != null && !driveFolder.isBlank()) {
            String folderId = GoogleDriveService.extractFolderId(driveFolder);
            if (folderId == null) {
                redirectAttributes.addFlashAttribute("error",
                        "Invalid Drive folder URL or ID. Paste a Google Drive folder URL or folder ID.");
                return "redirect:/settings";
            }
        }
        settingsService.set(SettingsService.KEY_DRIVE_FOLDER, driveFolder != null ? driveFolder : "");

        redirectAttributes.addFlashAttribute("success", "Settings saved");
        return "redirect:/settings";
    }
}
