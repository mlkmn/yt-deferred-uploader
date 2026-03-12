package pl.mlkmn.ytdeferreduploader.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaTracker {

    private static final String KEY_QUOTA_EXHAUSTED_DATE = "quota_exhausted_date";

    private final SettingsService settingsService;
    private final AppProperties appProperties;

    public boolean isExhausted() {
        return settingsService.get(KEY_QUOTA_EXHAUSTED_DATE)
                .map(LocalDate::parse)
                .filter(date -> date.equals(todayInQuotaZone()))
                .isPresent();
    }

    public void markExhausted() {
        LocalDate today = todayInQuotaZone();
        settingsService.set(KEY_QUOTA_EXHAUSTED_DATE, today.toString());
        log.info("Quota marked as exhausted for date={}", today);
    }

    public void reset() {
        settingsService.delete(KEY_QUOTA_EXHAUSTED_DATE);
        log.info("Quota reset manually");
    }

    private LocalDate todayInQuotaZone() {
        return LocalDate.now(ZoneId.of(appProperties.getYoutube().getQuotaResetTimezone()));
    }
}
