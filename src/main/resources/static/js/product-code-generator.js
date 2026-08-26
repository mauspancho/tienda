(() => {
  const money = value => new Intl.NumberFormat("es-MX", { style: "currency", currency: "MXN", minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value || 0);
  const number = value => new Intl.NumberFormat("es-MX", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value || 0);

  const buttons = document.querySelectorAll("[data-generate-code]");
  const lookupCard = document.querySelector("[data-product-lookup]");
  const lookupInput = document.querySelector("[data-product-lookup-input]");
  const lookupMessage = document.querySelector("[data-product-lookup-message]");
  const localBox = document.querySelector("[data-local-product]");
  const externalBox = document.querySelector("[data-external-product]");
  const form = document.querySelector("[data-product-form]");
  const costInput = document.querySelector("[data-cost-input]");
  const priceInput = document.querySelector("[data-price-input]");
  const profitPreview = document.querySelector("[data-profit-preview]");
  const marginPreview = document.querySelector("[data-margin-preview]");
  const imageUrlInput = document.querySelector("[data-product-image-url]");
  const imageFileInput = document.querySelector("[data-product-image-file]");
  const imageRemoveInput = document.querySelector("[data-product-remove-image]");
  const imageClearButton = document.querySelector("[data-product-image-clear]");
  const imageFileInfo = document.querySelector("[data-product-image-file-info]");
  let previewObjectUrl = null;

  async function generate(type) {
    const response = await fetch(`/admin/products/generate-code?type=${encodeURIComponent(type)}`, {
      headers: { "Accept": "application/json" },
      credentials: "same-origin"
    });
    if (!response.ok) {
      throw new Error("No fue posible generar el código.");
    }
    return response.json();
  }

  async function ensureProductCode() {
    const codeInput = document.querySelector('[data-code-target="code"]');
    if (!codeInput || codeInput.value.trim()) return;
    const result = await generate("code");
    codeInput.value = result.value;
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

  function setMessage(text, kind = "success") {
    if (!lookupMessage) return;
    lookupMessage.hidden = false;
    lookupMessage.textContent = text;
    lookupMessage.className = kind === "error" ? "alert alert-error" : "alert alert-success";
  }

  function hideLookupResults() {
    if (localBox) localBox.hidden = true;
    if (externalBox) externalBox.hidden = true;
  }

  function setField(selector, value) {
    const input = document.querySelector(selector);
    if (input && value) input.value = value;
  }

  function revokePreviewObjectUrl() {
    if (previewObjectUrl) {
      URL.revokeObjectURL(previewObjectUrl);
      previewObjectUrl = null;
    }
  }

  function isPersistableImageUrl(url) {
    return url.startsWith("https://") || url.startsWith("/uploads/products/");
  }

  function setRemoveImage(value) {
    if (imageRemoveInput) imageRemoveInput.checked = value;
  }

  function updateImagePreview(url, temporary = false) {
    const preview = document.querySelector("[data-image-preview]");
    const image = document.querySelector("[data-image-preview-img]");
    if (!preview || !image) return;
    if (!temporary) revokePreviewObjectUrl();
    if (!url || (!temporary && !isPersistableImageUrl(url))) {
      preview.hidden = true;
      image.removeAttribute("src");
      return;
    }
    image.src = url;
    preview.hidden = false;
  }

  function formatBytes(bytes) {
    if (!Number.isFinite(bytes)) return "0 KB";
    if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
    return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }

  function showSelectedImage(file) {
    revokePreviewObjectUrl();
    if (!file) return;
    previewObjectUrl = URL.createObjectURL(file);
    updateImagePreview(previewObjectUrl, true);
    setRemoveImage(false);
    if (imageFileInfo) imageFileInfo.textContent = `${file.name} · ${formatBytes(file.size)}. Se optimizará al guardar.`;
  }

  function clearSelectedImage() {
    revokePreviewObjectUrl();
    if (imageFileInput) imageFileInput.value = "";
    if (imageUrlInput) imageUrlInput.value = "";
    setRemoveImage(true);
    updateImagePreview("");
    if (imageFileInfo) imageFileInfo.textContent = "Imagen marcada para quitar. Puedes elegir otra antes de guardar.";
  }

  function showCategorySuggestion(result) {
    const suggestion = document.querySelector("[data-category-suggestion]");
    const categorySelect = document.querySelector("[data-product-category]");
    if (!suggestion) return;
    if (!result.categorySuggestion) {
      suggestion.hidden = true;
      return;
    }
    suggestion.hidden = false;
    suggestion.textContent = `Categoría sugerida: ${result.categorySuggestion}`;
    if (result.categoryId && categorySelect) {
      categorySelect.value = String(result.categoryId);
    }
  }

  function showLocalProduct(result) {
    hideLookupResults();
    if (!localBox) return;
    localBox.hidden = false;
    localBox.querySelector("[data-local-product-name]").textContent = result.name || "Producto registrado";
    localBox.querySelector("[data-local-product-code]").textContent = result.code || result.barcode || "";
    localBox.querySelector("[data-local-product-stock]").textContent = number(result.stock || 0);
    localBox.querySelector("[data-local-product-price]").textContent = money(result.price || 0);
    localBox.querySelector("[data-local-view]").href = `/admin/products?q=${encodeURIComponent(result.barcode || result.code || "")}`;
    localBox.querySelector("[data-local-edit]").href = `/admin/products/${result.productId}/edit`;
    setMessage("Este producto ya está registrado.");
  }

  async function showExternalProduct(result) {
    hideLookupResults();
    await ensureProductCode();
    setField('[data-code-target="barcode"]', result.barcode);
    setField("[data-product-name]", result.name);
    setField("[data-product-brand]", result.brand);
    setField("[data-product-presentation]", result.presentation);
    if (imageFileInput) imageFileInput.value = "";
    setRemoveImage(false);
    setField("[data-product-image-url]", result.imageUrl);
    updateImagePreview(result.imageUrl || "");
    if (imageFileInfo) imageFileInfo.textContent = result.imageUrl ? "Imagen externa detectada. Puedes reemplazarla subiendo una foto." : "Máximo 5 MB. Formatos: JPEG, PNG o WebP.";
    showCategorySuggestion(result);

    if (externalBox) {
      externalBox.hidden = false;
      externalBox.querySelector("[data-external-name]").textContent = result.name || "Producto encontrado";
      externalBox.querySelector("[data-external-brand]").textContent = result.brand || "";
      externalBox.querySelector("[data-external-presentation]").textContent = result.presentation || "";
      externalBox.querySelector("[data-external-separator]").hidden = !result.brand || !result.presentation;
      const image = externalBox.querySelector("[data-external-image]");
      if (result.imageUrl && isPersistableImageUrl(result.imageUrl)) {
        image.src = result.imageUrl;
        image.hidden = false;
      } else {
        image.hidden = true;
        image.removeAttribute("src");
      }
    }
    setMessage("Producto encontrado. Completa los datos de tu tienda.");
    document.querySelector("[data-cost-input]")?.focus();
  }

  async function showManualProduct(barcode, externalError = false) {
    hideLookupResults();
    await ensureProductCode();
    setField('[data-code-target="barcode"]', barcode);
    updateImagePreview(imageUrlInput?.value?.trim() || "");
    showCategorySuggestion({});
    setMessage(externalError
      ? "No fue posible consultar la base externa en este momento. Puedes continuar registrando el producto manualmente."
      : "No encontramos información para este código. Puedes registrar el producto manualmente.", externalError ? "error" : "success");
    document.querySelector("[data-product-name]")?.focus();
  }

  window.productBarcodeLookup = lookupBarcode;

  document.addEventListener("barcode:detected", event => {
    const barcode = event.detail?.barcode;
    if (event.detail?.source !== "camera" || !barcode) return;
    if (lookupInput) lookupInput.value = barcode;
    lookupBarcode(barcode);
  });

  async function lookupBarcode(barcode) {
    const clean = barcode.replace(/\s+/g, "").trim();
    if (!clean) return;
    lookupInput.disabled = true;
    hideLookupResults();
    setMessage("Buscando información del producto...");
    try {
      const response = await fetch(`/admin/api/products/barcode/${encodeURIComponent(clean)}/lookup`, {
        headers: { "Accept": "application/json" },
        credentials: "same-origin"
      });
      if (!response.ok) throw new Error("No fue posible consultar el producto.");
      const result = await response.json();
      if (result.status === "LOCAL_FOUND") showLocalProduct(result);
      else if (result.status === "EXTERNAL_FOUND") await showExternalProduct(result);
      else if (result.status === "EXTERNAL_ERROR") await showManualProduct(result.barcode || clean, true);
      else await showManualProduct(result.barcode || clean, false);
    } catch (error) {
      await showManualProduct(clean, true);
    } finally {
      lookupInput.disabled = false;
    }
  }

  function updateMarginPreview() {
    if (!costInput || !priceInput || !profitPreview || !marginPreview) return;
    const cost = Number(costInput.value || 0);
    const price = Number(priceInput.value || 0);
    const profit = price - cost;
    const margin = price > 0 ? (profit * 100) / price : 0;
    profitPreview.textContent = money(profit);
    marginPreview.textContent = `${margin.toFixed(2)}%`;
  }

  lookupInput?.addEventListener("keydown", event => {
    if (event.key === "Enter") {
      event.preventDefault();
      lookupBarcode(lookupInput.value);
    }
  });

  localBox?.querySelector("[data-local-cancel]")?.addEventListener("click", () => {
    hideLookupResults();
    lookupMessage.hidden = true;
    lookupInput.value = "";
    lookupInput.focus();
  });

  imageFileInput?.addEventListener("change", event => showSelectedImage(event.target.files?.[0]));
  imageClearButton?.addEventListener("click", clearSelectedImage);
  imageUrlInput?.addEventListener("input", event => {
    if (imageFileInput) imageFileInput.value = "";
    setRemoveImage(false);
    updateImagePreview(event.target.value.trim());
    if (imageFileInfo) imageFileInfo.textContent = event.target.value.trim() ? "Usando URL de imagen." : "Máximo 5 MB. Formatos: JPEG, PNG o WebP.";
  });

  costInput?.addEventListener("input", updateMarginPreview);
  priceInput?.addEventListener("input", updateMarginPreview);

  form?.addEventListener("submit", event => {
    const cost = Number(costInput?.value || 0);
    const price = Number(priceInput?.value || 0);
    if (price > 0 && price < cost && !window.confirm("El precio de venta es menor que el costo. ¿Deseas continuar?")) {
      event.preventDefault();
    }
  });

  updateMarginPreview();
  updateImagePreview(imageUrlInput?.value?.trim() || "");
  if (lookupCard?.dataset.initialBarcode) {
    lookupInput.value = lookupCard.dataset.initialBarcode;
  }
  if (lookupCard?.dataset.autoLookup === "true" && lookupInput?.value) {
    lookupBarcode(lookupInput.value);
  } else {
    setTimeout(() => lookupInput?.focus(), 40);
  }
  window.addEventListener("pagehide", revokePreviewObjectUrl);
})();
