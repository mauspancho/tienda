package com.tienda.pos.product;

import com.tienda.pos.common.NormalMode;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@NormalMode
public class ProductBrandNormalizer {

    public String normalize(String value) {
        if (value == null) {
            return null;
        }
        String collapsed = value.strip().replaceAll("(?U)\\s+", " ");
        if (collapsed.isEmpty()) {
            return null;
        }

        StringBuilder result = new StringBuilder(collapsed.length());
        boolean startOfWord = true;
        for (int offset = 0; offset < collapsed.length();) {
            int codePoint = collapsed.codePointAt(offset);
            if (Character.isLetter(codePoint)) {
                String letter = new String(Character.toChars(codePoint));
                result.append(startOfWord ? letter.toUpperCase(Locale.ROOT) : letter.toLowerCase(Locale.ROOT));
                startOfWord = false;
            } else {
                result.appendCodePoint(codePoint);
                startOfWord = true;
            }
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    public String key(String value) {
        String normalized = normalize(value);
        return normalized == null ? "" : normalized.toLowerCase(Locale.ROOT);
    }
}
