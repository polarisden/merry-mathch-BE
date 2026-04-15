package com.fsd10.merry_match_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "swipes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Swipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "swipe_id")
    private Long swipeId;

    @Column(name = "swiper_id", nullable = false, columnDefinition = "uuid")
    private java.util.UUID swiperId;

    @Column(name = "swiped_id", nullable = false, columnDefinition = "uuid")
    private java.util.UUID swipedId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "swiped_at", nullable = false)
    private Instant swipedAt;
}
