package com.chick.app.distribution.repository;

import com.chick.app.distribution.domain.ExpenseAllocation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseAllocationRepository extends JpaRepository<ExpenseAllocation, UUID> {
}
