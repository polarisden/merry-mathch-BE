package com.fsd10.merry_match_backend.controller;

import com.fsd10.merry_match_backend.dto.ChatRoomIdByUsersResponse;
import com.fsd10.merry_match_backend.dto.ChatRoomIdResponse;
import com.fsd10.merry_match_backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatService chatService;

    @GetMapping("/chatroom-id/{userId}")
    public ResponseEntity<ChatRoomIdResponse> getChatRoomIds(@PathVariable UUID userId) {
        return ResponseEntity.ok(new ChatRoomIdResponse(chatService.getChatRoomIdsByUserId(userId)));
    }

    @GetMapping("/chatroom-id")
    public ResponseEntity<ChatRoomIdByUsersResponse> getChatRoomIdByBothUsers(
            @RequestParam("swiper_id") UUID swiperId,
            @RequestParam("swiped_id") UUID swipedId
    ) {
        return ResponseEntity.ok(new ChatRoomIdByUsersResponse(chatService.getChatRoomIdByBothUsers(swiperId, swipedId)));
    }
}
