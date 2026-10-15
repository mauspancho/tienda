(() => {
  const modal = document.querySelector("[data-manual-promotion-modal]");
  const form = document.querySelector("[data-manual-promotion-form]");
  if (!modal || !form) return;

  const message = form.querySelector("[data-manual-promotion-message]");
  const preview = form.querySelector("[data-manual-promotion-preview]");
  const count = form.querySelector("[data-manual-promotion-count]");
  const key = form.querySelector("[data-manual-promotion-key]");
  const mode = form.querySelector("[data-manual-promotion-mode]");
  const phoneField = form.querySelector("[data-manual-promotion-phone-field]");
  const phone = form.querySelector("[data-manual-promotion-phone]");
  const groups = modal.querySelector("[data-manual-promotion-groups]");
  const groupWarning = modal.querySelector("[data-manual-promotion-group-warning]");
  const individualWarning = form.querySelector("[data-manual-promotion-individual-warning]");
  const destinationHelp = modal.querySelector("[data-manual-promotion-destination-help]");
  const submit = form.querySelector("[data-manual-promotion-submit]");
  const submitLabel = form.querySelector("[data-manual-promotion-submit-label]");
  const spinner = form.querySelector("[data-manual-promotion-spinner]");
  const result = form.querySelector("[data-manual-promotion-result]");
  const csrfToken = document.querySelector("meta[name='_csrf']")?.content || "";
  const csrfHeader = document.querySelector("meta[name='_csrf_header']")?.content || "X-CSRF-TOKEN";
  const groupReady = form.dataset.groupReady === "true";
  const individualReady = form.dataset.individualReady === "true";

  const newKey = () => {
    if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID().replaceAll("-", "");
    const values = new Uint32Array(4);
    if (globalThis.crypto?.getRandomValues) globalThis.crypto.getRandomValues(values);
    else values.forEach((_, index) => { values[index] = Math.floor(Math.random() * 0xffffffff); });
    return `manual_${Date.now().toString(36)}_${Array.from(values, value => value.toString(36)).join("")}`;
  };
  const refreshPreview = () => {
    preview.textContent = message.value;
    count.textContent = String(message.value.length);
  };
  const refreshMode = () => {
    const individual = mode.checked;
    phoneField.hidden = !individual;
    phone.required = individual;
    groups.hidden = individual || !groupReady;
    groupWarning.hidden = individual || groupReady;
    individualWarning.hidden = !individual || individualReady;
    destinationHelp.textContent = individual
      ? "Envío inmediato y exclusivo a un número individual."
      : "Envío inmediato a los grupos autorizados de esta tienda.";
    submit.disabled = individual ? !individualReady : !groupReady;
  };
  const open = () => {
    if (!key.value) key.value = newKey();
    refreshPreview();
    refreshMode();
    modal.showModal();
  };
  const close = () => modal.close();
  document.querySelector("[data-manual-promotion-open]")?.addEventListener("click", open);
  modal.querySelector("[data-manual-promotion-close]")?.addEventListener("click", close);
  modal.querySelector("[data-manual-promotion-cancel]")?.addEventListener("click", close);
  message.addEventListener("input", refreshPreview);
  mode.addEventListener("change", refreshMode);

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const individual = mode.checked;
    const productName = form.dataset.productName || "este producto";
    const destination = phone.value.trim();
    const confirmation = individual
      ? `¿Deseas enviar ${productName} exclusivamente al número ${destination}?`
      : `¿Deseas enviar ${productName} a los grupos seleccionados de WhatsApp?`;
    if (!window.confirm(confirmation)) return;

    submit.disabled = true;
    spinner.hidden = false;
    submitLabel.textContent = "Enviando...";
    result.hidden = true;
    const body = new URLSearchParams(new FormData(form));
    try {
      const response = await fetch(form.action, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8", [csrfHeader]: csrfToken },
        body
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || "No fue posible enviar la promoción.");
      const failures = (data.groups || []).filter(recipient => recipient.status === "FAILED");
      result.className = failures.length ? "alert alert-warning" : "alert alert-success";
      const summary = individual
        ? (data.accepted > 0
            ? "Promoción aceptada correctamente por WhatsApp."
            : "No fue posible enviar la promoción. Revisa el número o la conexión de WhatsApp.")
        : `Promoción procesada: ${data.accepted} grupos aceptados, ${data.failed} grupos con error.`;
      result.innerHTML = `<strong>${escapeHtml(summary)}</strong>`
        + (data.accepted > 0 ? "<p>La solicitud fue aceptada por WPPConnect; esto no confirma todavía la entrega al destinatario.</p>" : "")
        + (data.processing ? "<p>La misma solicitud ya se está procesando.</p>" : "")
        + (failures.length ? `<ul>${failures.map(recipient => `<li>${escapeHtml(recipient.groupName)}: ${escapeHtml(recipient.error || "Error de envío")}</li>`).join("")}</ul>` : "");
      result.hidden = false;
      if (!failures.length && !data.processing) key.value = newKey();
    } catch (error) {
      result.className = "alert alert-error";
      result.textContent = error.message;
      result.hidden = false;
    } finally {
      spinner.hidden = true;
      submitLabel.textContent = "Enviar promoción";
      refreshMode();
    }
  });

  function escapeHtml(value) {
    const span = document.createElement("span");
    span.textContent = value || "";
    return span.innerHTML;
  }
})();
