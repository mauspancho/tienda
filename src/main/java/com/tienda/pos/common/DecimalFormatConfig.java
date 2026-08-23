package com.tienda.pos.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.Formatter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Locale;

@Configuration
public class DecimalFormatConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addFormatter(new Formatter<BigDecimal>() {
            @Override
            public BigDecimal parse(String text, Locale locale) throws ParseException {
                if (text == null || text.isBlank()) {
                    return BigDecimal.ZERO.setScale(MoneyUtils.SCALE, MoneyUtils.ROUNDING);
                }
                String normalized = text.trim()
                        .replace("$", "")
                        .replace("%", "")
                        .replace(",", "");
                try {
                    return new BigDecimal(normalized).setScale(MoneyUtils.SCALE, MoneyUtils.ROUNDING);
                } catch (NumberFormatException ex) {
                    ParseException parseException = new ParseException("Invalid decimal value", 0);
                    parseException.initCause(ex);
                    throw parseException;
                }
            }

            @Override
            public String print(BigDecimal object, Locale locale) {
                return MoneyUtils.money(object).toPlainString();
            }
        });
    }
}
