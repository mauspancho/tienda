package com.tienda.pos.common;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

@Component("display")
public class DisplayFormat {

    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.US);

    public String money(BigDecimal value) {
        return "$" + number(value);
    }

    public String number(BigDecimal value) {
        return decimalFormat().format(MoneyUtils.money(value));
    }

    public String percent(BigDecimal value) {
        return number(value) + "%";
    }

    private DecimalFormat decimalFormat() {
        DecimalFormat format = new DecimalFormat("#,##0.00", SYMBOLS);
        format.setParseBigDecimal(true);
        format.setMinimumFractionDigits(MoneyUtils.SCALE);
        format.setMaximumFractionDigits(MoneyUtils.SCALE);
        return format;
    }
}
