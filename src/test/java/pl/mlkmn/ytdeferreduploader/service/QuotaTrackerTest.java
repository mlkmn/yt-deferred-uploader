package pl.mlkmn.ytdeferreduploader.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaTrackerTest {

    private static final String KEY = "quota_exhausted_date";

    @Mock
    private SettingsService settingsService;

    @Test
    void defaultZone_isPacificTime_inPropertiesAndApplicationYml() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));

        assertEquals("America/Los_Angeles", new AppProperties().getYoutube().getQuotaResetTimezone());
        assertEquals("America/Los_Angeles", yaml.getObject().getProperty("app.youtube.quota-reset-timezone"));
    }

    @Test
    void nextReset_winter_isMidnightPst() {
        assertEquals(Instant.parse("2026-11-07T08:00:00Z"), trackerAt("2026-11-06T10:00:00Z").nextReset());
    }

    @Test
    void nextReset_summer_isMidnightPdt() {
        assertEquals(Instant.parse("2026-07-02T07:00:00Z"), trackerAt("2026-07-01T10:00:00Z").nextReset());
    }

    @Test
    void nextReset_oneMinuteBeforeMidnightPt_isThatMidnight() {
        assertEquals(Instant.parse("2026-11-07T08:00:00Z"), trackerAt("2026-11-07T07:59:00Z").nextReset());
    }

    @Test
    void nextReset_aroundDstEnd_followsTheOffsetOfEachMidnight() {
        // Pacific DST ends on 2026-11-01 at 02:00 PDT.
        assertEquals(Instant.parse("2026-11-01T07:00:00Z"), trackerAt("2026-11-01T06:00:00Z").nextReset());
        assertEquals(Instant.parse("2026-11-02T08:00:00Z"), trackerAt("2026-11-01T12:00:00Z").nextReset());
    }

    @Test
    void markExhausted_storesTheDateInPacificTime() {
        // 23:30 PST on 2026-11-06, already 2026-11-07 in UTC and in Europe.
        trackerAt("2026-11-07T07:30:00Z").markExhausted();

        verify(settingsService).set(KEY, "2026-11-06");
    }

    @Test
    void exhaustedAt2330Pt_staysExhaustedUntilMidnightPt_thenClears() {
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        trackerAt("2026-11-07T07:30:00Z").markExhausted();
        verify(settingsService).set(eq(KEY), stored.capture());
        when(settingsService.get(KEY)).thenReturn(Optional.of(stored.getValue()));

        assertTrue(trackerAt("2026-11-07T07:59:00Z").isExhausted());
        assertFalse(trackerAt("2026-11-07T08:01:00Z").isExhausted());
    }

    @Test
    void isExhausted_noEntry_returnsFalse() {
        when(settingsService.get(KEY)).thenReturn(Optional.empty());

        assertFalse(trackerAt("2026-11-06T10:00:00Z").isExhausted());
    }

    @Test
    void isExhausted_dateWrittenUnderOldZone_isAtMostOneDayOff_andExpiresByItself() {
        // Stored under Europe/Warsaw shortly after its midnight, while it was still the previous day in PT.
        when(settingsService.get(KEY)).thenReturn(Optional.of("2026-11-07"));

        assertFalse(trackerAt("2026-11-07T00:30:00Z").isExhausted());
        assertTrue(trackerAt("2026-11-07T09:00:00Z").isExhausted());
        assertFalse(trackerAt("2026-11-08T08:00:00Z").isExhausted());
    }

    @Test
    void reset_deletesEntry() {
        trackerAt("2026-11-06T10:00:00Z").reset();

        verify(settingsService).delete(KEY);
    }

    private QuotaTracker trackerAt(String instant) {
        AppProperties appProperties = new AppProperties();
        return new QuotaTracker(settingsService, appProperties, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
}
