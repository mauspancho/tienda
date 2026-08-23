package com.tienda.pos.product;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import org.springframework.stereotype.Service;

@Service
@NormalMode
public class BarcodeLabelService {

    private static final String[] CODE_128_PATTERNS = {
            "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
            "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
            "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
            "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
            "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
            "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
            "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
            "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
            "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
            "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
            "114131", "311141", "411131", "211412", "211214", "211232", "2331112"
    };

    private static final int START_CODE_B = 104;
    private static final int STOP = 106;
    private static final int QUIET_ZONE = 10;
    private static final int BAR_HEIGHT = 60;

    public String code128Svg(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException("El producto no tiene código de barras para imprimir.");
        }
        String barcode = value.trim();
        int[] codes = encodeCode128B(barcode);
        int totalModules = totalModules(codes) + (QUIET_ZONE * 2);
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(totalModules)
                .append(' ')
                .append(BAR_HEIGHT)
                .append("\" role=\"img\" aria-label=\"Código de barras\" preserveAspectRatio=\"none\">");
        int x = QUIET_ZONE;
        for (int code : codes) {
            String pattern = CODE_128_PATTERNS[code];
            boolean bar = true;
            for (int i = 0; i < pattern.length(); i++) {
                int width = Character.digit(pattern.charAt(i), 10);
                if (bar) {
                    svg.append("<rect x=\"").append(x).append("\" y=\"0\" width=\"")
                            .append(width).append("\" height=\"").append(BAR_HEIGHT).append("\"/>");
                }
                x += width;
                bar = !bar;
            }
        }
        svg.append("</svg>");
        return svg.toString();
    }

    private int[] encodeCode128B(String value) {
        int[] codes = new int[value.length() + 3];
        codes[0] = START_CODE_B;
        int checksum = START_CODE_B;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < 32 || character > 126) {
                throw new DomainException("El código de barras contiene caracteres no imprimibles.");
            }
            int code = character - 32;
            codes[i + 1] = code;
            checksum += code * (i + 1);
        }
        codes[value.length() + 1] = checksum % 103;
        codes[value.length() + 2] = STOP;
        return codes;
    }

    private int totalModules(int[] codes) {
        int total = 0;
        for (int code : codes) {
            String pattern = CODE_128_PATTERNS[code];
            for (int i = 0; i < pattern.length(); i++) {
                total += Character.digit(pattern.charAt(i), 10);
            }
        }
        return total;
    }
}
