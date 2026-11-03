package com.chick.app.payment.repository;

import com.chick.app.payment.domain.BranchSettlement;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BranchSettlementRepository extends JpaRepository<BranchSettlement, UUID>, JpaSpecificationExecutor<BranchSettlement> {
}
