package com.chick.app.expense.repository;

import com.chick.app.expense.domain.OperatingExpense;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OperatingExpenseRepository
		extends JpaRepository<OperatingExpense, UUID>, JpaSpecificationExecutor<OperatingExpense> {
}
