package com.chick.app.common.repository;

import com.chick.app.common.domain.DocumentSequence;
import com.chick.app.common.domain.DocumentSequence.DocumentSequenceId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, DocumentSequenceId> {

	/** Creates the counter row if missing; safe when two transactions race on the first number of a year. */
	@Modifying
	@Query(value = "insert into document_sequences (document_type, year, next_value) values (:documentType, :year, 1) on conflict do nothing", nativeQuery = true)
	void ensureExists(@Param("documentType") String documentType, @Param("year") Integer year);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from DocumentSequence d where d.documentType = :documentType and d.year = :year")
	DocumentSequence findForUpdate(@Param("documentType") String documentType, @Param("year") Integer year);
}
