package pl.mlkmn.ytdeferreduploader.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.mlkmn.ytdeferreduploader.config.AppProperties;
import pl.mlkmn.ytdeferreduploader.model.QuotaLog;
import pl.mlkmn.ytdeferreduploader.repository.QuotaLogRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotaTrackerTest {

    @Mock
    private QuotaLogRepository quotaLogRepository;

    private AppProperties appProperties;
    private QuotaTracker quotaTracker;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getYoutube().setDailyQuotaLimit(10000);
        appProperties.getYoutube().setUploadCostUnits(1600);
        appProperties.getYoutube().setQuotaResetTimezone("Europe/Warsaw");
        quotaTracker = new QuotaTracker(quotaLogRepository, appProperties);
    }

    @Test
    void getUnitsUsedToday_noLogEntry_returnsZero() {
        when(quotaLogRepository.findById(any(LocalDate.class))).thenReturn(Optional.empty());

        assertEquals(0, quotaTracker.getUnitsUsedToday());
    }

    @Test
    void getUnitsUsedToday_withLogEntry_returnsUnits() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Warsaw"));
        QuotaLog log = new QuotaLog(today);
        log.setUnitsUsed(3200);
        when(quotaLogRepository.findById(today)).thenReturn(Optional.of(log));

        assertEquals(3200, quotaTracker.getUnitsUsedToday());
    }

    @Test
    void getRemainingUnits_noUsage_returnsFullQuota() {
        when(quotaLogRepository.findById(any(LocalDate.class))).thenReturn(Optional.empty());

        assertEquals(10000, quotaTracker.getRemainingUnits());
    }

    @Test
    void getRemainingUnits_withUsage_returnsRemaining() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Warsaw"));
        QuotaLog log = new QuotaLog(today);
        log.setUnitsUsed(4800);
        when(quotaLogRepository.findById(today)).thenReturn(Optional.of(log));

        assertEquals(5200, quotaTracker.getRemainingUnits());
    }

    @Test
    void canUpload_enoughQuota_returnsTrue() {
        when(quotaLogRepository.findById(any(LocalDate.class))).thenReturn(Optional.empty());

        assertTrue(quotaTracker.canUpload());
    }

    @Test
    void canUpload_notEnoughQuota_returnsFalse() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Warsaw"));
        QuotaLog log = new QuotaLog(today);
        log.setUnitsUsed(9000);
        when(quotaLogRepository.findById(today)).thenReturn(Optional.of(log));

        assertFalse(quotaTracker.canUpload());
    }

    @Test
    void canUpload_exactlyEnoughQuota_returnsTrue() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Warsaw"));
        QuotaLog log = new QuotaLog(today);
        log.setUnitsUsed(8400); // 10000 - 8400 = 1600 = exactly upload cost
        when(quotaLogRepository.findById(today)).thenReturn(Optional.of(log));

        assertTrue(quotaTracker.canUpload());
    }

    @Test
    void recordUsage_noExistingEntry_createsNew() {
        when(quotaLogRepository.findById(any(LocalDate.class))).thenReturn(Optional.empty());
        when(quotaLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        quotaTracker.recordUsage(1600);

        ArgumentCaptor<QuotaLog> captor = ArgumentCaptor.forClass(QuotaLog.class);
        verify(quotaLogRepository).save(captor.capture());
        assertEquals(1600, captor.getValue().getUnitsUsed());
    }

    @Test
    void recordUsage_existingEntry_addsToExisting() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Warsaw"));
        QuotaLog log = new QuotaLog(today);
        log.setUnitsUsed(3200);
        when(quotaLogRepository.findById(today)).thenReturn(Optional.of(log));
        when(quotaLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        quotaTracker.recordUsage(1600);

        ArgumentCaptor<QuotaLog> captor = ArgumentCaptor.forClass(QuotaLog.class);
        verify(quotaLogRepository).save(captor.capture());
        assertEquals(4800, captor.getValue().getUnitsUsed());
    }

    @Test
    void recordUpload_usesConfiguredCost() {
        when(quotaLogRepository.findById(any(LocalDate.class))).thenReturn(Optional.empty());
        when(quotaLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        quotaTracker.recordUpload();

        ArgumentCaptor<QuotaLog> captor = ArgumentCaptor.forClass(QuotaLog.class);
        verify(quotaLogRepository).save(captor.capture());
        assertEquals(1600, captor.getValue().getUnitsUsed());
    }
}
