document.addEventListener("submit", (event) => {
  const form = event.target;
  if (form.dataset.confirm && !window.confirm(form.dataset.confirm)) {
    event.preventDefault();
    return;
  }
  if (form.hasAttribute("data-single-submit")) {
    form.querySelectorAll('button[type="submit"], button:not([type])').forEach((button) => { button.disabled = true; });
  }
});
