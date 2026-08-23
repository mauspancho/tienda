module.exports = {
  content: ["./src/main/resources/templates/**/*.html", "./src/main/resources/static/js/**/*.js"],
  theme: {
    extend: {}
  },
  plugins: [require("daisyui")],
  daisyui: {
    themes: [
      {
        tienda: {
          "color-scheme": "light",
          "primary": "#2563eb",
          "primary-content": "#ffffff",
          "secondary": "#0f766e",
          "secondary-content": "#ffffff",
          "accent": "#16a34a",
          "accent-content": "#052e16",
          "neutral": "#111827",
          "neutral-content": "#f8fafc",
          "base-100": "#ffffff",
          "base-200": "#f6f7fb",
          "base-300": "#d9e0ea",
          "base-content": "#162033",
          "info": "#2563eb",
          "info-content": "#ffffff",
          "success": "#16a34a",
          "success-content": "#052e16",
          "warning": "#d97706",
          "warning-content": "#111827",
          "error": "#dc2626",
          "error-content": "#ffffff"
        }
      },
      {
        sunset: {
          "color-scheme": "dark",
          "primary": "#f97316",
          "primary-content": "#111018",
          "secondary": "#fb923c",
          "secondary-content": "#111018",
          "accent": "#22c55e",
          "accent-content": "#052e16",
          "neutral": "#181420",
          "neutral-content": "#fff7ed",
          "base-100": "#181420",
          "base-200": "#111018",
          "base-300": "#2b2433",
          "base-content": "#fff7ed",
          "info": "#93c5fd",
          "info-content": "#0f172a",
          "success": "#22c55e",
          "success-content": "#052e16",
          "warning": "#f97316",
          "warning-content": "#111018",
          "error": "#ef4444",
          "error-content": "#fff1f2"
        }
      }
    ]
  }
};
