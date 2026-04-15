package com.fsd10.merry_match_backend.service;

import com.fsd10.merry_match_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MerryCountService {

    private final UserRepository userRepository;

    @Transactional
    public void incrementMerryCount(UUID userId) {
        int updated = userRepository.incrementMerryCount(userId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }
}
