package pl.mlkmn.ytdeferreduploader.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotaTrackerTest {

    @Mock
    private SettingsService settingsService;

    private QuotaTracker quotaTracker;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.getYoutube().setQuotaResetTimezone("Europe/Warsaw");
        quotaTracker = new QuotaTracker(settingsService, appProperties);
    }

    @Test
    void isExhausted_noEntry_returnsFalse() {
        when(settingsService.get("quota_exhausted_date")).thenReturn(Optional.empty());

        assertFalse(quotaTracker.isExhausted());
    }

    @Test
    void isExhausted_todayEntry_returnsTrue() {
        String today = LocalDate.now(ZoneId.of("Europe/Warsaw")).toString();
        when(settingsService.get("quota_exhausted_date")).thenReturn(Optional.of(today));

        assertTrue(quotaTracker.isExhausted());
    }

    @Test
    void isExhausted_yesterdayEntry_returnsFalse() {
        String yesterday = LocalDate.now(ZoneId.of("Europe/Warsaw")).minusDays(1).toString();
        when(settingsService.get("quota_exhausted_date")).thenReturn(Optional.of(yesterday));

        assertFalse(quotaTracker.isExhausted());
    }

    @Test
    void markExhausted_storesTodayDate() {
        quotaTracker.markExhausted();

        String today = LocalDate.now(ZoneId.of("Europe/Warsaw")).toString();
        verify(settingsService).set("quota_exhausted_date", today);
    }

    @Test
    void reset_deletesEntry() {
        quotaTracker.reset();

        verify(settingsService).delete("quota_exhausted_date");
    }

    @Test
    void isExhausted_afterReset_returnsFalse() {
        when(settingsService.get("quota_exhausted_date")).thenReturn(Optional.empty());

        quotaTracker.reset();
        assertFalse(quotaTracker.isExhausted());
    }
}
