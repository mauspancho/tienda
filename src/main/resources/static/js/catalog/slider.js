(() => {
  const root = document.querySelector("[data-catalog-slider]");
  if (!root) return;
  const slides = [...root.querySelectorAll(".catalog-slide")];
  const prev = root.querySelector("[data-slider-prev]");
  const next = root.querySelector("[data-slider-next]");
  const dots = root.querySelector("[data-slider-dots]");
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  let index = 0;
  let timer = null;
  let startX = 0;

  function render() {
    slides.forEach((slide, i) => slide.classList.toggle("is-active", i === index));
    [...dots.children].forEach((dot, i) => dot.classList.toggle("is-active", i === index));
  }

  function go(nextIndex) {
    index = (nextIndex + slides.length) % slides.length;
    render();
  }

  function pause() {
    if (timer) clearInterval(timer);
    timer = null;
  }

  function play() {
    if (reducedMotion || slides.length < 2 || timer) return;
    timer = setInterval(() => go(index + 1), 5500);
  }

  slides.forEach((_, i) => {
    const dot = document.createElement("button");
    dot.type = "button";
    dot.setAttribute("aria-label", `Ir a promoción ${i + 1}`);
    dot.addEventListener("click", () => { pause(); go(i); });
    dots.appendChild(dot);
  });

  prev?.addEventListener("click", () => { pause(); go(index - 1); });
  next?.addEventListener("click", () => { pause(); go(index + 1); });
  root.addEventListener("pointerdown", event => { startX = event.clientX; pause(); });
  root.addEventListener("pointerup", event => {
    const delta = event.clientX - startX;
    if (Math.abs(delta) > 48) go(index + (delta < 0 ? 1 : -1));
  });
  root.addEventListener("mouseenter", pause);
  root.addEventListener("mouseleave", play);
  render();
  play();
})();
