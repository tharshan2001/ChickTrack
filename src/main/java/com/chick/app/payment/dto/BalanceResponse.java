package com.chick.app.payment.dto;

import java.math.BigDecimal;

public record BalanceResponse(BigDecimal outstandingAmount) {
}
