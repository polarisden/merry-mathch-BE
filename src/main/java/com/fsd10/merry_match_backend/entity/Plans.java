package com.fsd10.merry_match_backend.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plans {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "price_satang", nullable = false)
    private Integer priceSatang;

    @Column(name = "swipe_limit", nullable = false)
    private Integer swipeLimit;

    @Column(name = "can_see_likers", nullable = false)
    private Boolean canSeeLikers;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "icon_url")
    private String iconUrl;

    // managed by database trigger (update_updated_at_column)
    @Column(name = "created_at", updatable = false, insertable = false)
    private LocalDateTime createdAt;

    // managed by database default (CURRENT_TIMESTAMP)
    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "plan", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<PlanDescription> descriptions;
}
