package pl.mlkmn.ytdeferreduploader.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;
import pl.mlkmn.ytdeferreduploader.config.YouTubeApiConfig.AuthFlowFactory;
import pl.mlkmn.ytdeferreduploader.model.ScopeTier;
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

    @GetMapping("/settings")
    public String showSettings(Model model) {
        model.addAttribute("defaultDescription",
                settingsService.getOrDefault(SettingsService.KEY_DEFAULT_DESCRIPTION, ""));
        model.addAttribute("defaultPrivacy",
                settingsService.getOrDefault(SettingsService.KEY_DEFAULT_PRIVACY, "PRIVATE"));
        String driveFolder = settingsService.getOrDefault(SettingsService.KEY_DRIVE_FOLDER, "");
        model.addAttribute("driveFolder", driveFolder);
        model.addAttribute("resolvedFolderId", GoogleDriveService.extractFolderId(driveFolder));
        boolean youtubeConnected = settingsService.get(SettingsService.KEY_OAUTH_REFRESH_TOKEN).isPresent();
        model.addAttribute("youtubeConnected", youtubeConnected);
        model.addAttribute("defaultPlaylist",
                settingsService.getOrDefault(SettingsService.KEY_DEFAULT_PLAYLIST, ""));

        ScopeTier scopeTier = settingsService.getScopeTier();
        model.addAttribute("scopeTier", scopeTier.name());
        model.addAttribute("canInsertPlaylist", scopeTier.canInsertPlaylist());

        if (youtubeConnected) {
            playlistService.getChannel().ifPresent(ch ->
                    model.addAttribute("channelTitle", ch.getSnippet().getTitle()));
            if (scopeTier.canInsertPlaylist()) {
                model.addAttribute("playlists", playlistService.getUserPlaylists());
            }
        }
        model.addAttribute("quotaExhausted", quotaTracker.isExhausted());

        return "settings";
    }

    private static final List<String> HOSTED_SCOPES = List.of(
            "https://www.googleapis.com/auth/youtube.upload",
            "https://www.googleapis.com/auth/youtube.readonly",
            "https://www.googleapis.com/auth/drive.file"
    );

    @GetMapping("/settings/oauth/connect")
    public String startOAuth() {
        List<String> scopes = appProperties.isHostedMode()
                ? HOSTED_SCOPES
                : settingsService.getScopeTier().getScopes();
        GoogleAuthorizationCodeFlow flow = authFlowFactory.buildFlow(scopes);
        String redirectUri = appProperties.getYoutube().getRedirectUri();
        log.info("OAuth connect initiated: redirectUri={}, hostedMode={}", redirectUri, appProperties.isHostedMode());
        String authUrl = flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .build();
        log.info("Redirecting to Google OAuth: url={}", authUrl);
        return "redirect:" + authUrl;
    }

    @GetMapping("/settings/oauth/callback")
    public String oauthCallback(@RequestParam("code") String code,
                                RedirectAttributes redirectAttributes) {
        List<String> scopes = appProperties.isHostedMode()
                ? HOSTED_SCOPES
                : settingsService.getScopeTier().getScopes();
        GoogleAuthorizationCodeFlow flow = authFlowFactory.buildFlow(scopes);
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
        } catch (Exception e) {
            log.error("OAuth callback failed: redirectUri={}, error={}", redirectUri, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "OAuth failed: " + e.getMessage());
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

    @PostMapping("/settings/scope-tier")
    public String saveScopeTier(@RequestParam("scopeTier") String scopeTier,
                                RedirectAttributes redirectAttributes) {
        try {
            ScopeTier tier = ScopeTier.valueOf(scopeTier);
            ScopeTier currentTier = settingsService.getScopeTier();
            settingsService.set(SettingsService.KEY_SCOPE_TIER, tier.name());

            boolean isConnected = settingsService.get(SettingsService.KEY_OAUTH_REFRESH_TOKEN).isPresent();
            if (isConnected && tier != currentTier) {
                redirectAttributes.addFlashAttribute("warning",
                        "Scope tier changed. Please disconnect and reconnect your YouTube account for the new permissions to take effect.");
            } else {
                redirectAttributes.addFlashAttribute("success", "Scope tier saved");
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "Invalid scope tier");
        }
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
                               @RequestParam(value = "driveFolder", required = false) String driveFolder,
                               RedirectAttributes redirectAttributes) {
        settingsService.set(SettingsService.KEY_DEFAULT_DESCRIPTION, defaultDescription);
        settingsService.set(SettingsService.KEY_DEFAULT_PRIVACY, defaultPrivacy);
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
