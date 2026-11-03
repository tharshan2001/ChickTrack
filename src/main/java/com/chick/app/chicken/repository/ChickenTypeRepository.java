package com.chick.app.chicken.repository;

import com.chick.app.chicken.domain.ChickenType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ChickenTypeRepository extends JpaRepository<ChickenType, UUID>, JpaSpecificationExecutor<ChickenType> {
}
