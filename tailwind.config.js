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
          "success": "#16a34a",
          "warning": "#d97706",
          "error": "#dc2626"
        }
      },
      "sunset"
    ]
  }
};
