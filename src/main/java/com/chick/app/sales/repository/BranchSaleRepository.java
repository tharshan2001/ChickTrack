package com.chick.app.sales.repository;

import com.chick.app.branch.domain.Branch;
import com.chick.app.sales.domain.BranchSale;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchSaleRepository
		extends JpaRepository<BranchSale, UUID>, JpaSpecificationExecutor<BranchSale> {

	Optional<BranchSale> findByBranchAndSaleDate(Branch branch, LocalDate saleDate);

	/** Weight that left the branch per chicken type on days closed before {@code date}. */
	@Query("""
			select item.chickenType.id, sum(item.weightKg)
			from BranchSaleItem item
			where item.branchSale.branch.id = :branchId and item.branchSale.saleDate < :date
			group by item.chickenType.id
			""")
	List<Object[]> soldWeightBefore(@Param("branchId") UUID branchId, @Param("date") LocalDate date);

	/** Weight that left the branch per chicken type on every closed day. */
	@Query("""
			select item.chickenType.id, sum(item.weightKg)
			from BranchSaleItem item
			where item.branchSale.branch.id = :branchId
			group by item.chickenType.id
			""")
	List<Object[]> soldWeight(@Param("branchId") UUID branchId);

	/** Latest closed day of a branch after {@code date}, if any. */
	Optional<BranchSale> findFirstByBranchIdAndSaleDateAfterOrderBySaleDateDesc(UUID branchId, LocalDate date);

	List<BranchSale> findByBranchIdOrderBySaleDateAsc(UUID branchId);

	@Query("select coalesce(sum(s.totalSales), 0) from BranchSale s where s.branch.id = :branchId")
	BigDecimal totalSales(@Param("branchId") UUID branchId);
}
