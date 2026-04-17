package com.fsd10.merry_match_backend.repository;

import com.fsd10.merry_match_backend.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

  @Query("SELECT m FROM Match m WHERE m.user1Id = :userId OR m.user2Id = :userId")
  List<Match> findAllByUserId(@Param("userId") UUID userId);

  @Query("SELECT m FROM Match m WHERE (m.user1Id = :id1 AND m.user2Id = :id2) OR (m.user1Id = :id2 AND m.user2Id = :id1)")
  Optional<Match> findByBothUsers(@Param("id1") UUID id1, @Param("id2") UUID id2);

  interface PeerMatchStatusView {
    UUID getPeerId();
    String getStatus();
    Instant getMatchedAt();
  }

  @Query("""
      select m from Match m
      where m.status = 'active'
        and ((m.user1Id = :currentUserId and m.user2Id = :targetUserId)
          or (m.user1Id = :targetUserId and m.user2Id = :currentUserId))
      """)
  Optional<Match> findActivePair(@Param("currentUserId") UUID currentUserId, @Param("targetUserId") UUID targetUserId);

  @Query(value = """
      select distinct
        case
          when m.user1_id = :currentUserId then m.user2_id
          else m.user1_id
        end as peer_id
      from matches m
      where m.status = 'active'
        and (m.user1_id = :currentUserId or m.user2_id = :currentUserId)
        and (case when m.user1_id = :currentUserId then m.user2_id else m.user1_id end) in (:candidateIds)
      """, nativeQuery = true)
  List<UUID> findMatchedPeerIdsForCurrentUser(
      @Param("currentUserId") UUID currentUserId,
      @Param("candidateIds") List<UUID> candidateIds);

  @Query(value = """
      select distinct on (peer_id)
        peer_id,
        status,
        matched_at
      from (
        select
          case
            when m.user1_id = :currentUserId then m.user2_id
            else m.user1_id
          end as peer_id,
          m.status as status,
          m.matched_at as matched_at
        from matches m
        where (m.user1_id = :currentUserId or m.user2_id = :currentUserId)
          and (case when m.user1_id = :currentUserId then m.user2_id else m.user1_id end) in (:candidateIds)
      ) x
      order by peer_id, matched_at desc
      """, nativeQuery = true)
  List<PeerMatchStatusView> findPeerMatchStatusesForCurrentUser(
      @Param("currentUserId") UUID currentUserId,
      @Param("candidateIds") List<UUID> candidateIds);

  @Query(value = """
      select count(distinct
        case
          when m.user1_id = :currentUserId then m.user2_id
          else m.user1_id
        end
      )
      from matches m
      where m.status in ('active', 'matched')
        and (m.user1_id = :currentUserId or m.user2_id = :currentUserId)
      """, nativeQuery = true)
  int countActiveMatchByUserId(@Param("currentUserId") UUID currentUserId);

  @Query(value = """
      select *
      from matches m
      where m.status in ('active', 'matched')
        and ((m.user1_id = :currentUserId and m.user2_id = :targetUserId)
          or (m.user1_id = :targetUserId and m.user2_id = :currentUserId))
      order by m.matched_at desc
      limit 1
      """, nativeQuery = true)
  Optional<Match> findLatestMatchedOrActivePair(
      @Param("currentUserId") UUID currentUserId,
      @Param("targetUserId") UUID targetUserId);
}
