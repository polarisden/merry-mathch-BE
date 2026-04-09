package com.fsd10.merry_match_backend.repository;

import com.fsd10.merry_match_backend.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

  List<Message> findByChatRoomIdOrderByCreatedAtAsc(UUID chatRoomId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM Message m WHERE m.chatRoomId = :roomId")
  void deleteByChatRoomId(@Param("roomId") UUID roomId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Message m SET m.isRead = true WHERE m.chatRoomId = :roomId AND m.senderId <> :readerId AND m.isRead = false")
  int markIncomingRead(@Param("roomId") UUID roomId, @Param("readerId") UUID readerId);

  @Query(
      "SELECT COUNT(m) FROM Message m, ChatRoom c, Match mt "
          + "WHERE m.chatRoomId = c.id AND c.matchId = mt.id "
          + "AND (mt.user1Id = :uid OR mt.user2Id = :uid) "
          + "AND m.senderId <> :uid AND m.isRead = false")
  long countUnreadForUser(@Param("uid") UUID userId);
}
