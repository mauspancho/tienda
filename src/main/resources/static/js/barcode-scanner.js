(() => {
  const input = document.querySelector("[data-barcode-input]");
  const cartBody = document.querySelector("[data-cart-body]");
  const totalEl = document.querySelector("[data-cart-total]");
  const discountInput = document.querySelector("[data-discount]");
  const receivedInput = document.querySelector("[data-received]");
  const message = document.querySelector("[data-pos-message]");
  const results = document.querySelector("[data-search-results]");
  const checkoutButton = document.querySelector("[data-checkout]");
  const cart = new Map();
  let busy = false;

  if (!input || !cartBody) return;

  const money = value => new Intl.NumberFormat("es-MX", { style: "currency", currency: "MXN" }).format(value || 0);
  const say = (text, kind = "info") => {
    message.textContent = text;
    message.className = kind === "error" ? "alert alert-error" : "alert alert-success";
  };
  const focusScanner = () => setTimeout(() => input.focus(), 40);
  document.addEventListener("click", focusScanner);
  focusScanner();

  function render() {
    cartBody.innerHTML = "";
    let total = 0;
    for (const item of cart.values()) {
      const line = Number(item.price) * Number(item.quantity);
      total += line;
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td><strong>${item.name}</strong><br><span class="muted">${item.code || ""}</span></td>
        <td><input aria-label="Cantidad" type="number" min="0.001" step="0.001" value="${item.quantity}" data-qty="${item.id}"></td>
        <td>${money(item.price)}</td>
        <td>${money(line)}</td>
        <td><button class="btn btn-danger" data-remove="${item.id}" type="button">Quitar</button></td>`;
      cartBody.appendChild(tr);
    }
    const discount = Number(discountInput?.value || 0);
    totalEl.textContent = money(Math.max(total - discount, 0));
  }

  function addProduct(product) {
    const existing = cart.get(product.id);
    if (existing) existing.quantity = Number(existing.quantity) + 1;
    else cart.set(product.id, { ...product, quantity: 1 });
    render();
    say("Producto agregado.");
    input.value = "";
    results.innerHTML = "";
    focusScanner();
  }

  async function fetchBarcode(code) {
    if (!code || busy) return;
    busy = true;
    try {
      const response = await fetch(`/api/products/barcode/${encodeURIComponent(code)}`);
      if (!response.ok) {
        say("Código de barras no encontrado.", "error");
        return;
      }
      addProduct(await response.json());
    } finally {
      busy = false;
      input.value = "";
      focusScanner();
    }
  }

  async function search(q) {
    if (q.length < 2) {
      results.innerHTML = "";
      return;
    }
    const response = await fetch(`/api/products/search?q=${encodeURIComponent(q)}`);
    const products = await response.json();
    results.innerHTML = "";
    products.forEach(product => {
      const node = document.createElement("button");
      node.type = "button";
      node.className = "product-hit";
      node.innerHTML = `<strong>${product.name}</strong><br><span class="muted">${money(product.price)} · Stock ${product.stock}</span>`;
      node.addEventListener("click", () => addProduct(product));
      results.appendChild(node);
    });
  }

  input.addEventListener("keydown", event => {
    if (event.key === "Enter") {
      event.preventDefault();
      fetchBarcode(input.value.trim());
    }
  });
  input.addEventListener("input", event => search(event.target.value.trim()));
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
    if (cart.size === 0) {
      say("Agrega al menos un producto.", "error");
      return;
    }
    const csrf = document.querySelector("meta[name='_csrf']")?.content;
    const csrfHeader = document.querySelector("meta[name='_csrf_header']")?.content;
    const body = {
      discount: Number(discountInput?.value || 0),
      receivedAmount: Number(receivedInput?.value || 0),
      paymentMethod: document.querySelector("[name='paymentMethod']:checked")?.value || "CASH",
      items: [...cart.values()].map(item => ({ productId: item.id, quantity: item.quantity }))
    };
    const headers = { "Content-Type": "application/json" };
    if (csrf && csrfHeader) headers[csrfHeader] = csrf;
    const response = await fetch("/pos/checkout", { method: "POST", headers, body: JSON.stringify(body) });
    if (!response.ok) {
      say(await response.text(), "error");
      return;
    }
    const result = await response.json();
    cart.clear();
    render();
    say(`Venta completada. Folio ${result.folio}. Cambio ${money(result.change)}.`);
    window.open(`/tickets/${result.folio}`, "_blank");
  });
})();
