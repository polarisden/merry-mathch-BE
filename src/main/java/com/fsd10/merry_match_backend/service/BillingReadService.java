package com.fsd10.merry_match_backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fsd10.merry_match_backend.dto.billing.BillingHistoryItemDto;
import com.fsd10.merry_match_backend.entity.BillingRecord;
import com.fsd10.merry_match_backend.repository.BillingRecordRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BillingReadService {

    private final BillingRecordRepository billingRecordRepository;

    @Transactional(readOnly = true)
    public List<BillingHistoryItemDto> listHistoryForUser(UUID userId) {
        return billingRecordRepository
                .findByUserIdAndStatusOrderByBilledAtDesc(userId, BillingRecord.BillingStatus.PAID)
                .stream()
                .map(this::toItem)
                .toList();
    }

    private BillingHistoryItemDto toItem(BillingRecord br) {
        var billedAt = br.getPaidAt() != null ? br.getPaidAt() : br.getCreatedAt();
        return BillingHistoryItemDto.builder()
                .id(br.getId())
                .billedAt(billedAt)
                .planName(br.getPlan().getName())
                .amountSatang(br.getAmountSatang())
                .status(br.getStatus().name())
                .build();
    }
}
