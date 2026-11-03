package com.chick.app.branch.repository;

import com.chick.app.branch.domain.Branch;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BranchRepository extends JpaRepository<Branch, UUID>, JpaSpecificationExecutor<Branch> {

	Optional<Branch> findByCode(String code);

	boolean existsByCode(String code);
}
