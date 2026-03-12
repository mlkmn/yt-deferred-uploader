package pl.mlkmn.ytdeferreduploader.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;
import pl.mlkmn.ytdeferreduploader.model.QuotaLog;
import pl.mlkmn.ytdeferreduploader.repository.QuotaLogRepository;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaTracker {

    private final QuotaLogRepository quotaLogRepository;
    private final AppProperties appProperties;

    public int getUnitsUsedToday() {
        LocalDate today = todayInQuotaZone();
        return quotaLogRepository.findById(today)
                .map(QuotaLog::getUnitsUsed)
                .orElse(0);
    }

    public int getRemainingUnits() {
        return appProperties.getYoutube().getDailyQuotaLimit() - getUnitsUsedToday();
    }

    public boolean hasQuotaFor(int units) {
        return getRemainingUnits() >= units;
    }

    public boolean canUpload() {
        return hasQuotaFor(appProperties.getYoutube().getUploadCostUnits());
    }

    public void recordUsage(int units) {
        LocalDate today = todayInQuotaZone();
        QuotaLog quotaLog = quotaLogRepository.findById(today)
                .orElse(new QuotaLog(today));
        quotaLog.setUnitsUsed(quotaLog.getUnitsUsed() + units);
        quotaLogRepository.save(quotaLog);
        int remaining = appProperties.getYoutube().getDailyQuotaLimit() - quotaLog.getUnitsUsed();
        log.info("Quota usage recorded: units={}, date={}, totalUsed={}, remaining={}",
                units, today, quotaLog.getUnitsUsed(), remaining);
    }

    public void recordUpload() {
        recordUsage(appProperties.getYoutube().getUploadCostUnits());
    }

    private LocalDate todayInQuotaZone() {
        return LocalDate.now(ZoneId.of(appProperties.getYoutube().getQuotaResetTimezone()));
    }
}
