package com.fsd10.merry_match_backend.repository;

import com.fsd10.merry_match_backend.entity.Swipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SwipeRepository extends JpaRepository<Swipe, Long> {

    @Query("SELECT s.swipedId FROM Swipe s WHERE s.swiperId = :swiperId")
    List<UUID> findSwipedIdsBySwiperId(@Param("swiperId") UUID swiperId);

    @Query("SELECT s.swipedId FROM Swipe s WHERE s.swiperId = :swiperId AND s.action = :action")
    List<UUID> findSwipedIdsBySwiperIdAndAction(@Param("swiperId") UUID swiperId, @Param("action") String action);
}
