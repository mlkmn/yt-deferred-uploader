package pl.mlkmn.ytdeferreduploader.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.mlkmn.ytdeferreduploader.config.AppMode;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;
import pl.mlkmn.ytdeferreduploader.service.AccountDeletionService;
import pl.mlkmn.ytdeferreduploader.config.YouTubeApiConfig.AuthFlowFactory;
import pl.mlkmn.ytdeferreduploader.service.GoogleDriveService;
import pl.mlkmn.ytdeferreduploader.service.QuotaTracker;
import pl.mlkmn.ytdeferreduploader.service.SettingsService;
import pl.mlkmn.ytdeferreduploader.service.YouTubePlaylistService;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final AuthFlowFactory authFlowFactory;
    private final AppProperties appProperties;
    private final YouTubePlaylistService playlistService;
    private final QuotaTracker quotaTracker;
    private final AccountDeletionService accountDeletionService;

    @GetMapping("/settings")
    public String showSettings(Model model) {
        AppMode mode = appProperties.getMode();
        model.addAttribute("appMode", mode);

        model.addAttribute("defaultDescription",
                settingsService.getOrDefault(SettingsService.KEY_DEFAULT_DESCRIPTION, ""));
        model.addAttribute("defaultPrivacy",
                settingsService.getOrDefault(SettingsService.KEY_DEFAULT_PRIVACY, "PRIVATE"));

        if (mode.canPollDrive()) {
            String driveFolder = settingsService.getOrDefault(SettingsService.KEY_DRIVE_FOLDER, "");
            model.addAttribute("driveFolder", driveFolder);
            model.addAttribute("resolvedFolderId", GoogleDriveService.extractFolderId(driveFolder));
        }

        boolean youtubeConnected = settingsService.get(SettingsService.KEY_OAUTH_REFRESH_TOKEN).isPresent();
        model.addAttribute("youtubeConnected", youtubeConnected);

        if (youtubeConnected) {
            playlistService.getChannel().ifPresent(ch ->
                    model.addAttribute("channelTitle", ch.getSnippet().getTitle()));

            if (mode.canListPlaylists()) {
                model.addAttribute("defaultPlaylist",
                        settingsService.getOrDefault(SettingsService.KEY_DEFAULT_PLAYLIST, ""));
                model.addAttribute("playlists", playlistService.getUserPlaylists());
            }
        }

        model.addAttribute("quotaExhausted", quotaTracker.isExhausted());
        model.addAttribute("jobRetentionDays",
                settingsService.getOrDefault(SettingsService.KEY_JOB_RETENTION_DAYS, "30"));

        return "settings";
    }

    @GetMapping("/settings/oauth/consent")
    public String showOAuthConsent(Model model) {
        model.addAttribute("appMode", appProperties.getMode());
        return "oauth-consent";
    }

    @GetMapping("/settings/oauth/connect")
    public String startOAuth() {
        AppMode mode = appProperties.getMode();
        GoogleAuthorizationCodeFlow flow = authFlowFactory.buildFlow(mode.getScopes());
        String redirectUri = appProperties.getYoutube().getRedirectUri();
        log.info("OAuth connect initiated: redirectUri={}, mode={}", redirectUri, mode);
        String authUrl = flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .build();
        log.info("Redirecting to Google OAuth: url={}", authUrl);
        return "redirect:" + authUrl;
    }

    @GetMapping("/settings/oauth/callback")
    public String oauthCallback(@RequestParam("code") String code,
                                RedirectAttributes redirectAttributes) {
        GoogleAuthorizationCodeFlow flow = authFlowFactory.buildFlow(appProperties.getMode().getScopes());
        String redirectUri = appProperties.getYoutube().getRedirectUri();
        log.info("OAuth callback received: redirectUri={}, codeLength={}", redirectUri, code.length());
        try {
            GoogleTokenResponse tokenResponse = flow.newTokenRequest(code)
                    .setRedirectUri(redirectUri)
                    .execute();
            log.info("OAuth token exchange successful: hasAccessToken={}, hasRefreshToken={}, expiresIn={}",
                    tokenResponse.getAccessToken() != null,
                    tokenResponse.getRefreshToken() != null,
                    tokenResponse.getExpiresInSeconds());
            settingsService.set(SettingsService.KEY_OAUTH_ACCESS_TOKEN, tokenResponse.getAccessToken());
            settingsService.set(SettingsService.KEY_OAUTH_REFRESH_TOKEN, tokenResponse.getRefreshToken());
            if (tokenResponse.getExpiresInSeconds() != null) {
                settingsService.set(SettingsService.KEY_OAUTH_TOKEN_EXPIRY,
                        String.valueOf(tokenResponse.getExpiresInSeconds()));
            }
            redirectAttributes.addFlashAttribute("success", "YouTube account linked successfully");
        } catch (IOException e) {
            log.error("OAuth token exchange failed: redirectUri={}", redirectUri, e);
            redirectAttributes.addFlashAttribute("error", "Failed to exchange OAuth code. Please try again.");
        } catch (Exception e) {
            log.error("Unexpected error during OAuth callback: redirectUri={}", redirectUri, e);
            redirectAttributes.addFlashAttribute("error", "An unexpected error occurred. Please try again.");
        }
        return "redirect:/settings";
    }

    @PostMapping("/settings/oauth/disconnect")
    public String disconnectOAuth(RedirectAttributes redirectAttributes) {
        settingsService.delete(SettingsService.KEY_OAUTH_ACCESS_TOKEN);
        settingsService.delete(SettingsService.KEY_OAUTH_REFRESH_TOKEN);
        settingsService.delete(SettingsService.KEY_OAUTH_TOKEN_EXPIRY);
        settingsService.delete(SettingsService.KEY_DRIVE_FOLDER);
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
                               @RequestParam("defaultPrivacy") String defaultPrivacy,
                               @RequestParam(value = "defaultPlaylist", required = false) String defaultPlaylist,
                               @RequestParam(value = "jobRetentionDays", required = false) String jobRetentionDays,
                               @RequestParam(value = "driveFolder", required = false) String driveFolder,
                               RedirectAttributes redirectAttributes) {
        AppMode mode = appProperties.getMode();

        settingsService.set(SettingsService.KEY_DEFAULT_DESCRIPTION, defaultDescription);
        settingsService.set(SettingsService.KEY_DEFAULT_PRIVACY, defaultPrivacy);

        String retentionValue = jobRetentionDays != null ? jobRetentionDays.trim() : "30";
        try {
            int days = Integer.parseInt(retentionValue);
            if (days < -1) {
                redirectAttributes.addFlashAttribute("error", "Job retention days must be -1 (keep forever) or 0+");
                return "redirect:/settings";
            }
            settingsService.set(SettingsService.KEY_JOB_RETENTION_DAYS, retentionValue);
        } catch (NumberFormatException e) {
            redirectAttributes.addFlashAttribute("error", "Job retention days must be a number");
            return "redirect:/settings";
        }

        if (mode.canListPlaylists()) {
            settingsService.set(SettingsService.KEY_DEFAULT_PLAYLIST, defaultPlaylist != null ? defaultPlaylist : "");
        }

        // Validate and save Drive folder (self-hosted only, requires connected account)
        boolean youtubeConnected = settingsService.get(SettingsService.KEY_OAUTH_REFRESH_TOKEN).isPresent();
        if (mode.canPollDrive() && youtubeConnected) {
            if (driveFolder != null && !driveFolder.isBlank()) {
                String folderId = GoogleDriveService.extractFolderId(driveFolder);
                if (folderId == null) {
                    redirectAttributes.addFlashAttribute("error",
                            "Invalid Drive folder URL or ID. Paste a Google Drive folder URL or folder ID.");
                    return "redirect:/settings";
                }
            }
            settingsService.set(SettingsService.KEY_DRIVE_FOLDER, driveFolder != null ? driveFolder : "");
        }

        redirectAttributes.addFlashAttribute("success", "Settings saved");
        return "redirect:/settings";
    }

    @PostMapping("/settings/delete-account")
    public String deleteAccount(RedirectAttributes redirectAttributes) {
        try {
            accountDeletionService.deleteAllUserData();
            redirectAttributes.addFlashAttribute("success",
                    "All account data has been deleted. Your OAuth token has been revoked with Google.");
        } catch (Exception e) {
            log.error("Account deletion failed", e);
            redirectAttributes.addFlashAttribute("error",
                    "Account deletion failed: " + e.getMessage());
        }
        return "redirect:/settings";
    }
}
