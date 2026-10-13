(() => {
  const paymentForm = document.querySelector('form[action$="/payments"]');
  const layout = document.querySelector(".credit-detail-grid");
  if (!paymentForm || !layout) return;

  const action = paymentForm.getAttribute("action").replace(/\/payments$/, "/charges");
  const csrf = paymentForm.querySelector('input[name="_csrf"]');
  const form = document.createElement("form");
  form.className = "card form";
  form.method = "post";
  form.action = action;
  form.innerHTML = `
    <h2>Agregar dinero al crédito</h2>
    <div class="field"><label>Importe</label><input name="amount" type="number" min="0.01" step="0.01" required></div>
    <div class="field"><label>Concepto</label><input name="concept" maxlength="255" placeholder="Ej. Préstamo en efectivo o saldo anterior" required></div>
    <button class="btn btn-primary" type="submit">Agregar al adeudo</button>
    <p class="muted">Este cargo aumenta el saldo del cliente sin crear una venta ni modificar inventario o caja.</p>`;
  if (csrf) form.appendChild(csrf.cloneNode(true));
  form.addEventListener("submit", () => {
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    button.textContent = "Guardando...";
  });
  layout.insertBefore(form, layout.children[1] || null);
})();
