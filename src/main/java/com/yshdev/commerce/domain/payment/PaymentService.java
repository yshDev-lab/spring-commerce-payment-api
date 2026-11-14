package com.yshdev.commerce.domain.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Value("${pg.fee-rate:0.033}")
    private BigDecimal feeRate;

    /**
     * 멱등성 키(Idempotency-Key) 기반 결제 승인 처리.
     * 동일한 Idempotency-Key로 재요청 시 중복 PG 호출 없이 기존 승인 결과를 즉시 반환합니다.
     */
    @Transactional
    public Payment confirmPayment(Long orderId, Long sellerId, BigDecimal amount, String idempotencyKey) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> {
                    Payment payment = Payment.builder()
                            .orderId(orderId)
                            .sellerId(sellerId)
                            .amount(amount)
                            .idempotencyKey(idempotencyKey)
                            .build();
                    String pgTxId = "PG_TX_" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
                    payment.markApproved(pgTxId);
                    return paymentRepository.save(payment);
                });
    }

    /**
     * 비관적 락(PESSIMISTIC_WRITE)을 통한 동시 취소 경쟁 상태 방지
     */
    @Transactional
    public Payment cancelPayment(Long paymentId) {
        Payment payment = paymentRepository.findByIdWithPessimisticLock(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("결제 내역을 찾을 수 없습니다. ID=" + paymentId));
        payment.markCanceled();
        return payment;
    }

    /**
     * 판매자별 일일 정산 금액 집계 (총 승인 금액, PG 수수료, 최종 정산 예정 금액)
     */
    @Transactional(readOnly = true)
    public SettlementSummaryDto calculateDailySettlement(Long sellerId, LocalDate targetDate) {
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = targetDate.atTime(LocalTime.MAX);

        List<Payment> payments = paymentRepository.findApprovedPaymentsForSettlement(sellerId, start, end);

        BigDecimal grossAmount = payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal feeAmount = grossAmount.multiply(feeRate).setScale(0, RoundingMode.FLOOR);
        BigDecimal netSettlementAmount = grossAmount.subtract(feeAmount);

        return new SettlementSummaryDto(
                sellerId,
                targetDate,
                payments.size(),
                grossAmount,
                feeAmount,
                netSettlementAmount
        );
    }

    public record SettlementSummaryDto(
            Long sellerId,
            LocalDate settlementDate,
            int totalTransactionCount,
            BigDecimal grossAmount,
            BigDecimal feeAmount,
            BigDecimal netSettlementAmount
    ) {}
}
