package com.fsd10.merry_match_backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fsd10.merry_match_backend.dto.plan.PlanDto;
import com.fsd10.merry_match_backend.service.PlansService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/api/plans"})
@RequiredArgsConstructor
public class PlansController {

    private final PlansService plansService;

    @GetMapping
    public ResponseEntity<List<PlanDto>> getAllPlans() {
        return ResponseEntity.ok(plansService.getAllPlans());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanDto> getPlanById(@PathVariable UUID id) {
        return ResponseEntity.ok(plansService.getPlanById(id));
    }
}

