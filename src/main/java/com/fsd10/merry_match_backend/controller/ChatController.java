package com.fsd10.merry_match_backend.controller;

import com.fsd10.merry_match_backend.auth.SupabaseJwtService;
import com.fsd10.merry_match_backend.dto.ChatImageUploadResponse;
import com.fsd10.merry_match_backend.dto.ChatMessageResponse;
import com.fsd10.merry_match_backend.dto.ChatPeerResponse;
import com.fsd10.merry_match_backend.dto.ChatRoomListItem;
import com.fsd10.merry_match_backend.dto.PatchChatRoomLastMessageRequest;
import com.fsd10.merry_match_backend.dto.SendChatMessageRequest;
import com.fsd10.merry_match_backend.dto.SendChatMessageResponse;
import com.fsd10.merry_match_backend.dto.UnreadSummaryResponse;
import com.fsd10.merry_match_backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

  private final ChatService chatService;
  private final SupabaseJwtService supabaseJwtService;

  @GetMapping("/matches/{matchId}/rooms")
  public ResponseEntity<List<ChatRoomListItem>> listRoomsForMatch(
      @RequestHeader(name = "Authorization", required = false) String authorization,
      @PathVariable UUID matchId) {
    UUID userId = requireUser(authorization);
    return ResponseEntity.ok(chatService.listRoomsForMatch(matchId, userId));
  }

  @GetMapping("/rooms")
  public ResponseEntity<List<ChatRoomListItem>> listRoomsForCurrentUser(
      @RequestHeader(name = "Authorization", required = false) String authorization) {
    UUID userId = requireUser(authorization);
    return ResponseEntity.ok(chatService.listRoomsForCurrentUser(userId));
  }

  @PatchMapping("/rooms/{chatRoomId}/last-message")
  public ResponseEntity<Void> patchRoomLastMessage(
      @RequestHeader(name = "Authorization", required = false) String authorization,
      @PathVariable UUID chatRoomId,
      @RequestBody PatchChatRoomLastMessageRequest body) {
    UUID userId = requireUser(authorization);
    chatService.patchRoomLastMessage(chatRoomId, userId, body);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/rooms/{chatRoomId}/peer")
  public ResponseEntity<ChatPeerResponse> getPeer(
      @RequestHeader(name = "Authorization", required = false) String authorization,
      @PathVariable UUID chatRoomId) {
    UUID userId = requireUser(authorization);
    return ResponseEntity.ok(chatService.getPeer(chatRoomId, userId));
  }

  @GetMapping("/rooms/{chatRoomId}/messages")
  public ResponseEntity<List<ChatMessageResponse>> listMessages(
      @RequestHeader(name = "Authorization", required = false) String authorization,
      @PathVariable UUID chatRoomId) {
    UUID userId = requireUser(authorization);
    return ResponseEntity.ok(chatService.listMessages(chatRoomId, userId));
  }

  @PostMapping("/rooms/{chatRoomId}/messages")
  public ResponseEntity<SendChatMessageResponse> sendMessage(
      @RequestHeader(name = "Authorization", required = false) String authorization,
      @PathVariable UUID chatRoomId,
      @RequestBody SendChatMessageRequest body) {
    UUID userId = requireUser(authorization);
    return ResponseEntity.status(HttpStatus.CREATED).body(chatService.sendMessage(chatRoomId, userId, body));
  }

  @PostMapping("/rooms/{chatRoomId}/read")
  public ResponseEntity<Void> markRead(
      @RequestHeader(name = "Authorization", required = false) String authorization,
      @PathVariable UUID chatRoomId) {
    UUID userId = requireUser(authorization);
    chatService.markRoomRead(chatRoomId, userId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/rooms/{chatRoomId}/images")
  public ResponseEntity<ChatImageUploadResponse> uploadImage(
      @RequestHeader(name = "Authorization", required = false) String authorization,
      @PathVariable UUID chatRoomId,
      @RequestParam("file") MultipartFile file) {
    UUID userId = requireUser(authorization);
    return ResponseEntity.status(HttpStatus.CREATED).body(chatService.uploadChatImage(chatRoomId, userId, file));
  }

  @GetMapping("/unread-summary")
  public ResponseEntity<UnreadSummaryResponse> unreadSummary(
      @RequestHeader(name = "Authorization", required = false) String authorization) {
    UUID userId = requireUser(authorization);
    return ResponseEntity.ok(chatService.unreadSummary(userId));
  }

  private UUID requireUser(String authorization) {
    if (authorization == null || authorization.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    try {
      return supabaseJwtService.requireUserIdFromAuthorization(authorization);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
  }
}
