package com.fsd10.merry_match_backend.service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.fsd10.merry_match_backend.dto.plan.PlanDescriptionDto;
import com.fsd10.merry_match_backend.dto.plan.PlanDto;
import com.fsd10.merry_match_backend.entity.PlanDescription;
import com.fsd10.merry_match_backend.entity.Plans;
import com.fsd10.merry_match_backend.exception.PlanNotFoundException;
import com.fsd10.merry_match_backend.repository.PlansRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlansService {

    private final PlansRepository plansRepository;

    public List<PlanDto> getAllPlans() {
        var plans = plansRepository.findAll(Sort.by(Sort.Direction.ASC, "sortOrder"));
        return plans.stream().map(this::toDto).toList();
    }

    public PlanDto getPlanById(UUID id) {
        var plan = plansRepository.findWithDescriptionsById(id)
                .orElseThrow(PlanNotFoundException::new);
        return toDto(plan);
    }

    private PlanDto toDto(Plans plan) {
        var descriptions = (plan.getDescriptions() == null ? List.<PlanDescription>of() : plan.getDescriptions())
                .stream()
                .sorted(Comparator.comparing(d -> d.getSortOrder() == null ? Integer.MAX_VALUE : d.getSortOrder()))
                .map(this::toDto)
                .toList();

        return PlanDto.builder()
                .id(plan.getId())
                .name(plan.getName())
                .priceSatang(plan.getPriceSatang())
                .swipeLimit(plan.getSwipeLimit())
                .canSeeLikers(plan.getCanSeeLikers())
                .sortOrder(plan.getSortOrder())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .descriptions(descriptions)
                .build();
    }

    private PlanDescriptionDto toDto(PlanDescription d) {
        return PlanDescriptionDto.builder()
                .id(d.getId())
                .description(d.getDescription())
                .sortOrder(d.getSortOrder())
                .build();
    }
}

