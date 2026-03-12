package pl.mlkmn.ytdeferreduploader.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;
import pl.mlkmn.ytdeferreduploader.model.QuotaLog;
import pl.mlkmn.ytdeferreduploader.repository.QuotaLogRepository;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class QuotaTracker {

    private static final Logger log = LoggerFactory.getLogger(QuotaTracker.class);

    private final QuotaLogRepository quotaLogRepository;
    private final AppProperties appProperties;

    public QuotaTracker(QuotaLogRepository quotaLogRepository, AppProperties appProperties) {
        this.quotaLogRepository = quotaLogRepository;
        this.appProperties = appProperties;
    }

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
        log.info("Recorded {} quota units for {}. Total used today: {}", units, today, quotaLog.getUnitsUsed());
    }

    public void recordUpload() {
        recordUsage(appProperties.getYoutube().getUploadCostUnits());
    }

    private LocalDate todayInQuotaZone() {
        return LocalDate.now(ZoneId.of(appProperties.getYoutube().getQuotaResetTimezone()));
    }
}
