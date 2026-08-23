module.exports = {
  content: ["./src/main/resources/templates/**/*.html", "./src/main/resources/static/js/**/*.js"],
  theme: {
    extend: {}
  },
  plugins: [require("daisyui")],
  daisyui: {
    themes: [
      "dark",
      "forest",
      "sunset",
      "halloween",
      {
        tienda: {
          "color-scheme": "dark",
          "primary": "#2563eb",
          "secondary": "#0f766e",
          "accent": "#16a34a",
          "neutral": "#111827",
          "base-100": "#101828",
          "base-200": "#0b1220",
          "base-300": "#1f2937",
          "base-content": "#e5edf6",
          "info": "#2563eb",
          "success": "#16a34a",
          "warning": "#d97706",
          "error": "#dc2626"
        }
      },
      {
        abyss: {
          "color-scheme": "dark",
          "primary": "#67e8f9",
          "secondary": "#818cf8",
          "accent": "#22c55e",
          "neutral": "#030712",
          "base-100": "#050816",
          "base-200": "#0b1020",
          "base-300": "#111827",
          "base-content": "#e5edf6",
          "info": "#38bdf8",
          "success": "#22c55e",
          "warning": "#f59e0b",
          "error": "#ef4444"
        }
      }
    ]
  }
};
