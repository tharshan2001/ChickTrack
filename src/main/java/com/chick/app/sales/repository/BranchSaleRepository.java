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

	/** Sold weight per chicken type for a branch, leaving out one sale (pass a random id to keep all). */
	@org.springframework.data.jpa.repository.Query("""
			select item.chickenType.id, sum(item.weightKg)
			from BranchSaleItem item
			where item.branchSale.branch.id = :branchId and item.branchSale.id <> :excludedSaleId
			group by item.chickenType.id
			""")
	java.util.List<Object[]> soldWeightByChickenType(
			@org.springframework.data.repository.query.Param("branchId") java.util.UUID branchId,
			@org.springframework.data.repository.query.Param("excludedSaleId") java.util.UUID excludedSaleId);

	@org.springframework.data.jpa.repository.Query(
			"select coalesce(sum(s.transferValue), 0) from BranchSale s where s.branch.id = :branchId")
	java.math.BigDecimal totalTransferValue(@org.springframework.data.repository.query.Param("branchId") java.util.UUID branchId);
}
