package com.fsd10.merry_match_backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fsd10.merry_match_backend.entity.Plans;

public interface PlansRepository extends JpaRepository<Plans, UUID> {

    @Override
    @EntityGraph(attributePaths = "descriptions")
    java.util.List<Plans> findAll(Sort sort);

    @EntityGraph(attributePaths = "descriptions")
    Optional<Plans> findWithDescriptionsById(UUID id);
}

