package com.fsd10.merry_match_backend.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

	@Id
	@Column(columnDefinition = "uuid")
	private UUID id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(nullable = false, unique = true)
	private String username;

	@Column(nullable = false)
	private String name;

	@Column(name = "date_of_birth")
	private LocalDate dateOfBirth;

	private String gender;

	@Column(name = "sexual_preference")
	private String sexualPreference;

	@Column(name = "racial_preference")
	private String racialPreference;

	@Column(name = "meeting_interest")
	private String meetingInterest;

	@Column(name = "location_country")
	private String locationCountry;

	@Column(name = "location_city")
	private String locationCity;

	@Column(columnDefinition = "TEXT")
	private String bio;

	@Column(name = "created_at")
	private Instant createdAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	@Column(nullable = false)
	private String role;

	@Column(name = "merry_count")
	private Integer merryCount;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		if (this.createdAt == null) this.createdAt = now;
		if (this.updatedAt == null) this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}
}

