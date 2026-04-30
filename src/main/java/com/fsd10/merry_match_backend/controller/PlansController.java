package com.fsd10.merry_match_backend.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd10.merry_match_backend.dto.plan.PlanDescriptionUpsertRequest;
import com.fsd10.merry_match_backend.dto.plan.PlanDto;
import com.fsd10.merry_match_backend.dto.plan.PlanUpsertRequest;
import com.fsd10.merry_match_backend.service.PlansService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/api/plans", "/api/packages"})
@RequiredArgsConstructor
public class PlansController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PlansService plansService;

    @GetMapping
    public ResponseEntity<List<PlanDto>> getAllPlans() {
        return ResponseEntity.ok(plansService.getAllPlans());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanDto> getPlanById(@PathVariable UUID id) {
        return ResponseEntity.ok(plansService.getPlanById(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlanDto> createPlan(
            @RequestParam String name,
            @RequestParam(name = "merryLimit") Integer merryLimit,
            @RequestParam(name = "details") String details,
            @RequestParam(name = "priceSatang", required = false) Integer priceSatang,
            @RequestParam(name = "canSeeLikers", required = false) Boolean canSeeLikers,
            @RequestParam(name = "sortOrder", required = false) Integer sortOrder,
            @RequestParam(name = "icon", required = false) MultipartFile icon) {
        PlanUpsertRequest request = buildRequest(name, merryLimit, details, priceSatang, canSeeLikers, sortOrder);
        return ResponseEntity.status(201).body(plansService.createPlan(request, icon));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlanDto> updatePlan(
            @PathVariable UUID id,
            @RequestParam String name,
            @RequestParam(name = "merryLimit") Integer merryLimit,
            @RequestParam(name = "details") String details,
            @RequestParam(name = "priceSatang", required = false) Integer priceSatang,
            @RequestParam(name = "canSeeLikers", required = false) Boolean canSeeLikers,
            @RequestParam(name = "sortOrder", required = false) Integer sortOrder,
            @RequestParam(name = "icon", required = false) MultipartFile icon) {
        PlanUpsertRequest request = buildRequest(name, merryLimit, details, priceSatang, canSeeLikers, sortOrder);
        return ResponseEntity.ok(plansService.updatePlan(id, request, icon));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deletePlan(@PathVariable UUID id) {
        plansService.deletePlan(id);
        return ResponseEntity.ok().body(java.util.Map.of("message", "Plan deleted successfully"));
    }

    private PlanUpsertRequest buildRequest(
            String name,
            Integer merryLimit,
            String details,
            Integer priceSatang,
            Boolean canSeeLikers,
            Integer sortOrder) {
        return new PlanUpsertRequest(
                name,
                merryLimit,
                priceSatang,
                canSeeLikers,
                sortOrder,
                parseDetails(details));
    }

    private List<PlanDescriptionUpsertRequest> parseDetails(String details) {
        try {
            return OBJECT_MAPPER.readValue(details, new TypeReference<List<PlanDescriptionUpsertRequest>>() {});
        } catch (IOException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "details must be a valid JSON array");
        }
    }
}

