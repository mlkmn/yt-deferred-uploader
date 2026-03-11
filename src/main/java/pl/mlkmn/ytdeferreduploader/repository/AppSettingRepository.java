package pl.mlkmn.ytdeferreduploader.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.mlkmn.ytdeferreduploader.model.AppSetting;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
