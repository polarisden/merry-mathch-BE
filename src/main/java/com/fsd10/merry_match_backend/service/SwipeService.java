package com.fsd10.merry_match_backend.service;

import com.fsd10.merry_match_backend.dto.SwipeHistoryResponse;
import com.fsd10.merry_match_backend.dto.SwipeRequest;
import com.fsd10.merry_match_backend.entity.Swipe;
import com.fsd10.merry_match_backend.repository.SwipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SwipeService {

    private final SwipeRepository swipeRepository;

    @Transactional
    public Swipe recordSwipe(SwipeRequest req) {
        if (!List.of("like", "pass", "super_like").contains(req.action())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "action must be one of: like, pass, super_like");
        }
        Swipe swipe = Swipe.builder()
                .swiperId(req.swiperId())
                .swipedId(req.swipedId())
                .action(req.action())
                .swipedAt(Instant.now())
                .build();
        return swipeRepository.save(swipe);
    }

    @Transactional(readOnly = true)
    public SwipeHistoryResponse getSwipeHistory(UUID swiperId) {
        return new SwipeHistoryResponse(
                swipeRepository.findSwipedIdsBySwiperId(swiperId),
                swipeRepository.findSwipedIdsBySwiperIdAndAction(swiperId, "like"),
                swipeRepository.findSwipedIdsBySwiperIdAndAction(swiperId, "pass")
        );
    }
}
