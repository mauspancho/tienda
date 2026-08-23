package com.tienda.pos.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtils {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private MoneyUtils() {
    }

    public static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(SCALE, ROUNDING) : value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal marginPercent(BigDecimal salePrice, BigDecimal purchaseCost) {
        BigDecimal price = money(salePrice);
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        return price.subtract(money(purchaseCost)).multiply(ONE_HUNDRED).divide(price, SCALE, ROUNDING);
    }
}
