package com.chick.app.common.repository;

import com.chick.app.common.domain.DocumentSequence;
import com.chick.app.common.domain.DocumentSequence.DocumentSequenceId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, DocumentSequenceId> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from DocumentSequence d where d.documentType = :documentType and d.year = :year")
	DocumentSequence findForUpdate(@Param("documentType") String documentType, @Param("year") Integer year);
}
