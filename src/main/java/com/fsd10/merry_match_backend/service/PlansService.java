package com.fsd10.merry_match_backend.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fsd10.merry_match_backend.dto.plan.PlanDescriptionDto;
import com.fsd10.merry_match_backend.dto.plan.PlanDescriptionUpsertRequest;
import com.fsd10.merry_match_backend.dto.plan.PlanDto;
import com.fsd10.merry_match_backend.dto.plan.PlanUpsertRequest;
import com.fsd10.merry_match_backend.entity.PlanDescription;
import com.fsd10.merry_match_backend.entity.Plans;
import com.fsd10.merry_match_backend.exception.PlanNotFoundException;
import com.fsd10.merry_match_backend.repository.PlansRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlansService {

    private final PlansRepository plansRepository;
    private final ProfileImageService profileImageService;

    @Transactional(readOnly = true)
    public List<PlanDto> getAllPlans() {
        var plans = plansRepository.findAll(Sort.by(Sort.Direction.ASC, "sortOrder"));
        return plans.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PlanDto getPlanById(UUID id) {
        var plan = plansRepository.findWithDescriptionsById(id)
                .orElseThrow(PlanNotFoundException::new);
        return toDto(plan);
    }

    @Transactional
    public PlanDto createPlan(PlanUpsertRequest request, MultipartFile icon) {
        validateRequest(request);

        Plans plan = Plans.builder()
                .name(request.name().trim())
                .priceSatang(defaultIfNull(request.priceSatang(), 0))
                .swipeLimit(request.merryLimit())
                .canSeeLikers(defaultIfNull(request.canSeeLikers(), Boolean.FALSE))
                .sortOrder(defaultIfNull(request.sortOrder(), 0))
                .build();

        syncDescriptions(plan, request.details());
        Plans saved = plansRepository.save(plan);

        if (icon != null && !icon.isEmpty()) {
            String iconUrl = uploadPlanIcon(saved.getId(), icon);
            saved.setIconUrl(iconUrl);
            try {
                saved = plansRepository.save(saved);
            } catch (RuntimeException e) {
                // best-effort cleanup: we already uploaded icon, but DB update failed
                profileImageService.deletePublicObjectByUrl(iconUrl);
                throw e;
            }
        }

        return toDto(saved);
    }

    @Transactional
    public PlanDto updatePlan(UUID id, PlanUpsertRequest request, MultipartFile icon) {
        validateRequest(request);

        Plans plan = plansRepository.findWithDescriptionsById(id)
                .orElseThrow(PlanNotFoundException::new);

        String oldIconUrl = plan.getIconUrl();
        String newIconUrl = null;

        plan.setName(request.name().trim());
        plan.setPriceSatang(defaultIfNull(request.priceSatang(), 0));
        plan.setSwipeLimit(request.merryLimit());
        plan.setCanSeeLikers(defaultIfNull(request.canSeeLikers(), Boolean.FALSE));
        plan.setSortOrder(defaultIfNull(request.sortOrder(), 0));

        syncDescriptions(plan, request.details());

        if (icon != null && !icon.isEmpty()) {
            newIconUrl = uploadPlanIcon(plan.getId(), icon);
            plan.setIconUrl(newIconUrl);
        }

        Plans saved;
        try {
            saved = plansRepository.save(plan);
        } catch (RuntimeException e) {
            // best-effort cleanup: if we uploaded a new icon but DB update failed, delete it
            if (newIconUrl != null && !newIconUrl.isBlank()) {
                profileImageService.deletePublicObjectByUrl(newIconUrl);
            }
            throw e;
        }

        // delete old icon only after DB commit attempt succeeded
        if (newIconUrl != null && oldIconUrl != null && !oldIconUrl.isBlank() && !Objects.equals(oldIconUrl, newIconUrl)) {
            profileImageService.deletePublicObjectByUrl(oldIconUrl);
        }

        return toDto(saved);
    }

    @Transactional
    public void deletePlan(UUID id) {
        Plans plan = plansRepository.findWithDescriptionsById(id)
                .orElseThrow(PlanNotFoundException::new);

        if (plan.getIconUrl() != null && !plan.getIconUrl().isBlank()) {
            profileImageService.deletePublicObjectByUrl(plan.getIconUrl());
        }

        plansRepository.delete(plan);
    }

    private void validateRequest(PlanUpsertRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan data is required");
        }
        if (request.merryLimit() == null || request.merryLimit() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "merryLimit must be zero or greater");
        }
        if (request.priceSatang() != null && request.priceSatang() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "priceSatang must be zero or greater");
        }
        if (request.sortOrder() != null && request.sortOrder() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sortOrder must be zero or greater");
        }
        if (request.details() == null || request.details().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "details must contain at least one item");
        }
    }

    private void syncDescriptions(Plans plan, List<PlanDescriptionUpsertRequest> details) {
        List<PlanDescription> descriptions = plan.getDescriptions();
        if (descriptions == null) {
            descriptions = new ArrayList<>();
            plan.setDescriptions(descriptions);
        } else {
            descriptions.clear();
        }
        for (int i = 0; i < details.size(); i++) {
            PlanDescriptionUpsertRequest detail = details.get(i);
            if (detail == null || detail.description() == null || detail.description().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "details[" + i + "].description is required");
            }

            descriptions.add(PlanDescription.builder()
                    .plan(plan)
                    .description(detail.description().trim())
                    .sortOrder(defaultIfNull(detail.sortOrder(), i))
                    .build());
        }
    }

    private String uploadPlanIcon(UUID planId, MultipartFile icon) {
        String safeFilename = (icon.getOriginalFilename() == null || icon.getOriginalFilename().isBlank())
                ? "icon"
                : icon.getOriginalFilename().replaceAll("[\\\\/]", "_").replaceAll("\\s+", "_");
        String objectName = "plans/" + planId + "/" + UUID.randomUUID() + "_" + safeFilename;
        return profileImageService.uploadPublicObject(objectName, icon);
    }

    private <T> T defaultIfNull(T value, T fallback) {
        return value != null ? value : fallback;
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
                .merryLimit(plan.getSwipeLimit())
                .canSeeLikers(plan.getCanSeeLikers())
                .sortOrder(plan.getSortOrder())
                .icon(plan.getIconUrl())
                .iconUrl(plan.getIconUrl())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .details(descriptions)
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

