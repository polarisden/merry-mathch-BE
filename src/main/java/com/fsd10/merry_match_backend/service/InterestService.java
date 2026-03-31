package com.fsd10.merry_match_backend.service;

import com.fsd10.merry_match_backend.entity.Interest;
import com.fsd10.merry_match_backend.repository.InterestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InterestService {

  private final InterestRepository interestRepository;

  public List<Interest> getAllInterests() {
    return interestRepository.findAll();
  }
}
