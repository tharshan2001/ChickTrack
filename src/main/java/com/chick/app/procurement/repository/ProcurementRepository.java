package com.chick.app.procurement.repository;

import com.chick.app.procurement.domain.Procurement;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProcurementRepository extends JpaRepository<Procurement, UUID>, JpaSpecificationExecutor<Procurement> {
}
