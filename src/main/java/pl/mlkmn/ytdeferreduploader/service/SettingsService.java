package pl.mlkmn.ytdeferreduploader.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mlkmn.ytdeferreduploader.model.AppSetting;
import pl.mlkmn.ytdeferreduploader.repository.AppSettingRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SettingsService {

    public static final String KEY_DEFAULT_DESCRIPTION = "default_description";
    public static final String KEY_DEFAULT_TAGS = "default_tags";
    public static final String KEY_DEFAULT_PRIVACY = "default_privacy";
    public static final String KEY_DEFAULT_CATEGORY = "default_category";
    public static final String KEY_DEFAULT_PLAYLIST = "default_playlist";
    public static final String KEY_OAUTH_ACCESS_TOKEN = "oauth_access_token";
    public static final String KEY_OAUTH_REFRESH_TOKEN = "oauth_refresh_token";
    public static final String KEY_OAUTH_TOKEN_EXPIRY = "oauth_token_expiry_seconds";

    private final AppSettingRepository appSettingRepository;

    public Optional<String> get(String key) {
        return appSettingRepository.findById(key).map(AppSetting::getSettingValue);
    }

    public String getOrDefault(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    public void set(String key, String value) {
        AppSetting setting = appSettingRepository.findById(key)
                .orElse(new AppSetting(key, null));
        setting.setSettingValue(value);
        appSettingRepository.save(setting);
    }

    public void delete(String key) {
        appSettingRepository.deleteById(key);
    }
}
