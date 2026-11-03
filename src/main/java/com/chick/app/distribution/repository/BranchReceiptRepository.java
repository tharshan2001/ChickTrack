package com.chick.app.distribution.repository;

import com.chick.app.distribution.domain.BranchReceipt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchReceiptRepository extends JpaRepository<BranchReceipt, UUID> {
}
