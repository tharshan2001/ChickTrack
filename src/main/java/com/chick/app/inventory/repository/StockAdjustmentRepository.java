package com.chick.app.inventory.repository;

import com.chick.app.inventory.domain.StockAdjustment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StockAdjustmentRepository
		extends JpaRepository<StockAdjustment, UUID>, JpaSpecificationExecutor<StockAdjustment> {
}
