package com.chick.app.inventory.repository;

import com.chick.app.inventory.domain.InventoryMovement;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {
}
