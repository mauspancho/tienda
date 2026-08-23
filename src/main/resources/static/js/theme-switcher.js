(function () {
  const storageKey = "tienda-theme";
  const defaultTheme = "tienda";
  const themes = ["tienda", "sunset"];

  function preferredTheme() {
    try {
      const stored = window.localStorage.getItem(storageKey);
      return themes.includes(stored) ? stored : defaultTheme;
    } catch (error) {
      return defaultTheme;
    }
  }

  function applyTheme(theme) {
    const nextTheme = themes.includes(theme) ? theme : defaultTheme;
    document.documentElement.setAttribute("data-theme", nextTheme);
    if (document.body) {
      document.body.setAttribute("data-theme", nextTheme);
    }
    return nextTheme;
  }

  function syncControls(theme) {
    document.querySelectorAll("[data-theme-select]").forEach((control) => {
      control.value = theme;
    });
  }

  const activeTheme = applyTheme(preferredTheme());

  document.addEventListener("DOMContentLoaded", function () {
    syncControls(activeTheme);
    document.querySelectorAll("[data-theme-select]").forEach((control) => {
      control.addEventListener("change", function (event) {
        const nextTheme = applyTheme(event.target.value);
        try {
          window.localStorage.setItem(storageKey, nextTheme);
        } catch (error) {
          // Browsers in private mode can reject localStorage writes.
        }
        syncControls(nextTheme);
      });
    });
  });
})();
