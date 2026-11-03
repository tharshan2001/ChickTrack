package com.chick.app.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "document_sequences")
@IdClass(DocumentSequence.DocumentSequenceId.class)
@Getter
@Setter
@NoArgsConstructor
public class DocumentSequence {

	@Id
	@Column(name = "document_type", length = 50)
	private String documentType;

	@Id
	@Column(name = "year")
	private Integer year;

	@Column(name = "next_value", nullable = false)
	private Long nextValue = 1L;

	public static class DocumentSequenceId implements Serializable {
		private String documentType;
		private Integer year;

		public DocumentSequenceId() {
		}

		public DocumentSequenceId(String documentType, Integer year) {
			this.documentType = documentType;
			this.year = year;
		}
	}
}
