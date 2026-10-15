(() => {
  const root = document.querySelector("[data-brand-autocomplete]");
  if (!root) return;

  const input = root.querySelector("[data-product-brand]");
  const menu = root.querySelector("[data-brand-autocomplete-menu]");
  const source = root.dataset.brandSuggestionsUrl;
  let timer;
  let request;
  let activeIndex = -1;

  function close() {
    menu.hidden = true;
    menu.replaceChildren();
    input.setAttribute("aria-expanded", "false");
    activeIndex = -1;
  }

  function select(value) {
    input.value = value;
    input.dispatchEvent(new Event("change", { bubbles: true }));
    close();
  }

  function render(values) {
    menu.replaceChildren();
    values.forEach(value => {
      const option = document.createElement("button");
      option.type = "button";
      option.className = "product-autocomplete-option";
      option.setAttribute("role", "option");
      option.textContent = value;
      option.addEventListener("mousedown", event => {
        event.preventDefault();
        select(value);
      });
      menu.appendChild(option);
    });
    menu.hidden = values.length === 0;
    input.setAttribute("aria-expanded", String(values.length > 0));
    activeIndex = -1;
  }

  async function search() {
    const query = input.value.trim();
    if ([...query].length < 2) {
      close();
      return;
    }
    request?.abort();
    request = new AbortController();
    try {
      const separator = source.includes("?") ? "&" : "?";
      const response = await fetch(`${source}${separator}q=${encodeURIComponent(query)}`, {
        signal: request.signal,
        headers: { Accept: "application/json" }
      });
      if (!response.ok) throw new Error("No fue posible consultar marcas");
      render(await response.json());
    } catch (error) {
      if (error.name !== "AbortError") close();
    }
  }

  input.addEventListener("input", () => {
    clearTimeout(timer);
    timer = setTimeout(search, 250);
  });
  input.addEventListener("keydown", event => {
    const options = [...menu.querySelectorAll("[role='option']")];
    if ((event.key === "ArrowDown" || event.key === "ArrowUp") && options.length) {
      event.preventDefault();
      activeIndex = (activeIndex + (event.key === "ArrowDown" ? 1 : -1) + options.length) % options.length;
      options.forEach((option, index) => option.setAttribute("aria-selected", String(index === activeIndex)));
      options[activeIndex].scrollIntoView({ block: "nearest" });
    } else if (event.key === "Enter" && activeIndex >= 0) {
      event.preventDefault();
      select(options[activeIndex].textContent);
    } else if (event.key === "Escape") {
      close();
    }
  });
  input.addEventListener("blur", () => setTimeout(close, 120));
})();
