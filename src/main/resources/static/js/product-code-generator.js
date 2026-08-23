(() => {
  const buttons = document.querySelectorAll("[data-generate-code]");
  if (!buttons.length) return;

  async function generate(type) {
    const response = await fetch(`/products/generate-code?type=${encodeURIComponent(type)}`, {
      headers: { "Accept": "application/json" },
      credentials: "same-origin"
    });
    if (!response.ok) {
      throw new Error("No fue posible generar el código.");
    }
    return response.json();
  }

  buttons.forEach(button => {
    button.addEventListener("click", async () => {
      const type = button.dataset.generateCode;
      const input = document.querySelector(`[data-code-target="${type}"]`);
      const originalText = button.textContent;
      button.disabled = true;
      button.textContent = "...";
      try {
        const result = await generate(type);
        input.value = result.value;
        input.focus();
      } catch (error) {
        window.alert(error.message);
      } finally {
        button.disabled = false;
        button.textContent = originalText;
      }
    });
  });
})();
