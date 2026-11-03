package com.chick.app.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true, length = 50)
	private String name;

	@Column(length = 255)
	private String description;
}
