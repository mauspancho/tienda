(() => {
  const form = document.querySelector('[data-purchase-form]');
  const searchInput = document.querySelector('[data-purchase-product-search]');
  const productInput = document.querySelector('[data-purchase-product]');
  const menu = document.querySelector('[data-purchase-product-menu]');
  const costInput = document.querySelector('[data-purchase-cost]');
  const quantityInput = document.querySelector('[data-purchase-quantity]');
  const totalEl = document.querySelector('[data-purchase-total]');

  if (!form || !searchInput || !productInput || !menu) return;

  const normalize = value => String(value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase('es-MX')
    .trim();
  const money = value => new Intl.NumberFormat('es-MX', {
    style: 'currency',
    currency: 'MXN',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(value || 0);
  const products = Array.from(document.querySelectorAll('[data-purchase-product-option]')).map(option => {
    const product = {
      id: option.dataset.id,
      name: option.dataset.name || '',
      brand: option.dataset.brand || '',
      code: option.dataset.code || '',
      barcode: option.dataset.barcode || '',
      presentation: option.dataset.presentation || '',
      cost: Number(option.dataset.cost || 0)
    };
    product.search = normalize([product.name, product.brand, product.code, product.barcode, product.presentation].join(' '));
    return product;
  });

  let matches = [];
  let activeIndex = -1;

  function updateTotal() {
    const quantity = Number(quantityInput?.value || 0);
    const cost = Number(costInput?.value || 0);
    if (totalEl) totalEl.textContent = money(quantity * cost);
  }

  function closeMenu() {
    menu.hidden = true;
    searchInput.setAttribute('aria-expanded', 'false');
    activeIndex = -1;
  }

  function setActive(index) {
    const options = Array.from(menu.querySelectorAll('[role="option"]'));
    if (!options.length) return;
    activeIndex = (index + options.length) % options.length;
    options.forEach((option, optionIndex) => {
      option.setAttribute('aria-selected', String(optionIndex === activeIndex));
    });
    options[activeIndex].scrollIntoView({ block: 'nearest' });
  }

  function selectProduct(product) {
    searchInput.value = product.name;
    searchInput.setCustomValidity('');
    productInput.value = product.id;
    if (costInput) costInput.value = product.cost.toFixed(2);
    closeMenu();
    updateTotal();
  }

  function renderMatches() {
    const query = normalize(searchInput.value);
    menu.replaceChildren();
    activeIndex = -1;

    if (!query) {
      closeMenu();
      return;
    }

    matches = products
      .filter(product => product.search.includes(query))
      .sort((left, right) => {
        const leftStarts = normalize(left.name).startsWith(query) ? 0 : 1;
        const rightStarts = normalize(right.name).startsWith(query) ? 0 : 1;
        return leftStarts - rightStarts || left.name.localeCompare(right.name, 'es-MX');
      })
      .slice(0, 20);

    if (!matches.length) {
      const empty = document.createElement('div');
      empty.className = 'purchase-product-empty';
      empty.textContent = 'No hay productos coincidentes.';
      menu.append(empty);
    } else {
      matches.forEach((product, index) => {
        const option = document.createElement('button');
        option.type = 'button';
        option.className = 'product-autocomplete-option';
        option.setAttribute('role', 'option');
        option.setAttribute('aria-selected', 'false');
        option.dataset.index = String(index);

        const name = document.createElement('span');
        name.className = 'purchase-product-option-name';
        name.textContent = product.name;

        const details = [product.brand, product.presentation, product.code, product.barcode].filter(Boolean);
        const meta = document.createElement('span');
        meta.className = 'purchase-product-option-meta';
        meta.textContent = details.join(' · ');

        option.append(name, meta);
        option.addEventListener('mousedown', event => event.preventDefault());
        option.addEventListener('click', () => selectProduct(product));
        menu.append(option);
      });
    }

    menu.hidden = false;
    searchInput.setAttribute('aria-expanded', 'true');
    if (matches.length) setActive(0);
  }

  searchInput.addEventListener('input', () => {
    productInput.value = '';
    searchInput.setCustomValidity('');
    renderMatches();
  });
  searchInput.addEventListener('focus', renderMatches);
  searchInput.addEventListener('keydown', event => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      if (menu.hidden) renderMatches();
      setActive(activeIndex + 1);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActive(activeIndex - 1);
    } else if (event.key === 'Enter' && activeIndex >= 0) {
      event.preventDefault();
      selectProduct(matches[activeIndex]);
    } else if (event.key === 'Escape') {
      closeMenu();
    }
  });
  document.addEventListener('click', event => {
    if (!event.target.closest('[data-purchase-product-autocomplete]')) closeMenu();
  });
  form.addEventListener('submit', event => {
    if (productInput.value) return;
    event.preventDefault();
    searchInput.setCustomValidity('Selecciona un producto de la lista de coincidencias.');
    searchInput.reportValidity();
    searchInput.focus();
  });
  costInput?.addEventListener('input', updateTotal);
  quantityInput?.addEventListener('input', updateTotal);
  updateTotal();
})();
