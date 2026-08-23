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
          "primary": "#2563eb",
          "secondary": "#0f766e",
          "accent": "#16a34a",
          "neutral": "#111827",
          "base-100": "#ffffff",
          "base-200": "#f6f7fb",
          "base-300": "#d9e0ea",
          "info": "#2563eb",
          "success": "#16a34a",
          "warning": "#d97706",
          "error": "#dc2626"
        }
      }
    ]
  }
};