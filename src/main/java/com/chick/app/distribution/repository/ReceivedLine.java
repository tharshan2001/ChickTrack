package com.chick.app.distribution.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Weight a branch received on one distribution line, at that line's transfer price. */
public record ReceivedLine(UUID chickenTypeId, Instant receivedAt, BigDecimal weightKg, BigDecimal transferPricePerKg) {
}
