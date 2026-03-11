package pl.mlkmn.ytdeferreduploader.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "quota_log")
public class QuotaLog {

    @Id
    @Column(name = "log_date")
    private LocalDate logDate;

    @Column(name = "units_used")
    private int unitsUsed = 0;

    public QuotaLog() {
    }

    public QuotaLog(LocalDate logDate) {
        this.logDate = logDate;
    }

    public LocalDate getLogDate() {
        return logDate;
    }

    public void setLogDate(LocalDate logDate) {
        this.logDate = logDate;
    }

    public int getUnitsUsed() {
        return unitsUsed;
    }

    public void setUnitsUsed(int unitsUsed) {
        this.unitsUsed = unitsUsed;
    }
}
