package com.chick.app.pricing.repository;

import com.chick.app.pricing.domain.DailyPrice;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DailyPriceRepository extends JpaRepository<DailyPrice, UUID>, JpaSpecificationExecutor<DailyPrice> {
}
