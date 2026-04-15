package com.fsd10.merry_match_backend.repository;

import com.fsd10.merry_match_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmail(String email);

  Optional<User> findByUsername(String username);

  @Query(value = """
      select *
      from users u
      where u.id <> :userId
        and exists (
          select 1
          from swipes sw
          where sw.swiper_id = :userId
            and sw.swiped_id = u.id
            and sw.action in ('like', 'super_like')
        )
      order by u.created_at desc, u.id desc
      limit :limit
      offset :offset
      """, nativeQuery = true)
  List<User> findMerryListPage(
      @Param("userId") UUID userId,
      @Param("limit") int limit,
      @Param("offset") int offset);

  @Query(value = """
      select p.swipe_limit
      from subscriptions s
      join plans p on p.id = s.plan_id
      where s.user_id = :userId
      order by s.created_at desc
      limit 1
      """, nativeQuery = true)
  Integer findLimitMaxByUserId(@Param("userId") UUID userId);

  @Query(value = """
      select count(*)
      from swipes sw
      where sw.swiper_id = :userId
        and sw.action in ('like', 'super_like')
        and sw.swiped_at >= :startAt
        and sw.swiped_at < :endAt
      """, nativeQuery = true)
  int countTodayQuotaUsage(
      @Param("userId") UUID userId,
      @Param("startAt") Instant startAt,
      @Param("endAt") Instant endAt);

  @Query(value = """
      select count(*)
      from swipes sw
      where sw.swiper_id = :userId
        and sw.action in ('like', 'super_like')
      """, nativeQuery = true)
  int countOutgoingLikes(@Param("userId") UUID userId);

  @Modifying
  @Query(value = """
      delete from swipes sw
      where sw.swiper_id = :userId
        and sw.swiped_id = :targetUserId
        and sw.action in ('like', 'super_like')
      """, nativeQuery = true)
  int deleteOutgoingLikeToTarget(
      @Param("userId") UUID userId,
      @Param("targetUserId") UUID targetUserId);
}

