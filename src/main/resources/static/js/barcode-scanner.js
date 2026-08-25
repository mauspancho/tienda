(() => {
  const input = document.querySelector("[data-barcode-input]");
  const cartBody = document.querySelector("[data-cart-body]");
  const totalEl = document.querySelector("[data-cart-total]");
  const discountInput = document.querySelector("[data-discount]");
  const message = document.querySelector("[data-pos-message]");
  const results = document.querySelector("[data-search-results]");
  const checkoutButton = document.querySelector("[data-checkout]");
  const posPanel = document.querySelector("[data-cash-open]");
  const cashOpen = posPanel?.dataset.cashOpen === "true";
  const cart = new Map();
  let busy = false;
  let autoScanTimer = null;
  let lastInputAt = 0;
  let lastInputValue = "";
  let fastInputStreak = 0;
  let lastAutoCode = "";

  const AUTO_SCAN_MIN_LENGTH = 4;
  const SCANNER_KEY_INTERVAL_MS = 55;
  const AUTO_SCAN_IDLE_MS = 140;

  if (!input || !cartBody) return;

  const money = value => new Intl.NumberFormat("es-MX", { style: "currency", currency: "MXN", minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value || 0);
  const number = value => new Intl.NumberFormat("es-MX", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value || 0);
  const say = (text, kind = "info") => {
    message.textContent = text;
    message.className = kind === "error" ? "alert alert-error" : "alert alert-success";
  };
  const focusScanner = () => {
    if (!cashOpen || input.disabled) return;
    setTimeout(() => input.focus(), 40);
  };
  document.addEventListener("click", focusScanner);
  focusScanner();

  function cleanErrorText(text) {
    const doc = new DOMParser().parseFromString(text, "text/html");
    return doc.body?.textContent?.replace(/\s+/g, " ").trim() || text;
  }


  function safeImageUrl(url) {
    if (!url || typeof url !== "string") return "";
    const value = url.trim();
    return value.startsWith("https://") || value.startsWith("/uploads/products/") ? value : "";
  }

  function productImageNode(imageUrl) {
    const safeUrl = safeImageUrl(imageUrl);
    if (!safeUrl) {
      const placeholder = document.createElement("span");
      placeholder.className = "product-thumb product-thumb-placeholder";
      placeholder.textContent = "▣";
      return placeholder;
    }
    const img = document.createElement("img");
    img.className = "product-thumb";
    img.alt = "";
    img.src = safeUrl;
    img.addEventListener("error", () => {
      const placeholder = document.createElement("span");
      placeholder.className = "product-thumb product-thumb-placeholder";
      placeholder.textContent = "▣";
      img.replaceWith(placeholder);
    }, { once: true });
    return img;
  }
  function normalizeCartQuantity(value) {
    const quantity = Math.floor(Number(value || 0));
    return Number.isFinite(quantity) && quantity > 0 ? quantity : 1;
  }

  function cartTotal() {
    let total = 0;
    for (const item of cart.values()) {
      const quantity = normalizeCartQuantity(item.quantity);
      item.quantity = quantity;
      total += Number(item.price) * quantity;
    }
    const discount = Number(discountInput?.value || 0);
    return Math.max(total - discount, 0);
  }
  function render() {
    cartBody.innerHTML = "";
    for (const item of cart.values()) {
      const quantity = normalizeCartQuantity(item.quantity);
      item.quantity = quantity;
      const line = Number(item.price) * quantity;
      const tr = document.createElement("tr");
      const productTd = document.createElement("td");
      const productCell = document.createElement("div");
      productCell.className = "pos-product-cell";
      productCell.appendChild(productImageNode(item.imageUrl));
      const productText = document.createElement("div");
      const productName = document.createElement("strong");
      productName.className = "pos-product-name";
      productName.textContent = item.name || "Producto";
      const productCode = document.createElement("span");
      productCode.className = "muted pos-product-code";
      productCode.textContent = item.code || item.barcode || "";
      productText.append(productName, productCode);
      productCell.appendChild(productText);
      productTd.appendChild(productCell);

      const qtyTd = document.createElement("td");
      const qtyInput = document.createElement("input");
      qtyInput.setAttribute("aria-label", "Cantidad");
      qtyInput.type = "number";
      qtyInput.setAttribute("min", "1");
      qtyInput.setAttribute("step", "1");
      qtyInput.inputMode = "numeric";
      qtyInput.value = String(quantity);
      qtyInput.dataset.qty = String(item.id);
      qtyTd.appendChild(qtyInput);

      const priceTd = document.createElement("td");
      priceTd.textContent = money(item.price);
      const lineTd = document.createElement("td");
      lineTd.textContent = money(line);
      const removeTd = document.createElement("td");
      const removeButton = document.createElement("button");
      removeButton.className = "btn btn-danger";
      removeButton.dataset.remove = String(item.id);
      removeButton.type = "button";
      removeButton.textContent = "Quitar";
      removeTd.appendChild(removeButton);
      tr.append(productTd, qtyTd, priceTd, lineTd, removeTd);
      cartBody.appendChild(tr);
    }
    totalEl.textContent = money(cartTotal());
  }

  function registerUrlFor(barcode) {
    return `/products/new?barcode=${encodeURIComponent(barcode)}&lookup=true`;
  }

  function notifyCameraLookup(detail) {
    document.dispatchEvent(new CustomEvent("barcode:lookup-result", { detail }));
  }

  function showUnregisteredProduct(barcode) {
    say("Producto no registrado.", "error");
    results.innerHTML = "";
    const panel = document.createElement("div");
    panel.className = "product-hit";
    panel.innerHTML = `
      <strong>Producto no registrado.</strong><br>
      <span class="muted">Código: ${barcode}</span><br>
      <a class="btn btn-muted" href="${registerUrlFor(barcode)}">Buscar información y registrar</a>
      <button class="btn btn-muted" type="button" data-cancel-register>Cancelar</button>`;
    panel.querySelector("[data-cancel-register]").addEventListener("click", () => {
      results.innerHTML = "";
      say("Listo para vender.");
      focusScanner();
    });
    results.appendChild(panel);
  }

  function addProduct(product) {
    const existing = cart.get(product.id);
    if (existing) existing.quantity = Number(existing.quantity) + 1;
    else cart.set(product.id, { ...product, quantity: 1 });
    render();
    say("Producto agregado.");
    input.value = "";
    results.innerHTML = "";
    lastInputValue = "";
    fastInputStreak = 0;
    lastAutoCode = "";
    focusScanner();
  }

  async function fetchBarcode(code) {
    const barcode = code.trim();
    if (!barcode || busy || !cashOpen) return { status: "ignored", barcode };
    busy = true;
    try {
      const response = await fetch(`/api/products/barcode/${encodeURIComponent(barcode)}`);
      if (!response.ok) {
        showUnregisteredProduct(barcode);
        const result = { status: "missing", barcode, registerUrl: registerUrlFor(barcode) };
        notifyCameraLookup(result);
        return result;
      }
      const product = await response.json();
      addProduct(product);
      const result = { status: "found", barcode, product, productName: product.name, message: `${product.name} agregado.` };
      notifyCameraLookup(result);
      return result;
    } catch (error) {
      const result = { status: "error", barcode, message: "No fue posible consultar el producto." };
      notifyCameraLookup(result);
      say(result.message, "error");
      return result;
    } finally {
      busy = false;
      input.value = "";
      lastInputValue = "";
      fastInputStreak = 0;
      lastAutoCode = "";
      focusScanner();
    }
  }

  async function search(q) {
    if (!cashOpen || q.length < 2) {
      results.innerHTML = "";
      return;
    }
    const response = await fetch(`/api/products/search?q=${encodeURIComponent(q)}`);
    const products = await response.json();
    results.innerHTML = "";
    products.forEach(product => {
      const node = document.createElement("button");
      node.type = "button";
      node.className = "product-hit pos-product-cell";
      node.appendChild(productImageNode(product.imageUrl));
      const text = document.createElement("span");
      const name = document.createElement("strong");
      name.textContent = product.name || "Producto";
      const meta = document.createElement("span");
      meta.className = "muted pos-product-code";
      meta.textContent = `${money(product.price)} · Stock ${number(product.stock)}`;
      text.append(name, document.createElement("br"), meta);
      node.appendChild(text);
      node.addEventListener("click", () => addProduct(product));
      results.appendChild(node);
    });
  }

  function clearAutoScanTimer() {
    if (autoScanTimer) {
      clearTimeout(autoScanTimer);
      autoScanTimer = null;
    }
  }

  function scheduleAutoScanLookup(code) {
    clearAutoScanTimer();
    if (code.length < AUTO_SCAN_MIN_LENGTH || code === lastAutoCode) return;
    lastAutoCode = code;
    autoScanTimer = setTimeout(() => fetchBarcode(code), AUTO_SCAN_IDLE_MS);
  }

  function trackScannerInput(value) {
    const now = performance.now();
    const delta = now - lastInputAt;
    const grewBy = value.length - lastInputValue.length;
    const fastKey = grewBy === 1 && delta > 0 && delta <= SCANNER_KEY_INTERVAL_MS;
    const burstInput = grewBy > 1;

    if (burstInput) fastInputStreak += grewBy;
    else if (fastKey) fastInputStreak += 1;
    else fastInputStreak = 0;

    lastInputAt = now;
    lastInputValue = value;

    if (value.length >= AUTO_SCAN_MIN_LENGTH && (fastInputStreak >= AUTO_SCAN_MIN_LENGTH - 1 || burstInput)) {
      scheduleAutoScanLookup(value);
    } else {
      clearAutoScanTimer();
    }
  }

  window.posBarcodeLookup = fetchBarcode;

  document.addEventListener("barcode:detected", event => {
    const barcode = event.detail?.barcode;
    if (event.detail?.source !== "camera" || !barcode) return;
    fetchBarcode(barcode);
  });
  input.addEventListener("keydown", event => {
    if (event.key === "Enter") {
      event.preventDefault();
      clearAutoScanTimer();
      fetchBarcode(input.value.trim());
    }
  });
  input.addEventListener("input", event => {
    const value = event.target.value.trim();
    trackScannerInput(value);
    search(value);
  });
  cartBody.addEventListener("input", event => {
    const id = Number(event.target.dataset.qty);
    if (!id) return;
    const item = cart.get(id);
    item.quantity = Number(event.target.value);
    render();
  });
  cartBody.addEventListener("click", event => {
    const id = Number(event.target.dataset.remove);
    if (!id) return;
    cart.delete(id);
    render();
  });
  discountInput?.addEventListener("input", render);
  checkoutButton?.addEventListener("click", async () => {
    if (!cashOpen) {
      say("Abre la caja antes de realizar una venta.", "error");
      return;
    }
    if (cart.size === 0) {
      say("Agrega al menos un producto.", "error");
      return;
    }
    const csrf = document.querySelector("meta[name='_csrf']")?.content;
    const csrfHeader = document.querySelector("meta[name='_csrf_header']")?.content;
    const body = {
      discount: Number(discountInput?.value || 0),
      receivedAmount: cartTotal(),
      paymentMethod: document.querySelector("[name='paymentMethod']:checked")?.value || "CASH",
      items: [...cart.values()].map(item => ({ productId: item.id, quantity: normalizeCartQuantity(item.quantity) }))
    };
    const headers = { "Content-Type": "application/json" };
    if (csrf && csrfHeader) headers[csrfHeader] = csrf;
    const response = await fetch("/pos/checkout", { method: "POST", headers, body: JSON.stringify(body) });
    if (!response.ok) {
      say(cleanErrorText(await response.text()), "error");
      return;
    }
    const result = await response.json();
    cart.clear();
    render();
    say(`Venta completada. Folio ${result.folio}. Cambio ${money(result.change)}.`);
    window.open(`/tickets/${result.folio}`, "_blank");
  });
})();
