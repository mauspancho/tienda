(() => {
  const modal = document.querySelector("[data-product-share-modal]");
  if (!modal) return;

  const message = modal.querySelector("[data-share-message]");
  const messageCount = modal.querySelector("[data-share-message-count]");
  const previewMessage = modal.querySelector("[data-share-preview-message]");
  const result = modal.querySelector("[data-share-result]");
  const submit = modal.querySelector("[data-product-share-submit]");
  const submitLabel = modal.querySelector("[data-product-share-submit-label]");
  const spinner = modal.querySelector("[data-product-share-spinner]");
  const numberMode = modal.querySelector("[data-share-whatsapp-number-mode]");
  const phoneField = modal.querySelector("[data-share-whatsapp-phone-field]");
  const phone = modal.querySelector("[data-share-whatsapp-phone]");
  const groups = modal.querySelector("[data-share-whatsapp-groups]");
  const groupWarning = modal.querySelector("[data-share-whatsapp-warning]");
  const individualWarning = modal.querySelector("[data-share-whatsapp-individual-warning]");
  const manualActions = modal.querySelector("[data-share-manual-actions]");
  const downloadButton = modal.querySelector("[data-share-download]");
  const csrfToken = document.querySelector("meta[name='_csrf']")?.content || "";
  const csrfHeader = document.querySelector("meta[name='_csrf_header']")?.content || "X-CSRF-TOKEN";
  let currentProduct = null;
  let currentPlatform = "whatsapp";
  let requestKey = "";
  let imageFilePromise = null;

  const safeImageUrl = value => {
    const url = String(value || "").trim();
    return url.startsWith("https://") || url.startsWith("/uploads/products/") ? url : "";
  };

  const newKey = () => {
    if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID().replaceAll("-", "");
    const values = new Uint32Array(4);
    if (globalThis.crypto?.getRandomValues) globalThis.crypto.getRandomValues(values);
    else values.forEach((_, index) => { values[index] = Math.floor(Math.random() * 0xffffffff); });
    return `manual_${Date.now().toString(36)}_${Array.from(values, value => value.toString(36)).join("")}`;
  };

  const showModal = () => {
    if (typeof modal.showModal === "function") modal.showModal();
    else modal.setAttribute("open", "open");
  };

  const closeModal = () => {
    if (typeof modal.close === "function") modal.close();
    else modal.removeAttribute("open");
  };

  const setText = (selector, value) => {
    const element = modal.querySelector(selector);
    if (element) element.textContent = value || "";
  };

  function setImage(selector, url) {
    const image = modal.querySelector(selector);
    if (!image) return;
    if (url) {
      image.src = url;
      image.hidden = false;
    } else {
      image.removeAttribute("src");
      image.hidden = true;
    }
  }

  function setResult(kind, text) {
    result.className = `alert alert-${kind}`;
    result.textContent = text;
    result.hidden = !text;
  }

  function refreshMessagePreview() {
    const value = message.value;
    messageCount.textContent = String(value.length);
    previewMessage.textContent = value;
  }

  function populateGroups(product) {
    const list = modal.querySelector("[data-share-whatsapp-group-list]");
    list.replaceChildren();
    (product.whatsappGroups || []).forEach(name => {
      const badge = document.createElement("span");
      badge.className = "badge";
      badge.textContent = name;
      list.appendChild(badge);
    });
    setText("[data-share-whatsapp-group-count]", `${product.whatsappGroupCount} ${product.whatsappGroupCount === 1 ? "grupo destinatario" : "grupos destinatarios"}`);
  }

  function refreshWhatsappMode() {
    if (!currentProduct || currentPlatform !== "whatsapp") return;
    const individual = numberMode.checked;
    phoneField.hidden = !individual;
    phone.required = individual;
    groups.hidden = individual || !currentProduct.whatsappReady;
    groupWarning.hidden = individual || currentProduct.whatsappReady;
    individualWarning.hidden = !individual || currentProduct.whatsappIndividualReady;
    submit.disabled = individual ? !currentProduct.whatsappIndividualReady : !currentProduct.whatsappReady;
  }

  function selectPlatform(platform) {
    currentPlatform = ["whatsapp", "facebook", "instagram"].includes(platform) ? platform : "whatsapp";
    modal.querySelectorAll("[data-share-platform]").forEach(button => {
      const active = button.dataset.sharePlatform === currentPlatform;
      button.classList.toggle("btn-primary", active);
      button.classList.toggle("btn-muted", !active);
      button.setAttribute("aria-selected", String(active));
    });
    modal.querySelectorAll("[data-share-panel]").forEach(panel => {
      panel.hidden = panel.dataset.sharePanel !== currentPlatform;
    });
    manualActions.hidden = currentPlatform === "whatsapp";
    downloadButton.disabled = !currentProduct?.imageDownloadUrl;
    submitLabel.textContent = currentPlatform === "whatsapp"
      ? "Enviar por WhatsApp"
      : currentPlatform === "facebook" ? "Preparar para Facebook" : "Compartir en Instagram";
    submit.disabled = false;
    if (currentPlatform === "whatsapp") refreshWhatsappMode();
    setResult("info", "");
  }

  async function loadPreview(productId, platform) {
    setResult("info", "");
    submit.disabled = true;
    spinner.hidden = false;
    submitLabel.textContent = "Cargando...";
    showModal();
    try {
      const response = await fetch(`/admin/products/${encodeURIComponent(productId)}/share-preview`, {
        headers: { Accept: "application/json" }
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || "No fue posible preparar este producto.");
      currentProduct = data;
      requestKey = newKey();
      const imageUrl = safeImageUrl(data.imageUrl);
      const identity = [data.brand, data.presentation].filter(Boolean).join(" · ");
      setText("[data-share-product-name]", data.name);
      setText("[data-share-product-brand]", identity);
      setText("[data-share-product-description]", data.description);
      setText("[data-share-product-price]", `Precio: $${Number(data.salePrice || 0).toFixed(2)}`);
      setText("[data-share-preview-name]", data.name);
      setImage("[data-share-product-image]", imageUrl);
      setImage("[data-share-preview-image]", imageUrl);
      modal.querySelector("[data-share-no-image]").hidden = Boolean(imageUrl);
      message.value = data.suggestedMessage || data.description || data.name || "";
      groupWarning.textContent = data.whatsappWarning || "WhatsApp no está disponible para este producto.";
      individualWarning.textContent = data.whatsappIndividualWarning || "WhatsApp no está disponible para este producto.";
      populateGroups(data);
      numberMode.checked = false;
      phone.value = "";
      imageFilePromise = data.imageDownloadUrl ? fetchImageFile(data) : null;
      imageFilePromise?.catch(() => {});
      refreshMessagePreview();
      selectPlatform(platform);
    } catch (error) {
      currentProduct = null;
      setResult("error", error.message);
      submitLabel.textContent = "Continuar";
      submit.disabled = true;
    } finally {
      spinner.hidden = true;
    }
  }

  async function fetchImageFile(product) {
    const response = await fetch(product.imageDownloadUrl, { headers: { Accept: "image/*" } });
    if (!response.ok) throw new Error("No fue posible preparar la imagen del producto.");
    const blob = await response.blob();
    if (!blob.type.startsWith("image/")) throw new Error("El archivo del producto no es una imagen compatible.");
    const disposition = response.headers.get("Content-Disposition") || "";
    const filename = disposition.match(/filename="?([^";]+)"?/i)?.[1] || `producto-${product.id}.jpg`;
    return new File([blob], filename, { type: blob.type });
  }

  async function copyMessage(showFeedback = true) {
    const value = message.value.trim();
    if (!value) throw new Error("Escribe el mensaje que deseas compartir.");
    if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(value);
    else {
      const textarea = document.createElement("textarea");
      textarea.value = value;
      textarea.style.position = "fixed";
      textarea.style.opacity = "0";
      document.body.appendChild(textarea);
      textarea.select();
      if (!document.execCommand("copy")) throw new Error("No fue posible copiar el mensaje.");
      textarea.remove();
    }
    if (showFeedback) setResult("success", "Mensaje copiado. Ya puedes pegarlo en la publicación.");
  }

  async function downloadImage(showFeedback = true) {
    if (!imageFilePromise) {
      if (showFeedback) setResult("info", "Este producto no tiene una imagen local descargable; puedes compartir el texto.");
      return null;
    }
    const file = await imageFilePromise;
    const url = URL.createObjectURL(file);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = file.name;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    if (showFeedback) setResult("success", "Imagen descargada. Adjunta el archivo al crear tu publicación.");
    return file;
  }

  async function sendWhatsapp() {
    const individual = numberMode.checked;
    const destination = phone.value.trim();
    const target = individual
      ? `exclusivamente al número ${destination}`
      : `a ${currentProduct.whatsappGroupCount} ${currentProduct.whatsappGroupCount === 1 ? "grupo" : "grupos"} de WhatsApp`;
    if (!window.confirm(`¿Deseas enviar ${currentProduct.name} ${target}?`)) return;
    const body = new URLSearchParams({
      message: message.value,
      requestKey,
      sendToNumber: String(individual)
    });
    if (individual) body.set("phone", destination);
    const response = await fetch(`/admin/products/${currentProduct.id}/manual-whatsapp-promotion`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8", [csrfHeader]: csrfToken },
      body
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.message || "No fue posible enviar la promoción.");
    const failures = (data.groups || []).filter(recipient => recipient.status === "FAILED");
    const summary = individual
      ? (data.accepted > 0 ? "Promoción aceptada por WhatsApp." : "WhatsApp no aceptó la promoción.")
      : `Promoción procesada: ${data.accepted} grupos aceptados, ${data.failed} grupos con error.`;
    result.className = failures.length ? "alert alert-warning" : "alert alert-success";
    result.replaceChildren();
    const heading = document.createElement("strong");
    heading.textContent = summary;
    result.appendChild(heading);
    if (data.accepted > 0) {
      const note = document.createElement("p");
      note.textContent = "WPPConnect aceptó la solicitud; esto no confirma todavía la entrega al destinatario.";
      result.appendChild(note);
    }
    if (failures.length) {
      const list = document.createElement("ul");
      failures.forEach(failure => {
        const item = document.createElement("li");
        item.textContent = `${failure.groupName}: ${failure.error || "Error de envío"}`;
        list.appendChild(item);
      });
      result.appendChild(list);
    }
    result.hidden = false;
    if (!data.processing && !failures.length) requestKey = newKey();
  }

  async function prepareFacebook() {
    const facebook = window.open("https://www.facebook.com/", "_blank", "noopener");
    await copyMessage(false);
    if (imageFilePromise) await downloadImage(false);
    setResult("success", imageFilePromise
      ? `Mensaje copiado e imagen descargada. ${facebook ? "Facebook se abrió" : "Abre Facebook"} para que completes y confirmes la publicación.`
      : `Mensaje copiado. ${facebook ? "Facebook se abrió" : "Abre Facebook"} para que completes y confirmes la publicación.`);
  }

  async function prepareInstagram() {
    const text = message.value.trim();
    if (!text) throw new Error("Escribe el mensaje que deseas compartir.");
    let file = null;
    if (imageFilePromise) file = await imageFilePromise;
    const shareData = file ? { title: currentProduct.name, text, files: [file] } : { title: currentProduct.name, text };
    let canShareFiles = false;
    if (file && navigator.canShare) {
      try {
        canShareFiles = navigator.canShare({ files: [file] });
      } catch (_error) {
        canShareFiles = false;
      }
    }
    if (navigator.share && (!file || canShareFiles)) {
      try {
        await navigator.share(shareData);
        setResult("success", "Contenido enviado al menú del sistema. Confirma la publicación desde la aplicación que elijas.");
        return;
      } catch (error) {
        if (error.name === "AbortError") return;
      }
    }
    const instagram = window.open("https://www.instagram.com/", "_blank", "noopener");
    await copyMessage(false);
    if (file) await downloadImage(false);
    setResult("info", instagram
      ? "Tu navegador no pudo transferir todo el contenido. El mensaje se copió y la imagen se descargó; adjúntala y pega el texto en Instagram."
      : "El mensaje se copió y la imagen quedó preparada. Abre Instagram, adjunta la imagen y pega el texto.");
  }

  async function runAction() {
    if (!currentProduct || !message.value.trim()) {
      setResult("error", "Escribe el mensaje que deseas compartir.");
      return;
    }
    submit.disabled = true;
    spinner.hidden = false;
    const originalLabel = submitLabel.textContent;
    submitLabel.textContent = currentPlatform === "whatsapp" ? "Enviando..." : "Preparando...";
    result.hidden = true;
    try {
      if (currentPlatform === "whatsapp") await sendWhatsapp();
      else if (currentPlatform === "facebook") await prepareFacebook();
      else await prepareInstagram();
    } catch (error) {
      setResult("error", error.message || "No fue posible preparar el contenido.");
    } finally {
      spinner.hidden = true;
      submitLabel.textContent = originalLabel;
      if (currentPlatform === "whatsapp") refreshWhatsappMode();
      else submit.disabled = false;
    }
  }

  document.addEventListener("click", event => {
    const menuSummary = event.target.closest(".product-share-menu > summary");
    if (menuSummary) {
      const details = menuSummary.closest("details");
      document.querySelectorAll(".product-share-menu[open]").forEach(openMenu => {
        if (openMenu !== details) openMenu.removeAttribute("open");
      });
      if (window.innerWidth > 640) {
        const rect = menuSummary.getBoundingClientRect();
        const left = Math.max(8, Math.min(rect.right - 192, window.innerWidth - 200));
        const top = Math.min(rect.bottom + 6, window.innerHeight - 160);
        details.style.setProperty("--share-menu-left", `${left}px`);
        details.style.setProperty("--share-menu-top", `${Math.max(8, top)}px`);
      }
      return;
    }
    const trigger = event.target.closest("[data-product-share]");
    if (!trigger) {
      if (!event.target.closest(".product-share-menu")) {
        document.querySelectorAll(".product-share-menu[open]").forEach(menu => menu.removeAttribute("open"));
      }
      return;
    }
    event.preventDefault();
    trigger.closest("details")?.removeAttribute("open");
    loadPreview(trigger.dataset.productShare, trigger.dataset.sharePlatform || "whatsapp");
  });
  modal.querySelectorAll("[data-share-platform]").forEach(button => {
    button.addEventListener("click", () => selectPlatform(button.dataset.sharePlatform));
  });
  modal.querySelector("[data-product-share-close]")?.addEventListener("click", closeModal);
  modal.querySelector("[data-product-share-cancel]")?.addEventListener("click", closeModal);
  modal.querySelector("[data-share-copy]")?.addEventListener("click", () => copyMessage().catch(error => setResult("error", error.message)));
  downloadButton?.addEventListener("click", () => downloadImage().catch(error => setResult("error", error.message)));
  message.addEventListener("input", refreshMessagePreview);
  numberMode.addEventListener("change", refreshWhatsappMode);
  submit.addEventListener("click", runAction);
})();
