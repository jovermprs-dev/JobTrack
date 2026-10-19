package com.jobtrack.offer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Registro inmutable de un cambio de estado. Solo lo crea el servicio; el cliente nunca aporta estos datos. */
@Entity
@Table(name = "status_history")
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "offer_id", nullable = false, updatable = false)
    private UUID offerId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false, length = 24)
    private OfferStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false, length = 24)
    private OfferStatus toStatus;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Column(length = 1000, updatable = false)
    private String note;

    protected StatusHistory() {
    }

    public StatusHistory(UUID offerId, UUID userId, OfferStatus fromStatus, OfferStatus toStatus, Instant changedAt, String note) {
        this.offerId = offerId;
        this.userId = userId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = changedAt;
        this.note = note;
    }

    public UUID getId() { return id; }
    public UUID getOfferId() { return offerId; }
    public UUID getUserId() { return userId; }
    public OfferStatus getFromStatus() { return fromStatus; }
    public OfferStatus getToStatus() { return toStatus; }
    public Instant getChangedAt() { return changedAt; }
    public String getNote() { return note; }
}
