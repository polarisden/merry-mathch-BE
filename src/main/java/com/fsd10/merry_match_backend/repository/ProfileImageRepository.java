package com.fsd10.merry_match_backend.repository;

import com.fsd10.merry_match_backend.entity.ProfileImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileImageRepository extends JpaRepository<ProfileImage, UUID> {

  interface UserImageView {
    UUID getUserId();
    String getImageUrl();
  }

  List<ProfileImage> findByUserIdOrderByCreatedAtDesc(UUID userId);

  /** Oldest upload first (first photo from register / earliest profile upload). */
  List<ProfileImage> findByUserIdOrderByCreatedAtAsc(UUID userId);

  Optional<ProfileImage> findFirstByUserIdAndIsPrimaryTrueOrderByCreatedAtAsc(UUID userId);

  @Modifying
  @Query("update ProfileImage p set p.isPrimary = false where p.userId = :userId")
  void clearPrimaryByUserId(@Param("userId") UUID userId);

  long deleteByIdAndUserId(UUID id, UUID userId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from ProfileImage p where p.userId = :userId")
  int deleteAllByUserId(@Param("userId") UUID userId);

  @Query(value = """
      select distinct on (p.user_id)
        p.user_id as userId,
        p.image_url as imageUrl
      from profile_images p
      where p.user_id in (:userIds)
      order by p.user_id, p.created_at asc
      """, nativeQuery = true)
  List<UserImageView> findFirstImageByUserIds(@Param("userIds") List<UUID> userIds);
}

