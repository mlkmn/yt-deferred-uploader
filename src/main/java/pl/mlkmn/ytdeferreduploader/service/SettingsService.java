package pl.mlkmn.ytdeferreduploader.service;

import org.springframework.stereotype.Service;
import pl.mlkmn.ytdeferreduploader.model.AppSetting;
import pl.mlkmn.ytdeferreduploader.repository.AppSettingRepository;

import java.util.Optional;

@Service
public class SettingsService {

    public static final String KEY_DEFAULT_DESCRIPTION = "default_description";
    public static final String KEY_DEFAULT_TAGS = "default_tags";
    public static final String KEY_DEFAULT_PRIVACY = "default_privacy";
    public static final String KEY_DEFAULT_CATEGORY = "default_category";

    private final AppSettingRepository appSettingRepository;

    public SettingsService(AppSettingRepository appSettingRepository) {
        this.appSettingRepository = appSettingRepository;
    }

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
}
