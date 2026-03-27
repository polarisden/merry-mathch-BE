package com.fsd10.merry_match_backend.service;

import com.fsd10.merry_match_backend.entity.Test;
import com.fsd10.merry_match_backend.repository.TestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestService {

    private final TestRepository testRepository;

    public List<Test> getAllTests() {
        return testRepository.findAll();
    }
}
