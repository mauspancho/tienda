(() => {
  const $ = window.jQuery;
  const filterForm = document.querySelector("[data-product-filter-form]");
  const tableElement = document.querySelector("[data-products-table]");
  const modal = document.querySelector("[data-label-modal]");
  const quantityInput = document.querySelector("[data-label-quantity]");
  const productName = document.querySelector("[data-label-product-name]");
  const productBarcode = document.querySelector("[data-label-product-barcode]");
  const csrfToken = document.querySelector("meta[name='_csrf']")?.content || "";
  const autocompleteState = new WeakMap();
  let filterTimer;
  let labelUrl = "";

  if (!$ || !$.fn.DataTable || !filterForm || !tableElement) return;

  const money = value => new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency: "MXN",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(Number(value || 0));
  const number = value => new Intl.NumberFormat("es-MX", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(Number(value || 0));
  const percent = value => `${number(value)}%`;
  const escapeHtml = value => $("<div>").text(value ?? "").html();
  const safeImageUrl = value => {
    const url = String(value || "").trim();
    return url.startsWith("https://") || url.startsWith("/uploads/products/") ? url : "";
  };

  function productImage(row) {
    const imageUrl = safeImageUrl(row.imageUrl);
    return imageUrl
      ? `<img class="product-thumb" src="${escapeHtml(imageUrl)}" alt="" loading="lazy">`
      : '<span class="product-thumb product-thumb-placeholder">▣</span>';
  }

  function codeCell(row) {
    const printButton = row.barcode
      ? `<button class="btn btn-muted btn-sm" type="button" data-open-label-modal data-label-url="/admin/products/${row.id}/barcode-label" data-label-product="${escapeHtml(row.name)}" data-label-barcode="${escapeHtml(row.barcode)}">Imprimir</button>`
      : "";
    return `<div class="flex items-center gap-2"><span>${escapeHtml(row.code)}</span>${printButton}</div>`;
  }

  function productCell(row) {
    const details = [row.category, row.barcode].filter(Boolean)
      .map(value => `<span>${escapeHtml(value)}</span>`).join("");
    return `<div class="product-list-product">${productImage(row)}<div><strong>${escapeHtml(row.name)}</strong><div class="product-meta">${details}</div></div></div>`;
  }

  function whatsappCell(row) {
    const checked = row.whatsapp ? " checked" : "";
    const label = row.whatsapp ? "Seleccionado" : "No seleccionado";
    return `<label class="inline-flex items-center cursor-pointer whitespace-nowrap">
      <input type="checkbox" class="sr-only peer" data-whatsapp-promotion data-product-id="${row.id}" aria-label="Promoción WhatsApp para ${escapeHtml(row.name)}"${checked}>
      <span class="relative w-9 h-5 bg-base-300 rounded-full peer peer-focus:ring-4 peer-focus:ring-green-300 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-0.5 after:start-[2px] after:bg-white after:border after:border-base-300 after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-green-600" aria-hidden="true"></span>
      <span class="select-none ms-3 text-sm font-medium text-base-content peer-checked:text-green-700">${label}</span>
    </label>`;
  }

  function actionCell(row) {
    const action = row.promoted ? "unpromote" : "promote";
    const label = row.promoted ? "Quitar promoción" : "Promocionar";
    const buttonClass = row.promoted ? "btn btn-muted" : "btn btn-primary";
    const badge = row.promoted ? '<span class="badge badge-yellow mt-2">★ Promocionado</span>' : "";
    return `<div class="join">
      <a class="btn btn-muted join-item" href="/admin/products/${row.id}/edit">Editar</a>
      <form class="join-item" action="/admin/products/${row.id}/${action}" method="post">
        <input type="hidden" name="_csrf" value="${escapeHtml(csrfToken)}">
        <button class="${buttonClass}" type="submit">${label}</button>
      </form>
    </div>${badge}`;
  }

  const table = $(tableElement).DataTable({
    processing: true,
    serverSide: true,
    deferRender: true,
    searching: false,
    autoWidth: false,
    scrollX: true,
    pageLength: 20,
    lengthMenu: [10, 20, 50, 100],
    order: [[1, "asc"]],
    ajax: {
      url: tableElement.dataset.source,
      data(data) {
        const filters = new FormData(filterForm);
        for (const [name, value] of filters.entries()) {
          if (name !== "page" && String(value).trim()) data[name] = String(value).trim();
        }
      }
    },
    columns: [
      { data: null, render: (_data, type, row) => type === "display" ? codeCell(row) : row.code },
      { data: null, render: (_data, type, row) => type === "display" ? productCell(row) : row.name },
      { data: "brand", defaultContent: "" },
      { data: "salePrice", render: (value, type) => type === "display" ? money(value) : value },
      { data: "purchaseCost", render: (value, type) => type === "display" ? money(value) : value },
      { data: "stock", render: (value, type, row) => type === "display" ? `<span class="badge ${row.lowStock ? "badge-yellow" : "badge-green"}">${number(value)}</span>` : value },
      { data: "margin", orderable: false, render: (value, type) => type === "display" ? percent(value) : value },
      { data: null, orderable: false, render: (_data, type, row) => type === "display" ? whatsappCell(row) : row.whatsapp },
      { data: null, orderable: false, render: (_data, type, row) => type === "display" ? actionCell(row) : row.id }
    ],
    language: {
      processing: "Actualizando productos...",
      emptyTable: "No hay productos para mostrar.",
      zeroRecords: "No se encontraron productos con esos filtros.",
      lengthMenu: "Mostrar _MENU_ productos",
      info: "Mostrando _START_ a _END_ de _TOTAL_ productos",
      infoEmpty: "Mostrando 0 productos",
      paginate: { first: "Primera", last: "Última", next: "Siguiente", previous: "Anterior" }
    }
  });

  function updateUrl() {
    const url = new URL(filterForm.action, window.location.origin);
    for (const [name, value] of new FormData(filterForm).entries()) {
      const text = String(value).trim();
      if (name !== "page" && text) url.searchParams.set(name, text);
    }
    window.history.replaceState({}, "", url);
  }

  function reloadTable(resetPage = true) {
    clearTimeout(filterTimer);
    updateUrl();
    table.ajax.reload(null, resetPage);
  }

  function hideAutocomplete(input) {
    const menu = input.closest("[data-product-autocomplete]")?.querySelector("[data-autocomplete-menu]");
    if (!menu) return;
    menu.hidden = true;
    menu.innerHTML = "";
    input.setAttribute("aria-expanded", "false");
    const state = autocompleteState.get(input);
    if (state) state.activeIndex = -1;
  }

  function selectSuggestion(input, value) {
    input.value = value;
    hideAutocomplete(input);
    reloadTable();
  }

  function renderSuggestions(input, values) {
    const menu = input.closest("[data-product-autocomplete]")?.querySelector("[data-autocomplete-menu]");
    if (!menu) return;
    menu.innerHTML = "";
    values.forEach(value => {
      const option = document.createElement("button");
      option.type = "button";
      option.className = "product-autocomplete-option";
      option.setAttribute("role", "option");
      option.dataset.autocompleteValue = value;
      option.textContent = value;
      menu.appendChild(option);
    });
    menu.hidden = values.length === 0;
    input.setAttribute("aria-expanded", String(values.length > 0));
  }

  function loadSuggestions(input) {
    const state = autocompleteState.get(input);
    state.request?.abort();
    state.request = $.getJSON("/admin/api/products/suggestions", {
      field: input.dataset.autocompleteField,
      q: input.value.trim()
    }).done(values => renderSuggestions(input, values))
      .fail((_request, status) => { if (status !== "abort") hideAutocomplete(input); });
  }

  function moveAutocompleteSelection(input, direction) {
    const options = [...(input.closest("[data-product-autocomplete]")?.querySelectorAll("[data-autocomplete-value]") || [])];
    if (!options.length) return;
    const state = autocompleteState.get(input);
    state.activeIndex = (state.activeIndex + direction + options.length) % options.length;
    options.forEach((option, index) => option.setAttribute("aria-selected", String(index === state.activeIndex)));
    options[state.activeIndex].scrollIntoView({ block: "nearest" });
  }

  document.querySelectorAll("[data-product-filter]").forEach(control => {
    control.addEventListener(control.matches("select") ? "change" : "input", event => {
      clearTimeout(filterTimer);
      if (event.type === "change") reloadTable();
      else filterTimer = setTimeout(() => reloadTable(), 350);
    });
  });

  document.querySelectorAll("[data-autocomplete-field]").forEach((input, index) => {
    const menu = input.closest("[data-product-autocomplete]")?.querySelector("[data-autocomplete-menu]");
    if (menu) {
      menu.id = `product-autocomplete-${index}`;
      input.setAttribute("aria-controls", menu.id);
    }
    autocompleteState.set(input, { activeIndex: -1, timer: null, request: null });
    input.addEventListener("input", () => {
      const state = autocompleteState.get(input);
      clearTimeout(state.timer);
      state.timer = setTimeout(() => loadSuggestions(input), 150);
    });
    input.addEventListener("focus", () => loadSuggestions(input));
    input.addEventListener("keydown", event => {
      const state = autocompleteState.get(input);
      if (event.key === "ArrowDown" || event.key === "ArrowUp") {
        event.preventDefault();
        moveAutocompleteSelection(input, event.key === "ArrowDown" ? 1 : -1);
      } else if (event.key === "Enter" && state.activeIndex >= 0) {
        const option = input.closest("[data-product-autocomplete]")?.querySelectorAll("[data-autocomplete-value]")[state.activeIndex];
        if (option) {
          event.preventDefault();
          selectSuggestion(input, option.dataset.autocompleteValue);
        }
      } else if (event.key === "Escape") {
        hideAutocomplete(input);
      }
    });
  });

  filterForm.addEventListener("submit", event => {
    event.preventDefault();
    reloadTable();
  });

  document.addEventListener("click", event => {
    const clearButton = event.target.closest("[data-clear-product-filters]");
    if (clearButton) {
      event.preventDefault();
      ["q", "name", "brand", "categoryId", "minPrice", "maxPrice", "active", "whatsapp"]
        .forEach(name => { filterForm.elements.namedItem(name).value = ""; });
      document.querySelectorAll("[data-autocomplete-field]").forEach(hideAutocomplete);
      table.order([[1, "asc"]]);
      reloadTable();
      return;
    }

    const suggestion = event.target.closest("[data-autocomplete-value]");
    if (suggestion) {
      const input = suggestion.closest("[data-product-autocomplete]")?.querySelector("[data-autocomplete-field]");
      if (input) selectSuggestion(input, suggestion.dataset.autocompleteValue);
      return;
    }

    const labelButton = event.target.closest("[data-open-label-modal]");
    if (labelButton) {
      labelUrl = labelButton.dataset.labelUrl;
      productName.textContent = labelButton.dataset.labelProduct || "Producto";
      productBarcode.textContent = labelButton.dataset.labelBarcode || "";
      quantityInput.value = "1";
      if (typeof modal.showModal === "function") modal.showModal();
      else modal.setAttribute("open", "open");
      setTimeout(() => quantityInput.focus(), 40);
      return;
    }

    document.querySelectorAll("[data-autocomplete-field]").forEach(input => {
      if (!input.closest("[data-product-autocomplete]").contains(event.target)) hideAutocomplete(input);
    });
  });

  $(document).on("change", "[data-whatsapp-promotion]", function () {
    const input = this;
    const previousValue = !input.checked;
    input.disabled = true;
    $.ajax({
      url: `/admin/products/${input.dataset.productId}/whatsapp-promotion`,
      method: "POST",
      data: { selected: input.checked, _csrf: csrfToken }
    }).done(() => table.ajax.reload(null, false))
      .fail(() => {
        input.checked = previousValue;
        input.disabled = false;
        window.alert("No fue posible guardar la selección de Promoción WhatsApp.");
      });
  });

  document.querySelector("[data-label-cancel]")?.addEventListener("click", () => modal.close());
  document.querySelector("[data-label-print]")?.addEventListener("click", () => {
    const quantity = Math.max(1, Math.min(Number(quantityInput.value || 1), 500));
    modal.close();
    window.open(`${labelUrl}?quantity=${encodeURIComponent(quantity)}`, "_blank", "noopener");
  });
  quantityInput?.addEventListener("keydown", event => {
    if (event.key === "Enter") {
      event.preventDefault();
      document.querySelector("[data-label-print]")?.click();
    }
  });
})();
