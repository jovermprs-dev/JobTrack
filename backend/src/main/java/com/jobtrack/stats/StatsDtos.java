package com.jobtrack.stats;

import com.jobtrack.offer.OfferStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class StatsDtos {

    private StatsDtos() {
    }

    public record Totals(long saved, long applied, long active, long interviews, long offersReceived, long favorites) {
    }

    public record StatusCount(OfferStatus status, long count) {
    }

    public record WeekCount(LocalDate weekStart, long count) {
    }

    /**
     * @param value {@code applied > 0 ? responded / applied : null}, en tanto por uno
     * @param sufficientData {@code applied >= MIN_APPLICATIONS_FOR_RATE}
     */
    public record ResponseRate(Double value, long responded, long applied, boolean sufficientData) {
    }

    public record CompanyCount(String company, long count) {
    }

    public record AverageCompatibility(Double value, long offersScored) {
    }

    public record CompatibleOffer(UUID id, String title, String company, int score) {
    }

    public record Activity(UUID offerId, String title, String company, OfferStatus fromStatus, OfferStatus toStatus,
            String note, Instant changedAt) {
    }

    public record StatsResponse(Totals totals, List<StatusCount> byStatus, List<WeekCount> applicationsOverTime,
            ResponseRate responseRate, List<CompanyCount> topCompanies, AverageCompatibility averageCompatibility,
            List<CompatibleOffer> topCompatible, List<Activity> recentActivity) {
    }
}
