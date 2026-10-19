package com.jobtrack.offer;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobOfferRepository extends JpaRepository<JobOffer, UUID>, JpaSpecificationExecutor<JobOffer> {

    Optional<JobOffer> findByIdAndUserId(UUID id, UUID userId);

    List<JobOffer> findAllByUserIdOrderByDetectedDateDescCreatedAtDesc(UUID userId);

    long countByUserId(UUID userId);

    long countByUserIdAndFavoriteTrue(UUID userId);

    long countByUserIdAndAppliedDateIsNotNull(UUID userId);

    long countByUserIdAndStatusIn(UUID userId, Collection<OfferStatus> statuses);

    @Query("select o.status, count(o) from JobOffer o where o.userId = :userId group by o.status")
    List<Object[]> countByStatus(@Param("userId") UUID userId);

    @Query("select o.appliedDate from JobOffer o where o.userId = :userId and o.appliedDate >= :from")
    List<LocalDate> appliedDatesSince(@Param("userId") UUID userId, @Param("from") LocalDate from);

    @Query("""
            select min(o.company), count(o) from JobOffer o
            where o.userId = :userId and o.appliedDate is not null
            group by lower(o.company)
            order by count(o) desc, lower(o.company)""")
    List<Object[]> topCompanies(@Param("userId") UUID userId, Pageable pageable);

    @Query("select avg(o.compatibilityScore), count(o.compatibilityScore) from JobOffer o where o.userId = :userId")
    List<Object[]> compatibilityAverage(@Param("userId") UUID userId);

    List<JobOffer> findTop5ByUserIdAndCompatibilityScoreIsNotNullOrderByCompatibilityScoreDescUpdatedAtDesc(UUID userId);
}
