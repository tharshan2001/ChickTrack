package com.chick.app.branch.domain;

import com.chick.app.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "branches")
@Getter
@Setter
@NoArgsConstructor
public class Branch extends BaseEntity {

	@Column(nullable = false, unique = true, length = 32)
	private String code;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(columnDefinition = "text")
	private String address;

	@Column(length = 32)
	private String phone;

	@Column(nullable = false)
	private boolean active = true;
}
