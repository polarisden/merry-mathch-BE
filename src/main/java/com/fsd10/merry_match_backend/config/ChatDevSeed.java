package com.fsd10.merry_match_backend.config;

import com.fsd10.merry_match_backend.entity.ChatRoom;
import com.fsd10.merry_match_backend.entity.Match;
import com.fsd10.merry_match_backend.entity.User;
import com.fsd10.merry_match_backend.repository.ChatRoomRepository;
import com.fsd10.merry_match_backend.repository.MatchRepository;
import com.fsd10.merry_match_backend.repository.MessageRepository;
import com.fsd10.merry_match_backend.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Optional: creates {@code matches} + {@code chat_rooms} for the dev room UUID so the frontend mock
 * room id works without manual SQL. Enable with {@code merry.chat.seed-dev-room=true}.
 *
 * <p>Set {@code merry.chat.dev-room-participant-email} so the logged-in dev user is always one of
 * the two participants (otherwise the first two rows in {@code users} are used, which often causes
 * 403 for the account you are testing with).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatDevSeed {

  private final UserRepository userRepository;
  private final MatchRepository matchRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final MessageRepository messageRepository;
  private final ChatProperties chatProperties;

  @Order(100)
  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void seedDevChatRoom() {
    if (!chatProperties.seedDevRoom()) {
      return;
    }
    UUID devRoomId = chatProperties.devRoomId();
    String participantEmail = chatProperties.devRoomParticipantEmail();
    Optional<Pair> pairOpt = resolveParticipantPair(participantEmail);
    if (pairOpt.isEmpty()) {
      log.warn("merry.chat.seed-dev-room=true but could not resolve two users; skipping chat room seed");
      return;
    }
    Pair pair = pairOpt.get();

    if (chatRoomRepository.existsById(devRoomId)) {
      ChatRoom existing = chatRoomRepository.findById(devRoomId).orElseThrow();
      Match m = matchRepository.findById(existing.getMatchId()).orElseThrow();
      if (participantsMatch(m, pair.u1, pair.u2)) {
        log.debug("Dev chat room {} already seeded for expected participants", devRoomId);
        return;
      }
      if (participantEmail == null || participantEmail.isBlank()) {
        log.info("Dev chat room {} exists with different participants; set merry.chat.dev-room-participant-email to fix or delete rows manually", devRoomId);
        return;
      }
      log.info("Dev chat room {} participants mismatch; reseeding for {}", devRoomId, participantEmail.trim());
      messageRepository.deleteByChatRoomId(devRoomId);
      chatRoomRepository.deleteById(devRoomId);
      matchRepository.deleteById(m.getId());
    }

    UUID matchId = UUID.randomUUID();
    Match m =
        Match.builder()
            .id(matchId)
            .user1Id(pair.u1)
            .user2Id(pair.u2)
            .matchedAt(Instant.now())
            .status("active")
            .build();
    matchRepository.save(m);
    ChatRoom room =
        ChatRoom.builder().id(devRoomId).matchId(matchId).createdAt(Instant.now()).build();
    chatRoomRepository.save(room);
    log.info("Seeded dev chat room {} (match {}) for users {} and {}", devRoomId, matchId, pair.u1, pair.u2);
  }

  private static boolean participantsMatch(Match m, UUID a, UUID b) {
    return (m.getUser1Id().equals(a) && m.getUser2Id().equals(b))
        || (m.getUser1Id().equals(b) && m.getUser2Id().equals(a));
  }

  private Optional<Pair> resolveParticipantPair(String participantEmail) {
    String email = participantEmail == null ? "" : participantEmail.trim().toLowerCase();
    if (!email.isEmpty()) {
      Optional<User> primary = userRepository.findByEmail(email);
      if (primary.isEmpty()) {
        log.warn("merry.chat.dev-room-participant-email={} not found in users table", email);
        return Optional.empty();
      }
      UUID u1 = primary.get().getId();
      Optional<UUID> u2 = userRepository.findAll(PageRequest.of(0, 50)).getContent().stream()
          .map(User::getId)
          .filter(id -> !id.equals(u1))
          .findFirst();
      if (u2.isEmpty()) {
        log.warn("Need at least 2 users to seed dev chat room (only found {})", email);
        return Optional.empty();
      }
      return Optional.of(new Pair(u1, u2.get()));
    }

    List<User> users = userRepository.findAll(PageRequest.of(0, 2)).getContent();
    if (users.size() < 2) {
      return Optional.empty();
    }
    return Optional.of(new Pair(users.get(0).getId(), users.get(1).getId()));
  }

  private record Pair(UUID u1, UUID u2) {}
}
