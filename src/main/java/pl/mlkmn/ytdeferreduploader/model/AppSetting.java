package pl.mlkmn.ytdeferreduploader.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.mlkmn.ytdeferreduploader.crypto.EncryptedStringConverter;

@Entity
@Table(name = "app_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 100)
    private String settingKey;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String settingValue;
}
