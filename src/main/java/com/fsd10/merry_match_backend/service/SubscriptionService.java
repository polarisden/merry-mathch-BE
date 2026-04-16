package com.fsd10.merry_match_backend.service;

import com.fsd10.merry_match_backend.dto.SubscriptionResponse;
import com.fsd10.merry_match_backend.dto.plan.PlanDescriptionDto;
import com.fsd10.merry_match_backend.dto.plan.PlanDto;
import com.fsd10.merry_match_backend.entity.Subscription;
import com.fsd10.merry_match_backend.entity.Plans;
import com.fsd10.merry_match_backend.repository.PlansRepository;
import com.fsd10.merry_match_backend.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlansRepository plansRepository;

    @Transactional(readOnly = true)
    public SubscriptionResponse getByUserId(UUID userId) {
        Subscription sub = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));

        Plans plan = plansRepository.findWithDescriptionsById(sub.getPlanId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found"));

        return toResponse(sub, plan);
    }

    private static SubscriptionResponse toResponse(Subscription s, Plans p) {
        List<PlanDescriptionDto> descriptions = p.getDescriptions() == null ? List.of() :
                p.getDescriptions().stream()
                        .map(d -> PlanDescriptionDto.builder()
                                .id(d.getId())
                                .description(d.getDescription())
                                .sortOrder(d.getSortOrder())
                                .build())
                        .toList();

        PlanDto planDto = PlanDto.builder()
                .id(p.getId())
                .name(p.getName())
                .priceSatang(p.getPriceSatang())
                .swipeLimit(p.getSwipeLimit())
                .canSeeLikers(p.getCanSeeLikers())
                .sortOrder(p.getSortOrder())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .descriptions(descriptions)
                .build();

        return new SubscriptionResponse(
                s.getId(),
                s.getUserId(),
                planDto,
                s.getStatus(),
                s.getCurrentPeriodStart(),
                s.getCurrentPeriodEnd(),
                s.getCancelAt(),
                s.getCancelledAt(),
                s.getAutoRenew(),
                s.getCardBrand(),
                s.getCardExpirationMonth(),
                s.getCardExpirationYear(),
                s.getCardLastDigits(),
                s.getScheduledPlanChangeAt(),
                s.getPendingPlanId(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
