package com.jobtrack.stats;

import com.jobtrack.offer.JobOfferRepository;
import com.jobtrack.offer.OfferStatus;
import com.jobtrack.offer.StatusHistoryRepository;
import com.jobtrack.stats.StatsDtos.Activity;
import com.jobtrack.stats.StatsDtos.AverageCompatibility;
import com.jobtrack.stats.StatsDtos.CompanyCount;
import com.jobtrack.stats.StatsDtos.CompatibleOffer;
import com.jobtrack.stats.StatsDtos.ResponseRate;
import com.jobtrack.stats.StatsDtos.StatsResponse;
import com.jobtrack.stats.StatsDtos.StatusCount;
import com.jobtrack.stats.StatsDtos.Totals;
import com.jobtrack.stats.StatsDtos.WeekCount;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatsService {

    static final int WEEKS = 12;
    /** Por debajo de este número de candidaturas la tasa de respuesta se marca como poco fiable. */
    static final int MIN_APPLICATIONS_FOR_RATE = 5;

    private static final Set<OfferStatus> ACTIVE = Set.of(OfferStatus.APPLIED, OfferStatus.IN_PROCESS, OfferStatus.INTERVIEW);
    private static final Set<OfferStatus> RESPONSE = Set.of(OfferStatus.IN_PROCESS, OfferStatus.INTERVIEW,
            OfferStatus.OFFER_RECEIVED, OfferStatus.REJECTED);

    private final JobOfferRepository offers;
    private final StatusHistoryRepository history;
    private final Clock clock;

    public StatsService(JobOfferRepository offers, StatusHistoryRepository history, Clock clock) {
        this.offers = offers;
        this.history = history;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StatsResponse compute(UUID userId) {
        long applied = offers.countByUserIdAndAppliedDateIsNotNull(userId);

        Map<OfferStatus, Long> counts = new EnumMap<>(OfferStatus.class);
        for (Object[] row : offers.countByStatus(userId)) {
            counts.put((OfferStatus) row[0], (Long) row[1]);
        }
        List<StatusCount> byStatus = new ArrayList<>();
        for (OfferStatus s : OfferStatus.values()) {
            byStatus.add(new StatusCount(s, counts.getOrDefault(s, 0L)));
        }

        Totals totals = new Totals(
                offers.countByUserId(userId),
                applied,
                offers.countByUserIdAndStatusIn(userId, ACTIVE),
                history.countOffersEverIn(userId, OfferStatus.INTERVIEW),
                history.countOffersEverIn(userId, OfferStatus.OFFER_RECEIVED),
                offers.countByUserIdAndFavoriteTrue(userId));

        long responded = history.countAppliedOffersEverIn(userId, RESPONSE);
        ResponseRate rate = new ResponseRate(applied > 0 ? (double) responded / applied : null, responded, applied,
                applied >= MIN_APPLICATIONS_FOR_RATE);

        List<CompanyCount> topCompanies = offers.topCompanies(userId, PageRequest.of(0, 5)).stream()
                .map(r -> new CompanyCount((String) r[0], (Long) r[1])).toList();

        Object[] avg = offers.compatibilityAverage(userId).get(0);
        long scored = avg[1] == null ? 0 : (Long) avg[1];
        AverageCompatibility averageCompatibility = new AverageCompatibility(
                scored > 0 ? Math.round(((Number) avg[0]).doubleValue() * 10) / 10.0 : null, scored);

        List<CompatibleOffer> topCompatible = offers
                .findTop5ByUserIdAndCompatibilityScoreIsNotNullOrderByCompatibilityScoreDescUpdatedAtDesc(userId).stream()
                .map(o -> new CompatibleOffer(o.getId(), o.getTitle(), o.getCompany(), o.getCompatibilityScore())).toList();

        List<Activity> recent = history.recentActivity(userId, PageRequest.of(0, 10)).stream()
                .map(a -> new Activity(a.offerId(), a.title(), a.company(), a.fromStatus(), a.toStatus(), a.note(), a.changedAt()))
                .toList();

        return new StatsResponse(totals, byStatus, weekly(userId), rate, topCompanies, averageCompatibility, topCompatible, recent);
    }

    /** Candidaturas por semana (lunes) durante las últimas {@value #WEEKS} semanas, rellenando con 0. */
    private List<WeekCount> weekly(UUID userId) {
        LocalDate currentWeek = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate first = currentWeek.minusWeeks(WEEKS - 1L);
        Map<LocalDate, Long> buckets = new java.util.LinkedHashMap<>();
        for (int i = 0; i < WEEKS; i++) {
            buckets.put(first.plusWeeks(i), 0L);
        }
        for (LocalDate date : offers.appliedDatesSince(userId, first)) {
            LocalDate week = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            buckets.computeIfPresent(week, (k, v) -> v + 1);
        }
        return buckets.entrySet().stream().map(e -> new WeekCount(e.getKey(), e.getValue())).toList();
    }
}
