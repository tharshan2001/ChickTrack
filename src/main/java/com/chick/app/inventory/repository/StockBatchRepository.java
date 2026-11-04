package com.chick.app.inventory.repository;

import com.chick.app.inventory.domain.StockBatch;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.chick.app.chicken.domain.ChickenType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockBatchRepository
		extends JpaRepository<StockBatch, UUID>, JpaSpecificationExecutor<StockBatch> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from StockBatch b where b.chickenType = :chickenType and b.remainingWeightKg > b.reservedWeightKg order by b.receivedAt asc, b.id asc")
	java.util.List<StockBatch> findAvailableByChickenType(@Param("chickenType") ChickenType chickenType);

	/** Locks the given batches in id order so concurrent dispatches cannot deadlock or oversell. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from StockBatch b where b.id in :ids order by b.id")
	java.util.List<StockBatch> lockAllById(@Param("ids") java.util.Collection<UUID> ids);
}
