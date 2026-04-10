package com.fsd10.merry_match_backend.repository;

import com.fsd10.merry_match_backend.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

  @Query(
      "SELECT c FROM ChatRoom c JOIN Match m ON c.matchId = m.id "
          + "WHERE m.user1Id = :uid OR m.user2Id = :uid ORDER BY c.createdAt DESC")
  List<ChatRoom> findByParticipantUserId(@Param("uid") UUID userId);

  @Query(
      "SELECT c FROM ChatRoom c JOIN Match m ON c.matchId = m.id "
          + "WHERE c.matchId = :mid AND (m.user1Id = :uid OR m.user2Id = :uid) "
          + "ORDER BY COALESCE(c.lastMessageAt, c.createdAt) DESC")
  List<ChatRoom> findByMatchIdAndParticipant(@Param("mid") UUID matchId, @Param("uid") UUID userId);
}
