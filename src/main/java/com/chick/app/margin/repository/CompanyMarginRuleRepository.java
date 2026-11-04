package com.chick.app.margin.repository;

import com.chick.app.margin.domain.CompanyMarginRule;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CompanyMarginRuleRepository
		extends JpaRepository<CompanyMarginRule, UUID>, JpaSpecificationExecutor<CompanyMarginRule> {
}
