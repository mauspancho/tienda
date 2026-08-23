package com.tienda.pos.sale;

import java.math.BigDecimal;

public record SaleResult(String folio, BigDecimal total, BigDecimal received, BigDecimal change) {
}
