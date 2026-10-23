# Sistema de temas y tokens de diseño — Tienda POS

Este documento describe la arquitectura centralizada de estilos del POS, dónde
está la fuente de verdad visual y los pasos exactos para **añadir un tema nuevo**
(por ejemplo: Oscuro, Océano, Esmeralda) sin tocar plantillas ni componentes.

## 1. Fundamentos

| Capa | Archivo | Responsabilidad |
|------|---------|-----------------|
| Paleta daisyUI | `tailwind.config.js` | Colores base por tema (`primary`, `base-100`, `error`, …) destinados a los componentes daisyUI (`btn`, `badge`, `alert`, `menu`, …) vía canales `--p`, `--b1`, `--bc`, … |
| Tokens semánticos | `src/main/resources/static/css/input.css` (bloques `:root` y `[data-theme="…"]` al inicio) | Colores semánticos (`--color-primary`, `--color-surface`, `--color-border`, …) consumidos por TODO el CSS propio |
| Componentes | `input.css` (secciones `LAYOUT`, `COMPONENTES`, módulos) | Estilos estructurales; **nunca** definen colores por tema, solo consumen tokens |
| Módulos con CSS propio | `static/css/platform.css`, `credits.css`, `whatsapp*.css` | Módulos autocontenidos (ver §7 excepciones) |
| Cambio de tema | `static/js/theme-switcher.js` + `static/js/theme-boot.js` | Selección, persistencia y aplicación del tema |
| Build | `npm run css:build` | Compila `input.css` → `app.css` (Tailwind 3 + daisyUI 4) |

Reglas del sistema:

- Los componentes consumen `var(--token)`; no escriben hexadecimales por tema.
- Cada tema vive **única y exclusivamente** en un bloque `[data-theme="nombre"]`
  que redefiene solo tokens.
- El tema **Claro (`tienda`)** se hereda de `:root`: sus tokens son los valores
  por defecto. Cualquier página sin `data-theme` se ve Claro de forma segura.
- Sunset y cualquier otro tema solo añaden su bloque de tokens.

## 2. Tokens principales (`input.css`, bloques iniciales)

Agrupados por categoría en los comentarios del archivo:

- **Superficies**: `--color-background`, `--canvas-gradient` (decoración del
  lienzo), `--color-surface`, `--color-surface-elevated` (menús flotantes),
  `--color-surface-muted`, `--color-nav`, `--color-header`, `--color-sidebar*`.
- **Texto**: `--color-text`, `--color-text-muted`, `--color-text-inverse`,
  `--color-heading`.
- **Identidad**: `--color-primary[-hover|-active|-contrast]`, `--color-secondary`,
  `--color-accent`.
- **Feedback**: `--color-{success,warning,danger,info}` con sufijos
  `-surface` (fondo), `-text` y `-border`.
- **Interfaz**: `--color-border[-soft|-strong]`, `--color-focus-ring`,
  `--color-link[-hover]`, `--color-input*`, `--color-table-header[-text]`,
  `--color-table-row-hover`, `--color-overlay`.
- **Menus/selección**: `--menu-hover-bg/-text`, `--color-selection-*`,
  `--shadow-selection`, `--autocomplete-*`, `--theme-menu-*`.
- **Gráficos** (Chart.js): `--chart-text`, `--chart-grid`, `--chart-grid-strong`,
  `--chart-point`, `--chart-fill`, `--chart-tooltip-*` y las series constantes
  `--series-a…d` (datos, no decoración).
- **Botones**: `--btn-muted-bg/-text`, `--quick-pos-border`.
- **Escalas**: `--radius-round|sm|md|lg|pill`, `--shadow-card|nav|pop|modal|login-card`,
  `--transition-fast|base`, `--font-sans`, `--panel-backdrop`.

Un token definido en `:root` (equivalencias `:root` = Claro):

```css
--color-primary: #2563eb;
--color-surface: #ffffff;
```

Su redefinición en Sunset:

```css
[data-theme="sunset"] {
  --color-primary: #f97316;
  --color-surface: rgba(28, 24, 37, .98);
}
```

## 3. Cambio y persistencia del tema

- **Atributo raíz**: `data-theme` en `<html>` (y en `<body>` por compatibilidad).
- **Aplicación temprana**: cada plantilla carga `<script th:src="@{/js/theme-boot.js}">`
  **antes** de `app.css`; el script lee `localStorage` y fija `data-theme` antes del
  primer render → no hay destello de tema incorrecto (FOUC).
- **Persistencia**: clave `tienda-theme` en `localStorage`. Valores válidos:
  `tienda`, `sunset`.
- **UI**: dropdown de temas en el layout (`templates/fragments/layout.html`) y en el
  módulo plataforma (`templates/platform/layout.html`), ambos con botones
  `data-theme-option`.
- **Eventos**: `theme-switcher.js` dispara `tienda:theme-changed`
  (`event.detail.theme`). Las vistas con Chart.js re-tematizan sus gráficos
  escuchando ese evento y leyendo tokens con `getComputedStyle`.
- **Registro programático**: `window.TiendaThemes` expone `themes`,
  `current()`, `apply(theme)` y `set(theme)`.

## 4. Cómo añadir un tema nuevo — el caso real: «Ocean»

El tema **Ocean** (azul/cian, añadido como tercer tema) sigue exactamente esta
receta, útil para cualquier tema futuro (p. ej. Oscuro, Esmeralda):

1. **Paleta daisyUI** en `tailwind.config.js` → `daisyui.themes`, añadiendo un
   objeto con los colores del tema:

   ```js
   {
     ocean: {
       "color-scheme": "light",
       "primary": "#00799A", "primary-content": "#ffffff",
       "secondary": "#00B3E2", "secondary-content": "#002430",
       "accent": "#0095BD", "accent-content": "#002430",
       "neutral": "#004B61", "neutral-content": "#ECF9FF",
       "base-100": "#ffffff", "base-200": "#ECF9FF", "base-300": "#A1E4FF",
       "base-content": "#002430",
       /* … info, success, warning, error … */
     }
   }
   ```

   daisyUI genera automáticamente `[data-theme="ocean"]` con los canales
   `--p`, `--b1`, `--bc` para todos sus componentes (`btn`, `badge`, `menu`, …).

2. **Bloque de tokens** en `input.css`, copiando la estructura de un tema
   existente y sustituyendo solo los valores:

   ```css
   [data-theme="ocean"] {
     --color-background: #ECF9FF;
     --color-surface: #FFFFFF;
     --color-surface-muted: #D0F1FF;
     --color-primary: #00799A;
     /* … resto de tokens, copiando la estructura del bloque elegido … */
   }
   ```

   El resto de tokens que no se redefinan se heredan de `:root` (Claro).

3. **Registro** en **`static/js/theme-switcher.js`** y **`static/js/theme-boot.js`**
   (ambos guardan la lista de temas válidos):

   ```js
   const themes = ["tienda", "sunset", "ocean", "forest"];
   ```

4. **Botón en la UI** (una línea en cada dropdown — admin y plataforma):

   ```html
   <li><button type="button" data-theme-option="ocean">Ocean</button></li>
   ```

   Temas añadidos con esta receta: **Ocean** (azul/cian) y **Forest**
   (verde bosque). En Forest no fue necesario tocar ni una regla de
   componente; solo se tomó la decisión de contraste documentada en su bloque:
   `#40957f` no alcanza 4.5:1 con texto blanco sobre verde (3.61:1), por lo que
   `--color-primary` usa `#3d6868` (6.22:1) y `#40957f` queda como acento
   gráfico (bordes fuertes, anillo de foco, relleno de gráficos, ≥ 3:1).

Con eso, **todas** las vistas (tablas, formularios, tarjetas, modales, POS,
cámara, gráficos) adoptan el tema automáticamente: ningún selector de
componente depende del tema activo.

## 5. Cómo escribir CSS nuevo compatible con temas

```css
/* BIEN — token semántico */
.mi-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  color: var(--color-text);
}
.mi-accion { background: var(--color-primary); color: var(--color-primary-contrast); }

/* BIEN — derivación local a partir de tokens solo cuando aporta valor */
.mi-tono { background: color-mix(in srgb, var(--color-surface) 88%, #000 6%); }

/* MAL — color literal o branch por tema */
.mi-panel { background: #181420; }
html[data-theme="sunset"] .mi-panel { background: #191b22; }
```

Matices a respetar:

- Para elementos que deben verse **identical en ambos temas** (texto sobre
  gradientes, guías de cámara, banner de impresión) usa un literal y déjalo
  comentado como excepción constant (ver §7).
- Las **series de gráficos** (`--series-a…d`) son datos, no decoración: se
  mantienen constantes entre temas.
- No duplicar exhaustivamente todos los estados hover/active por tema: los
  tokens `-hover`, `-active`, `-focus-ring` ya cubren los estados.

## 6. Validaciones del sistema (hechas en la refactorización)

- `npm run css:build` compila sin errores y el CSS resultante ya no contiene
  reglas `html[data-theme="tienda/sunset"] <componente>`.
- Toda la identidad anterior de Claro y Sunset se preserva (los valores de los
  tokens se extrajeron de las reglas por-tema previas; las únicas
  normalizaciones menores están en §7.3).
- Claro y Sunset seleccionables, persistentes (`tienda-theme`) y sin FOUC
  gracias a `theme-boot.js`.

## 7. Excepciones documentadas (colores que NO usan tokens del tema)

7.1. **Recursos de identidad constante**
- `.ticket` (ticket de impresión): fondo blanco/imprenta, siempre negro
  sobre blanco — es una salida física.
- `.brand-mark` / badge «TP» del logo: verde institucional #22c55e con texto
  #052e16 en ambos temas (logo).
- Banner de éxito del escáner `.camera-scan-success`: gradiente #16a34a→#22c55e
  con texto blanco constante; debe garantizar contraste sobre su propio fondo.
- Indicadores de estado del escáner `.camera-status` (`#22c55e`/`#f87171`), guía de
  encuadre `.camera-guide` y fondo de vídeo `.camera-preview` (#050812):
  indicadores coloreados sobre vídeo; constantes entre temas por legibilidad.

7.2. **Módulos autocontenidos**
- **Catálogo público** (`templates/catalog/*`, clase `.catalog-*` en `input.css`):
  tienda en línea con diseño propio e independiente; sus rutas están
  deshabilitadas en producción (404) y el CSS es autónomo — no se integra al
  sistema de temas a propósito.
- **Etiquetas de código de barras** (`templates/products/barcode-label.html`):
  página de impresión, superficie constante.
- **`platform.css`**: el módulo de administración de plataforma define sus
  propios tokens `--platform-*` (patrón equivalente, alcance local) y sus
  variantes `[data-theme]` solo dentro del bloque de tokens del módulo.
- **`credits.css` / `whatsapp*.css`**: módulos pequeños que ya consumen
  canales daisyUI o tokens; sin reglas por tema propias.

7.3. **Normalizaciones menores respecto a la versión anterior**
- `--color-text-muted` de Sunset unificado en `rgba(255,247,237,.76)` (antes
  había tres variantes .76/.78/.82 para el mismo rol semántico).
- Translucidez de paneles flotantes y menús unificada en `--color-surface-elevated`
  y `--panel-backdrop` (blur/saturación por tema en un solo token).
- Estados `checked` de acceso rápido de usuarios y toggle de WhatsApp ahora usan
  `--color-selection-*` / `--color-success*`, con el matiz esperado por tema.

## 8. Errores comunes a evitar

- No añadir un bloque `[data-theme="…"]` nuevo anidado en módulos: si un estilo
  necesita variar por tema, añade/use tokens; el bloque del tema es el único
  lugar donde cambian.
- No usar `!important` para «ganar» a otro tema: con tokens no hay peleas de
  cascada.
- Al añadir un tema, recordar actualizar la lista de temas en los **dos** JS
  (`theme-boot.js` y `theme-switcher.js`) y el dropdown en los dos layouts
  (admin y plataforma).
- `app.css` es un artefacto de build: tras cambiar `input.css` ejecutar
  `npm run css:build` (o `css:watch` en desarrollo).
