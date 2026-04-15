package com.fsd10.merry_match_backend.service;

import com.fsd10.merry_match_backend.dto.MatchResponse;
import com.fsd10.merry_match_backend.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;

    @Transactional(readOnly = true)
    public List<MatchResponse> getMatchesByUserId(UUID userId) {
        return matchRepository.findAllByUserId(userId).stream()
                .map(m -> new MatchResponse(
                        m.getId(),
                        m.getUser1Id(),
                        m.getUser2Id(),
                        m.getMatchedAt(),
                        m.getStatus()
                ))
                .toList();
    }
}
