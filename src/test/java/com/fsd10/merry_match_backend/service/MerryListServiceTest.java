package com.fsd10.merry_match_backend.service;

import com.fsd10.merry_match_backend.dto.MerryActionResponse;
import com.fsd10.merry_match_backend.dto.MerryListResponse;
import com.fsd10.merry_match_backend.entity.Match;
import com.fsd10.merry_match_backend.entity.User;
import com.fsd10.merry_match_backend.repository.MatchRepository;
import com.fsd10.merry_match_backend.repository.ProfileImageRepository;
import com.fsd10.merry_match_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerryListServiceTest {

  @Mock
  private UserRepository userRepository;
  @Mock
  private MatchRepository matchRepository;
  @Mock
  private ProfileImageRepository profileImageRepository;

  @InjectMocks
  private MerryListService merryListService;

  @Test
  void getMerryList_shouldReturnPagedItemsWithMatchedStatusAndComputedAge() {
    UUID currentUserId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    User target = User.builder()
        .id(targetId)
        .name("Alice")
        .dateOfBirth(LocalDate.now().minusYears(24))
        .locationCity("Bangkok")
        .locationCountry("Thailand")
        .gender("Female")
        .sexualPreference("Male")
        .racialPreference("Indefinite")
        .meetingInterest("Long-term commitment")
        .build();

    when(userRepository.findMerryListPage(currentUserId, 20, 0))
        .thenReturn(List.of(target));
    MatchRepository.PeerMatchStatusView statusView = new MatchRepository.PeerMatchStatusView() {
      @Override
      public UUID getPeerId() {
        return targetId;
      }

      @Override
      public String getStatus() {
        return "active";
      }
    };
    when(matchRepository.findPeerMatchStatusesForCurrentUser(currentUserId, List.of(targetId)))
        .thenReturn(List.of(statusView));

    ProfileImageRepository.UserImageView imageView = new ProfileImageRepository.UserImageView() {
      @Override
      public UUID getUserId() {
        return targetId;
      }

      @Override
      public String getImageUrl() {
        return "https://img.example.com/a.jpg";
      }
    };
    when(profileImageRepository.findFirstImageByUserIds(List.of(targetId))).thenReturn(List.of(imageView));
    when(userRepository.countOutgoingLikes(currentUserId)).thenReturn(9);
    when(matchRepository.countActiveMatchByUserId(currentUserId)).thenReturn(3);
    when(userRepository.countTodayQuotaUsage(eq(currentUserId), any(), any())).thenReturn(7);
    when(userRepository.findLimitMaxByUserId(currentUserId)).thenReturn(45);

    MerryListResponse response = merryListService.getMerryList(currentUserId, 1, 20);

    assertEquals(1, response.items().size());
    assertEquals("Alice", response.items().get(0).name());
    assertNotNull(response.items().get(0).age());
    assertTrue(response.items().get(0).matched());
    assertEquals("Bangkok, Thailand", response.items().get(0).location());
    assertEquals(9, response.merryToYou());
    assertEquals(3, response.merryMatch());
    assertEquals(7, response.limitUsed());
    assertEquals(45, response.limitMax());
  }

  @Test
  void getMerryList_shouldFallbackLimitMaxTo20WhenNoPlan() {
    UUID currentUserId = UUID.randomUUID();
    when(userRepository.findMerryListPage(currentUserId, 20, 0))
        .thenReturn(List.of());
    when(userRepository.countOutgoingLikes(currentUserId)).thenReturn(0);
    when(matchRepository.countActiveMatchByUserId(currentUserId)).thenReturn(0);
    when(userRepository.countTodayQuotaUsage(eq(currentUserId), any(), any())).thenReturn(0);
    when(userRepository.findLimitMaxByUserId(currentUserId)).thenReturn(null);

    MerryListResponse response = merryListService.getMerryList(currentUserId, 1, 20);

    assertEquals(0, response.limitUsed());
    assertEquals(20, response.limitMax());
  }

  @Test
  void getMerryList_shouldUseTodaySwipeCountAsLimitUsed() {
    UUID currentUserId = UUID.randomUUID();
    when(userRepository.findMerryListPage(currentUserId, 20, 0))
        .thenReturn(List.of());
    when(userRepository.countOutgoingLikes(currentUserId)).thenReturn(12);
    when(matchRepository.countActiveMatchByUserId(currentUserId)).thenReturn(4);
    when(userRepository.countTodayQuotaUsage(eq(currentUserId), any(), any())).thenReturn(11);
    when(userRepository.findLimitMaxByUserId(currentUserId)).thenReturn(45);

    MerryListResponse response = merryListService.getMerryList(currentUserId, 1, 20);

    assertEquals(12, response.merryToYou());
    assertEquals(4, response.merryMatch());
    assertEquals(11, response.limitUsed());
    assertEquals(45, response.limitMax());
  }

  @Test
  void merry_shouldBeIdempotentWhenActivePairAlreadyExists() {
    UUID currentUserId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();
    UUID matchId = UUID.randomUUID();

    Match existing = Match.builder()
        .id(matchId)
        .user1Id(targetUserId)
        .user2Id(currentUserId)
        .status("active")
        .build();

    when(userRepository.findById(targetUserId)).thenReturn(Optional.of(User.builder().id(targetUserId).build()));
    when(matchRepository.findActivePair(currentUserId, targetUserId)).thenReturn(Optional.of(existing));

    MerryActionResponse response = merryListService.merry(currentUserId, targetUserId);

    assertEquals(matchId, response.id());
    assertEquals("active", response.status());
    verify(matchRepository, never()).save(any(Match.class));
  }

  @Test
  void merry_shouldRejectMerryYourself() {
    UUID currentUserId = UUID.randomUUID();

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class,
        () -> merryListService.merry(currentUserId, currentUserId)
    );

    assertEquals(400, ex.getStatusCode().value());
    assertFalse(ex.getReason() == null || ex.getReason().isBlank());
    verify(userRepository, never()).findById(eq(currentUserId));
  }
}
