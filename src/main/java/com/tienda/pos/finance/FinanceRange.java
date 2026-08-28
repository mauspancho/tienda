package com.tienda.pos.finance;

import java.time.LocalDate;

public record FinanceRange(LocalDate from, LocalDate to, String label, String period) {
}
