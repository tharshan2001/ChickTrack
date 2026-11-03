package com.chick.app.sales.repository;

import com.chick.app.branch.domain.Branch;
import com.chick.app.sales.domain.BranchSale;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BranchSaleRepository
		extends JpaRepository<BranchSale, UUID>, JpaSpecificationExecutor<BranchSale> {

	Optional<BranchSale> findByBranchAndSaleDate(Branch branch, LocalDate saleDate);
}
