package com.chick.app.inventory.repository;

import com.chick.app.inventory.domain.StockBatch;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import com.chick.app.chicken.domain.ChickenType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockBatchRepository extends JpaRepository<StockBatch, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from StockBatch b where b.chickenType = :chickenType and b.remainingWeightKg > 0 order by b.receivedAt asc, b.id asc")
	java.util.List<StockBatch> findAvailableByChickenType(@Param("chickenType") ChickenType chickenType);
}
