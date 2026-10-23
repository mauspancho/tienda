package com.tienda.pos.staticresources;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Verifica el sistema de temas: registro de temas, paletas, tokens y selector.
 * Un tema nuevo debe aparecer en los cuatro lugares: paleta (tailwind.config.js),
 * bloque de tokens (input.css), registro JS (theme-boot.js + theme-switcher.js)
 * y botones del selector (layout admin y plataforma).
 */
class ThemeSystemTest {

    private static final Path ROOT = Path.of("");

    /** Temas conocidos del sistema, incluido el más reciente (forest). */
    private static final List<String> THEMES = List.of("tienda", "sunset", "ocean", "forest");

    @Test
    void everyThemeIsRegisteredInBootAndSwitcherScripts() throws Exception {
        String boot = Files.readString(ROOT.resolve("src/main/resources/static/js/theme-boot.js"));
        String switcher = Files.readString(ROOT.resolve("src/main/resources/static/js/theme-switcher.js"));

        for (String theme : THEMES) {
            assertThat(boot).contains("\"" + theme + "\"");
            assertThat(switcher).contains("\"" + theme + "\"");
        }
    }

    @Test
    void everyThemeHasADaisyuiPaletteInTailwindConfig() throws Exception {
        String config = Files.readString(ROOT.resolve("tailwind.config.js"));

        for (String theme : THEMES) {
            assertThat(config).contains(theme + ": {");
        }
        // La primera paleta es la por defecto (sin data-theme): debe seguir siendo tienda.
        assertThat(config.indexOf("tienda: {")).isLessThan(config.indexOf("sunset: {"));
    }

    @Test
    void everyThemeHasATokenBlockInInputCss() throws Exception {
        String css = Files.readString(ROOT.resolve("src/main/resources/static/css/input.css"));

        // tienda es el tema por defecto y vive en :root; sunset, ocean y forest son bloques.
        for (String theme : List.of("sunset", "ocean", "forest")) {
            assertThat(css).contains("[data-theme=\"" + theme + "\"] {");
        }
        assertThat(css).contains("--color-background: #ECF9FF;"); // ocean
        assertThat(css).contains("--color-accent: #fcee8c;"); // forest
        assertThat(css).contains("--color-selection-bg: #f97316;"); // sunset
    }

    @Test
    void themeBlocksDefineOnlyTokensNotComponentRules() throws Exception {
        String css = Files.readString(ROOT.resolve("src/main/resources/static/css/input.css"));

        // Dentro de cada bloque [data-theme="..."] solo deben declararse tokens (--x: ...);
        // los componentes nunca se bifurcan por tema.
        Pattern blockPattern = Pattern.compile("\\[data-theme=\"\\w+\"\\] \\{([^}]*)}", Pattern.DOTALL);
        Matcher blocks = blockPattern.matcher(css);
        int found = 0;
        while (blocks.find()) {
            found++;
            String body = blocks.group(1);
            assertThat(body).as("el bloque de tema declara tokens: " + themeName(blocks.start(), css)).contains("--color");
            assertThat(body).doesNotMatch("(?m)^\\s*[.#a-zA-Z]");
        }
        // sunset, ocean y forest (tienda es :root y no tiene bloque)
        assertThat(found).isEqualTo(3);
    }

    private static String themeName(int start, String css) {
        Matcher m = Pattern.compile("\\[data-theme=\"(\\w+)\"\\]").matcher(css);
        while (m.find()) {
            if (m.start() >= start) {
                return m.group(1);
            }
        }
        return "?";
    }

    @Test
    void themeSelectorOffersEveryThemeInBothLayouts() throws Exception {
        String adminLayout = Files.readString(ROOT.resolve("src/main/resources/templates/fragments/layout.html"));
        String platformLayout = Files.readString(ROOT.resolve("src/main/resources/templates/platform/layout.html"));

        for (String theme : THEMES) {
            assertThat(adminLayout).contains("data-theme-option=\"" + theme + "\"");
            assertThat(platformLayout).contains("data-theme-option=\"" + theme + "\"");
        }
    }

    @Test
    void bootScriptRespectsTemplateFixedThemeAndStoresPreference() throws Exception {
        String boot = Files.readString(ROOT.resolve("src/main/resources/static/js/theme-boot.js"));

        assertThat(boot).contains("tienda-theme");
        assertThat(boot).contains("root.hasAttribute(\"data-theme\")");
    }

    @Test
    void loginAndSetupPagesKeepTheirFixedDarkTheme() throws Exception {
        // Los nombres visibles del selector están en ambos layouts y el tema fijado
        // de login/setup no es pisado por el boot (se respetan los[data-theme] existentes).
        String login = Files.readString(ROOT.resolve("src/main/resources/templates/auth/login.html"));
        String setup = Files.readString(ROOT.resolve("src/main/resources/templates/setup/index.html"));

        assertThat(login).contains("data-theme=\"sunset\"");
        assertThat(setup).contains("data-theme=\"sunset\"");
        assertThat(login).contains("theme-boot.js");
        assertThat(setup).contains("theme-boot.js");
    }
}
