package com.chick.app.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Business dates are Sri Lankan calendar days; timestamps are stored in UTC. */
public final class BusinessTime {

	public static final ZoneId ZONE = ZoneId.of("Asia/Colombo");

	private BusinessTime() {
	}

	public static LocalDate today() {
		return LocalDate.now(ZONE);
	}

	/** First instant of the business day. */
	public static Instant startOf(LocalDate date) {
		return date.atStartOfDay(ZONE).toInstant();
	}

	/** Last instant of the business day (inclusive). */
	public static Instant endOf(LocalDate date) {
		return startOf(date.plusDays(1)).minusNanos(1);
	}

	public static LocalDate dateOf(Instant instant) {
		return instant.atZone(ZONE).toLocalDate();
	}
}
