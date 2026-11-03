package com.chick.app.supplier.domain;

import com.chick.app.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
public class Supplier extends BaseEntity {

	@Column(nullable = false, length = 150)
	private String name;

	@Column(name = "contact_person", length = 150)
	private String contactPerson;

	@Column(length = 32)
	private String phone;

	@Column(length = 150)
	private String email;

	@Column(columnDefinition = "text")
	private String address;

	@Column(name = "payment_terms", length = 255)
	private String paymentTerms;

	@Column(nullable = false)
	private boolean active = true;
}
