package com.fsd10.merry_match_backend.service;

import com.fsd10.merry_match_backend.dto.ChatImageUploadResponse;
import com.fsd10.merry_match_backend.dto.ChatMessageResponse;
import com.fsd10.merry_match_backend.dto.ChatPeerResponse;
import com.fsd10.merry_match_backend.dto.ChatRoomListItem;
import com.fsd10.merry_match_backend.dto.PatchChatRoomLastMessageRequest;
import com.fsd10.merry_match_backend.dto.SendChatMessageRequest;
import com.fsd10.merry_match_backend.dto.SendChatMessageResponse;
import com.fsd10.merry_match_backend.dto.UnreadSummaryResponse;
import com.fsd10.merry_match_backend.entity.ChatRoom;
import com.fsd10.merry_match_backend.entity.Match;
import com.fsd10.merry_match_backend.entity.Message;
import com.fsd10.merry_match_backend.entity.ProfileImage;
import com.fsd10.merry_match_backend.entity.User;
import com.fsd10.merry_match_backend.repository.ChatRoomRepository;
import com.fsd10.merry_match_backend.repository.MatchRepository;
import com.fsd10.merry_match_backend.repository.MessageRepository;
import com.fsd10.merry_match_backend.repository.ProfileImageRepository;
import com.fsd10.merry_match_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

  private static final long CHAT_IMAGE_MAX_BYTES = 10L * 1024 * 1024;
  private static final Set<String> CHAT_IMAGE_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp", "image/jpg");

  private final ChatRoomRepository chatRoomRepository;
  private final MatchRepository matchRepository;
  private final MessageRepository messageRepository;
  private final ProfileImageService profileImageService;
  private final UserRepository userRepository;
  private final ProfileImageRepository profileImageRepository;

  @Value("${supabase.chat-bucket:chat-images}")
  private String chatBucket;

  @Value("${supabase.chat-image-sign-ttl-seconds:604800}")
  private long chatImageSignTtlSeconds;

  @Transactional(readOnly = true)
  public List<UUID> getChatRoomIdsByUserId(UUID userId) {
    return chatRoomRepository.findChatRoomIdsByUserId(userId);
  }

  @Transactional(readOnly = true)
  public UUID getChatRoomIdByBothUsers(UUID swiperId, UUID swipedId) {
    Match match = matchRepository.findByBothUsers(swiperId, swipedId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));
    ChatRoom chatRoom = chatRoomRepository.findByMatchId(match.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat room not found"));
    return chatRoom.getId();
  }

  /** All chat rooms the user participates in (any match), newest activity first. */
  @Transactional(readOnly = true)
  public List<ChatRoomListItem> listRoomsForCurrentUser(UUID currentUserId) {
    return chatRoomRepository.findByParticipantUserId(currentUserId).stream()
        .sorted(
            Comparator.comparing(
                    (ChatRoom c) ->
                        c.getLastMessageAt() != null ? c.getLastMessageAt() : c.getCreatedAt())
                .reversed())
        .map(c -> toRoomListItem(c, currentUserId))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ChatRoomListItem> listRoomsForMatch(UUID matchId, UUID currentUserId) {
    Match match =
        matchRepository
            .findById(matchId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));
    if (!match.getUser1Id().equals(currentUserId) && !match.getUser2Id().equals(currentUserId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant in this match");
    }
    return chatRoomRepository.findByMatchIdAndParticipant(matchId, currentUserId).stream()
        .map(c -> toRoomListItem(c, currentUserId))
        .toList();
  }

  @Transactional
  public ChatRoomListItem openOrCreateRoomWithPeer(UUID currentUserId, UUID peerUserId) {
    if (currentUserId.equals(peerUserId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot open chat with yourself");
    }

    userRepository
        .findById(peerUserId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Peer user not found"));

    Match match =
        matchRepository
            .findLatestMatchedOrActivePair(currentUserId, peerUserId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "You can chat only with your matched user"));

    ChatRoom room =
        chatRoomRepository
            .findFirstByMatchIdOrderByCreatedAtAsc(match.getId())
            .orElseGet(
                () ->
                    chatRoomRepository.save(
                        ChatRoom.builder().id(UUID.randomUUID()).matchId(match.getId()).build()));

    return toRoomListItem(room, currentUserId);
  }

  @Transactional
  public void patchRoomLastMessage(UUID chatRoomId, UUID userId, PatchChatRoomLastMessageRequest req) {
    assertParticipant(chatRoomId, userId);
    if (req.getLastMessageType() == null || req.getLastMessageType().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "last_message_type is required");
    }
    if (req.getLastMessageAt() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "last_message_at is required");
    }
    if (req.getLastSenderId() == null || !req.getLastSenderId().equals(userId)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "last_sender_id must match the authenticated user");
    }
    String type = normalizeType(req.getLastMessageType());
    if (type == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "last_message_type must be text or image");
    }

    ChatRoom room =
        chatRoomRepository
            .findById(chatRoomId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat room not found"));
    room.setLastMessageText(blankToNull(req.getLastMessageText()));
    room.setLastMessageType(type);
    room.setLastMessageAt(req.getLastMessageAt());
    room.setLastSenderId(req.getLastSenderId());
    chatRoomRepository.save(room);
  }

  @Transactional(readOnly = true)
  public List<ChatMessageResponse> listMessages(UUID chatRoomId, UUID currentUserId) {
    assertParticipant(chatRoomId, currentUserId);
    return messageRepository.findByChatRoomIdOrderByCreatedAtAsc(chatRoomId).stream()
        .map(this::toResponse)
        .toList();
  }

  /**
   * Other participant in the match for this room — first uploaded profile image (created_at ASC).
   */
  @Transactional(readOnly = true)
  public ChatPeerResponse getPeer(UUID chatRoomId, UUID currentUserId) {
    Match match = resolveMatchForRoom(chatRoomId, currentUserId);
    UUID peerId =
        match.getUser1Id().equals(currentUserId) ? match.getUser2Id() : match.getUser1Id();

    User peer =
        userRepository
            .findById(peerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Peer user not found"));

    String imageUrl = null;
    List<ProfileImage> asc = profileImageRepository.findByUserIdOrderByCreatedAtAsc(peerId);
    if (!asc.isEmpty()) {
      imageUrl = asc.get(0).getImageUrl();
    }

    return ChatPeerResponse.builder().userId(peerId).name(peer.getName()).imageUrl(imageUrl).build();
  }

  @Transactional
  public SendChatMessageResponse sendMessage(UUID chatRoomId, UUID senderId, SendChatMessageRequest req) {
    assertParticipant(chatRoomId, senderId);

    String type = normalizeType(req.getMessageType());
    if (type == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message_type must be text or image");
    }

    String text = blankToNull(req.getMessageText());
    String imageUrl = blankToNull(req.getImageUrl());

    if ("text".equals(type)) {
      if (text == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message_text is required for text messages");
      }
      imageUrl = null;
    } else {
      if (imageUrl == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image_url is required for image messages");
      }
      if (text != null && text.isBlank()) {
        text = null;
      }
    }

    Message saved =
        messageRepository.save(
            Message.builder()
                .id(UUID.randomUUID())
                .chatRoomId(chatRoomId)
                .senderId(senderId)
                .messageType(type)
                .messageText(text)
                .imageUrl(imageUrl)
                .isRead(false)
                .createdAt(Instant.now())
                .build());

    ChatRoom room =
        chatRoomRepository
            .findById(chatRoomId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat room not found"));
    applyLastMessageSnapshot(room, saved);
    chatRoomRepository.save(room);

    return SendChatMessageResponse.builder().message(toResponse(saved)).build();
  }

  @Transactional
  public void markRoomRead(UUID chatRoomId, UUID readerId) {
    assertParticipant(chatRoomId, readerId);
    messageRepository.markIncomingRead(chatRoomId, readerId);
  }

  @Transactional(readOnly = true)
  public UnreadSummaryResponse unreadSummary(UUID userId) {
    long n = messageRepository.countUnreadForUser(userId);
    return UnreadSummaryResponse.builder().totalUnread(n).build();
  }

  @Transactional
  public ChatImageUploadResponse uploadChatImage(UUID chatRoomId, UUID userId, MultipartFile file) {
    assertParticipant(chatRoomId, userId);
    validateChatImage(file);

    String original = Objects.requireNonNullElse(file.getOriginalFilename(), "image");
    String safe = original.replaceAll("[\\\\/]", "_").replaceAll("\\s+", "_");
    String objectName = "rooms/" + chatRoomId + "/" + UUID.randomUUID() + "_" + safe;

    profileImageService.uploadPublicObjectToBucket(chatBucket, objectName, file);
    String imageUrl =
        profileImageService.createSignedUrlForObject(chatBucket, objectName, chatImageSignTtlSeconds);
    return ChatImageUploadResponse.builder().imageUrl(imageUrl).build();
  }

  private void validateChatImage(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
    }
    if (file.getSize() > CHAT_IMAGE_MAX_BYTES) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image must be 10 MB or smaller");
    }
    String ct = file.getContentType();
    if (ct != null && !ct.isBlank()) {
      String normalized = ct.trim().toLowerCase();
      if (!CHAT_IMAGE_TYPES.contains(normalized)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Only JPEG, PNG, or WebP images are allowed");
      }
    } else {
      String name = Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase();
      if (!(name.endsWith(".jpg")
          || name.endsWith(".jpeg")
          || name.endsWith(".png")
          || name.endsWith(".webp"))) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Only JPEG, PNG, or WebP images are allowed");
      }
    }
  }

  private Match resolveMatchForRoom(UUID chatRoomId, UUID userId) {
    ChatRoom room =
        chatRoomRepository
            .findById(chatRoomId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat room not found"));
    Match match =
        matchRepository
            .findById(room.getMatchId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));
    if (!match.getUser1Id().equals(userId) && !match.getUser2Id().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant in this chat");
    }
    return match;
  }

  private void assertParticipant(UUID chatRoomId, UUID userId) {
    resolveMatchForRoom(chatRoomId, userId);
  }

  private void applyLastMessageSnapshot(ChatRoom room, Message saved) {
    String preview;
    if ("image".equals(saved.getMessageType())) {
      if (saved.getMessageText() != null && !saved.getMessageText().isBlank()) {
        preview = saved.getMessageText();
      } else {
        preview = "Photo";
      }
    } else {
      preview = saved.getMessageText();
    }
    room.setLastMessageText(preview);
    room.setLastMessageType(saved.getMessageType());
    room.setLastMessageAt(saved.getCreatedAt());
    room.setLastSenderId(saved.getSenderId());
  }

  private ChatRoomListItem toRoomListItem(ChatRoom c, UUID currentUserId) {
    Match match =
        matchRepository
            .findById(c.getMatchId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));
    UUID peerId =
        match.getUser1Id().equals(currentUserId) ? match.getUser2Id() : match.getUser1Id();

    User peer =
        userRepository
            .findById(peerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Peer user not found"));

    String peerImageUrl = null;
    List<ProfileImage> asc = profileImageRepository.findByUserIdOrderByCreatedAtAsc(peerId);
    if (!asc.isEmpty()) {
      peerImageUrl = asc.get(0).getImageUrl();
    }

    long unread =
        messageRepository.countByChatRoomIdAndSenderIdNotAndIsReadFalse(c.getId(), currentUserId);

    return new ChatRoomListItem(
        c.getId(),
        c.getMatchId(),
        c.getCreatedAt(),
        c.getLastMessageText(),
        c.getLastMessageType(),
        c.getLastMessageAt(),
        c.getLastSenderId(),
        peer.getName() != null ? peer.getName() : "",
        peerImageUrl,
        unread);
  }

  private static String normalizeType(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String t = raw.trim().toLowerCase();
    if ("text".equals(t) || "image".equals(t)) {
      return t;
    }
    return null;
  }

  private static String blankToNull(String s) {
    if (s == null) {
      return null;
    }
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  private ChatMessageResponse toResponse(Message m) {
    String imageUrl = m.getImageUrl();
    if ("image".equals(m.getMessageType()) && imageUrl != null && !imageUrl.isBlank()) {
      imageUrl =
          profileImageService.refreshToSignedUrlIfPublicChatPath(
              chatBucket, imageUrl, chatImageSignTtlSeconds);
    }
    return ChatMessageResponse.builder()
        .id(m.getId())
        .chatRoomId(m.getChatRoomId())
        .senderId(m.getSenderId())
        .messageType(m.getMessageType())
        .messageText(m.getMessageText())
        .imageUrl(imageUrl)
        .isRead(m.isRead())
        .createdAt(m.getCreatedAt())
        .build();
  }
}
