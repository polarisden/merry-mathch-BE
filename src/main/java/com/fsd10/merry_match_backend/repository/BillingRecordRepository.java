package com.fsd10.merry_match_backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fsd10.merry_match_backend.entity.BillingRecord;

public interface BillingRecordRepository extends JpaRepository<BillingRecord, UUID> {

    Optional<BillingRecord> findByOmiseChargeId(String omiseChargeId);

    boolean existsByOmiseChargeId(String omiseChargeId);

    @Query("""
            select br from BillingRecord br
            where br.user.id = :userId
              and br.status = :status
            order by coalesce(br.paidAt, br.createdAt) desc
            """)
    List<BillingRecord> findByUserIdAndStatusOrderByBilledAtDesc(
            @Param("userId") UUID userId,
            @Param("status") BillingRecord.BillingStatus status);
}
