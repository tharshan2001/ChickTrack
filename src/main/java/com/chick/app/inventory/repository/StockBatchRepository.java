package com.chick.app.inventory.repository;

import com.chick.app.inventory.domain.StockBatch;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockBatchRepository extends JpaRepository<StockBatch, UUID> {
}
