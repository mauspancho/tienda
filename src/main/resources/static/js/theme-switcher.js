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
    document.dispatchEvent(new CustomEvent("tienda:theme-changed", { detail: { theme: nextTheme } }));
    return nextTheme;
  }

  function syncControls(theme) {
    document.querySelectorAll("[data-theme-select]").forEach((control) => {
      control.value = theme;
    });
    document.querySelectorAll("[data-theme-option]").forEach((control) => {
      const active = control.dataset.themeOption === theme;
      control.classList.toggle("active", active);
      control.setAttribute("aria-current", active ? "true" : "false");
    });
  }

  const activeTheme = applyTheme(preferredTheme());

  document.addEventListener("DOMContentLoaded", function () {
    syncControls(activeTheme);
    document.querySelectorAll("[data-theme-select]").forEach((control) => {
      control.addEventListener("change", function (event) {
        setTheme(event.target.value);
      });
    });
    document.querySelectorAll("[data-theme-option]").forEach((control) => {
      control.addEventListener("click", function (event) {
        setTheme(event.currentTarget.dataset.themeOption);
        event.currentTarget.closest("details")?.removeAttribute("open");
      });
    });
  });

  function setTheme(theme) {
    const nextTheme = applyTheme(theme);
    try {
      window.localStorage.setItem(storageKey, nextTheme);
    } catch (error) {
      // Browsers in private mode can reject localStorage writes.
    }
    syncControls(nextTheme);
  }
})();
