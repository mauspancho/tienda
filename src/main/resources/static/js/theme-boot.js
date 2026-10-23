/*
 * theme-boot.js — se carga en el <head>, SIN defer, ANTES de la hoja de estilos.
 * Fija data-theme en <html> antes del primer render para evitar flashes
 * del tema por defecto (FOUC). Registra el tema con el mismo formato que
 * theme-switcher.js (localStorage "tienda-theme").
 */
(function () {
  "use strict";
  var storageKey = "tienda-theme";
  var themes = ["tienda", "sunset", "ocean", "forest"];
  var root = document.documentElement;

  // Respeta el tema fijado por la plantilla (login y setup usan sunset fijo).
  if (root.hasAttribute("data-theme")) {
    return;
  }

  var stored = null;
  try {
    stored = window.localStorage.getItem(storageKey);
  } catch (error) {
    // Sin localStorage: se queda el tema por defecto.
  }

  if (themes.indexOf(stored) !== -1) {
    root.setAttribute("data-theme", stored);
  } else {
    root.setAttribute("data-theme", "tienda");
  }
})();
