package com.yshdev.commerce.domain.payment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Tag(name = "Payment & Settlement API", description = "주문 결제 승인/취소 및 일별 판매자 정산 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "결제 승인 요청 (멱등성 보장)", description = "Idempotency-Key 헤더를 통해 중복 결제를 원천 차단합니다.")
    @PostMapping("/payments/confirm")
    public ResponseEntity<Payment> confirmPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ConfirmRequest request
    ) {
        Payment payment = paymentService.confirmPayment(
                request.orderId(),
                request.sellerId(),
                request.amount(),
                idempotencyKey
        );
        return ResponseEntity.ok(payment);
    }

    @Operation(summary = "결제 취소 요청 (비관적 락 적용)")
    @PostMapping("/payments/{paymentId}/cancel")
    public ResponseEntity<Payment> cancelPayment(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.cancelPayment(paymentId));
    }

    @Operation(summary = "판매자별 일일 정산 내역 조회")
    @GetMapping("/settlements/daily")
    public ResponseEntity<PaymentService.SettlementSummaryDto> getDailySettlement(
            @RequestParam Long sellerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(paymentService.calculateDailySettlement(sellerId, date));
    }

    public record ConfirmRequest(
            @NotNull Long orderId,
            @NotNull Long sellerId,
            @NotNull @Positive BigDecimal amount
    ) {}
}
