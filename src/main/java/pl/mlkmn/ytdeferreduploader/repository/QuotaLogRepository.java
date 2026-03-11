package pl.mlkmn.ytdeferreduploader.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.mlkmn.ytdeferreduploader.model.QuotaLog;

import java.time.LocalDate;

public interface QuotaLogRepository extends JpaRepository<QuotaLog, LocalDate> {
}
