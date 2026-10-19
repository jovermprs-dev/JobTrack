package com.jobtrack.offer;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, UUID> {

    List<StatusHistory> findByOfferIdAndUserIdOrderByChangedAtDescIdDesc(UUID offerId, UUID userId);

    @Query("select count(distinct h.offerId) from StatusHistory h where h.userId = :userId and h.toStatus = :status")
    long countOffersEverIn(@Param("userId") UUID userId, @Param("status") OfferStatus status);

    @Query("""
            select count(distinct h.offerId) from StatusHistory h join JobOffer o on o.id = h.offerId
            where h.userId = :userId and h.toStatus in :statuses and o.appliedDate is not null""")
    long countAppliedOffersEverIn(@Param("userId") UUID userId, @Param("statuses") Collection<OfferStatus> statuses);

    record ActivityRow(UUID offerId, String title, String company, OfferStatus fromStatus, OfferStatus toStatus,
            String note, Instant changedAt) {
    }

    @Query("""
            select new com.jobtrack.offer.StatusHistoryRepository$ActivityRow(
                h.offerId, o.title, o.company, h.fromStatus, h.toStatus, h.note, h.changedAt)
            from StatusHistory h join JobOffer o on o.id = h.offerId
            where h.userId = :userId
            order by h.changedAt desc, h.id desc""")
    List<ActivityRow> recentActivity(@Param("userId") UUID userId, Pageable pageable);
}
