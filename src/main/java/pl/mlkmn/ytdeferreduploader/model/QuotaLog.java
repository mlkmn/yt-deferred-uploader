package pl.mlkmn.ytdeferreduploader.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "quota_log")
@Getter
@Setter
@NoArgsConstructor
public class QuotaLog {

    @Id
    @Column(name = "log_date")
    private LocalDate logDate;

    @Column(name = "units_used")
    private int unitsUsed = 0;

    public QuotaLog(LocalDate logDate) {
        this.logDate = logDate;
    }
}
