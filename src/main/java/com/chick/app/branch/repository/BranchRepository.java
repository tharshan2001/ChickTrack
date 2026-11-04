package com.chick.app.branch.repository;

import com.chick.app.branch.domain.Branch;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BranchRepository extends JpaRepository<Branch, UUID>, JpaSpecificationExecutor<Branch> {

	Optional<Branch> findByCode(String code);

	boolean existsByCode(String code);

	/**
	 * Locks the branch row. Writes that change a branch's stock or balance (sales, settlements,
	 * receipts, reversals) take this lock so they run one at a time per branch.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from Branch b where b.id = :id")
	Optional<Branch> lockById(@Param("id") UUID id);
}
