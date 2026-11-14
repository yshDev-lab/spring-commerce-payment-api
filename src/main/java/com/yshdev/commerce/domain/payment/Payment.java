package com.yshdev.commerce.domain.payment;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "uk_idempotency_key", columnList = "idempotencyKey", unique = true),
        @Index(name = "idx_seller_approved_at", columnList = "sellerId, approvedAt")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(length = 80)
    private String pgTransactionId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    private LocalDateTime approvedAt;
    private LocalDateTime canceledAt;

    @Builder
    public Payment(Long orderId, Long sellerId, String idempotencyKey, BigDecimal amount) {
        this.orderId = orderId;
        this.sellerId = sellerId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.status = PaymentStatus.READY;
    }

    public void markApproved(String pgTransactionId) {
        if (this.status == PaymentStatus.APPROVED) {
            return; // 멱등성 보장
        }
        if (this.status == PaymentStatus.CANCELED) {
            throw new IllegalStateException("이미 취소된 결제는 승인할 수 없습니다.");
        }
        this.pgTransactionId = pgTransactionId;
        this.status = PaymentStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
    }

    public void markCanceled() {
        if (this.status != PaymentStatus.APPROVED) {
            throw new IllegalStateException("승인 상태의 결제만 취소할 수 있습니다.");
        }
        this.status = PaymentStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    public enum PaymentStatus {
        READY, APPROVED, CANCELED, FAILED
    }
}
