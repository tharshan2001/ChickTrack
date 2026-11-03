package com.chick.app.distribution.repository;

import com.chick.app.distribution.domain.Distribution;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DistributionRepository extends JpaRepository<Distribution, UUID>, JpaSpecificationExecutor<Distribution> {
}
