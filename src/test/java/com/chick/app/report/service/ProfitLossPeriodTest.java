package com.chick.app.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chick.app.common.exception.BusinessException;
import com.chick.app.report.service.ProfitLossService.GroupBy;
import com.chick.app.report.service.ProfitLossService.Period;
import com.chick.app.report.service.ProfitLossService.Range;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ProfitLossPeriodTest {

	private final ProfitLossService service = new ProfitLossService(null, null, null, null, null, null);

	@Test
	void dailyIsOneDayWithoutBreakdown() {
		Range range = service.resolve("daily", LocalDate.of(2026, 11, 5), null, null, null, null, null);
		assertThat(range).isEqualTo(new Range(Period.DAILY, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5), GroupBy.NONE));
	}

	@Test
	void monthlyFromDateOrMonthGroupsByDay() {
		Range byDate = service.resolve("monthly", LocalDate.of(2024, 2, 10), null, null, null, null, null);
		assertThat(byDate).isEqualTo(new Range(Period.MONTHLY, LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29), GroupBy.DAY));
		Range byMonth = service.resolve(null, null, "2026-11", null, null, null, "none");
		assertThat(byMonth).isEqualTo(new Range(Period.MONTHLY, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), GroupBy.NONE));
	}

	@Test
	void yearlyGroupsByMonth() {
		Range range = service.resolve(null, null, null, 2026, null, null, null);
		assertThat(range).isEqualTo(new Range(Period.YEARLY, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), GroupBy.MONTH));
	}

	@Test
	void customNeedsBothEndsInOrder() {
		Range range = service.resolve(null, null, null, null, LocalDate.of(2026, 1, 15), LocalDate.of(2026, 3, 10), "month");
		assertThat(range).isEqualTo(new Range(Period.CUSTOM, LocalDate.of(2026, 1, 15), LocalDate.of(2026, 3, 10), GroupBy.MONTH));
		assertThatThrownBy(() -> service.resolve("custom", null, null, null, LocalDate.of(2026, 1, 1), null, null))
				.isInstanceOf(BusinessException.class).hasMessageContaining("'from' and 'to'");
		assertThatThrownBy(() -> service.resolve("custom", null, null, null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null))
				.isInstanceOf(BusinessException.class).hasMessageContaining("after");
	}

	@Test
	void rejectsBadValuesAndTooManyBuckets() {
		assertThatThrownBy(() -> service.resolve("weekly", null, null, null, null, null, null))
				.isInstanceOf(BusinessException.class).hasMessageContaining("daily, monthly, yearly, custom");
		assertThatThrownBy(() -> service.resolve(null, null, "Nov-2026", null, null, null, null))
				.isInstanceOf(BusinessException.class).hasMessageContaining("2026-11");
		assertThatThrownBy(() -> service.resolve("custom", null, null, null, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 1), "day"))
				.isInstanceOf(BusinessException.class).hasMessageContaining("coarser");
	}
}
