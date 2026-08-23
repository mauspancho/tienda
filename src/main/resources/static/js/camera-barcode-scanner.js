(() => {
  const COOLDOWN_MS = 1500;
  const CAMERA_BUTTON_SELECTOR = "[data-camera-scan]";
  const ZXING_PATH = "/vendor/zxing/zxing-browser.min.js";

  const errorMessages = {
    NotAllowedError: "No se concedio permiso para usar la camara.",
    PermissionDeniedError: "No se concedio permiso para usar la camara.",
    NotFoundError: "No se encontro una camara disponible.",
    DevicesNotFoundError: "No se encontro una camara disponible.",
    NotReadableError: "La camara esta ocupada por otra aplicacion.",
    TrackStartError: "La camara esta ocupada por otra aplicacion.",
    OverconstrainedError: "No fue posible utilizar la camara solicitada.",
    ConstraintNotSatisfiedError: "No fue posible utilizar la camara solicitada."
  };

  function normalizeBarcode(value) {
    return String(value || "").replace(/\s+/g, "").trim();
  }

  function cameraErrorMessage(error) {
    if (!window.isSecureContext) {
      return "Para utilizar la camara debes acceder al sistema mediante HTTPS.";
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      return "Este navegador no permite utilizar la camara. Puedes escribir el codigo o utilizar un lector externo.";
    }
    return errorMessages[error?.name] || error?.message || "No fue posible iniciar la camara.";
  }

  function isExpectedDecodeMiss(error) {
    const name = error?.name || error?.constructor?.name || "";
    return ["NotFoundException", "ChecksumException", "FormatException"].includes(name);
  }

  function createModal() {
    let modal = document.querySelector("[data-camera-modal]");
    if (modal) return modal;

    modal = document.createElement("div");
    modal.className = "camera-modal-backdrop";
    modal.hidden = true;
    modal.setAttribute("data-camera-modal", "");
    modal.innerHTML = `
      <div class="camera-modal" role="dialog" aria-modal="true" aria-labelledby="camera-modal-title">
        <div class="camera-modal-header">
          <h2 id="camera-modal-title">Escanear codigo</h2>
          <button class="btn btn-ghost btn-sm" type="button" data-camera-close aria-label="Cerrar camara">Cerrar</button>
        </div>
        <div class="camera-preview" data-camera-preview>
          <video data-camera-video autoplay playsinline muted></video>
          <div class="camera-guide" aria-hidden="true"><span></span></div>
        </div>
        <p class="camera-status" data-camera-status>Coloca el codigo dentro del recuadro.</p>
        <div class="camera-unregistered" data-camera-unregistered hidden></div>
        <p class="camera-privacy">La camara se utiliza unicamente para leer codigos de barras. El video no se envia ni se almacena.</p>
        <div class="camera-actions">
          <button class="btn btn-muted" type="button" data-camera-torch hidden>Encender linterna</button>
          <button class="btn btn-primary" type="button" data-camera-finish>Terminar escaneo</button>
        </div>
      </div>`;
    document.body.appendChild(modal);
    return modal;
  }

  class CameraBarcodeScanner {
    constructor({ videoElement, onDetected, onError, onStatus, onTorchAvailable, cooldownMs = COOLDOWN_MS } = {}) {
      this.videoElement = videoElement;
      this.onDetected = onDetected || (() => {});
      this.onError = onError || (() => {});
      this.onStatus = onStatus || (() => {});
      this.onTorchAvailable = onTorchAvailable || (() => {});
      this.cooldownMs = cooldownMs;
      this.reader = null;
      this.controls = null;
      this.lastBarcode = "";
      this.lastDetectedAt = 0;
      this.paused = false;
      this.running = false;
      this.torchOn = false;
      this.visibilityHandler = () => {
        if (document.visibilityState === "hidden" && this.running) {
          this.pause();
          this.stop();
          this.onStatus("La camara fue pausada.", "error");
        }
      };
      this.pageHideHandler = () => this.stop();
    }

    async start() {
      if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
        const message = cameraErrorMessage();
        this.onError(message);
        throw new Error(message);
      }
      if (!window.ZXingBrowser?.BrowserMultiFormatReader) {
        const message = "No se cargo la libreria local para leer codigos de barras.";
        this.onError(message);
        throw new Error(message);
      }
      if (!this.videoElement) {
        throw new Error("El elemento de video es requerido para iniciar la camara.");
      }

      this.stop();
      this.paused = false;
      this.running = true;
      this.lastBarcode = "";
      this.lastDetectedAt = 0;
      this.reader = new window.ZXingBrowser.BrowserMultiFormatReader(undefined, {
        delayBetweenScanAttempts: 180,
        delayBetweenScanSuccess: 220
      });
      document.addEventListener("visibilitychange", this.visibilityHandler);
      window.addEventListener("pagehide", this.pageHideHandler);
      try {
        this.controls = await this.reader.decodeFromConstraints({
          audio: false,
          video: {
            facingMode: { ideal: "environment" },
            width: { ideal: 1280 },
            height: { ideal: 720 }
          }
        }, this.videoElement, (result, error) => {
          if (result) this.handleResult(result);
          else if (error && !isExpectedDecodeMiss(error)) this.onStatus("Buscando codigo...", "info");
        });
        this.onTorchAvailable(this.hasTorchSupport());
        this.onStatus("Buscando codigo...", "info");
      } catch (error) {
        const message = cameraErrorMessage(error);
        this.onError(message);
        this.stop();
        throw error;
      }
    }

    handleResult(result) {
      if (this.paused) return;
      const barcode = normalizeBarcode(typeof result.getText === "function" ? result.getText() : result.text || result);
      if (!barcode) return;
      const now = Date.now();
      if (barcode === this.lastBarcode && now - this.lastDetectedAt < this.cooldownMs) return;
      this.lastBarcode = barcode;
      this.lastDetectedAt = now;
      this.onDetected(barcode, result);
    }

    pause() {
      this.paused = true;
    }

    resume() {
      this.paused = false;
      this.onStatus("Buscando codigo...", "info");
    }

    hasTorchSupport() {
      if (typeof this.controls?.switchTorch === "function") return true;
      const track = this.videoElement?.srcObject?.getVideoTracks?.()[0];
      return Boolean(track?.getCapabilities?.().torch);
    }

    async toggleTorch() {
      const next = !this.torchOn;
      try {
        if (typeof this.controls?.switchTorch === "function") {
          await this.controls.switchTorch(next);
        } else {
          const track = this.videoElement?.srcObject?.getVideoTracks?.()[0];
          if (!track?.getCapabilities?.().torch) return false;
          await track.applyConstraints({ advanced: [{ torch: next }] });
        }
        this.torchOn = next;
        return true;
      } catch (error) {
        this.onStatus("No fue posible cambiar la linterna.", "error");
        return false;
      }
    }

    stop() {
      this.running = false;
      this.paused = false;
      document.removeEventListener("visibilitychange", this.visibilityHandler);
      window.removeEventListener("pagehide", this.pageHideHandler);
      try {
        this.controls?.stop?.();
      } catch (error) {
        // ZXing controls can throw if the stream already ended.
      }
      const stream = this.videoElement?.srcObject;
      stream?.getTracks?.().forEach(track => track.stop());
      if (this.videoElement) {
        this.videoElement.pause?.();
        this.videoElement.removeAttribute("src");
        this.videoElement.srcObject = null;
      }
      this.controls = null;
      this.reader = null;
      this.torchOn = false;
      this.onTorchAvailable(false);
    }
  }

  class CameraBarcodeModal {
    constructor() {
      this.modal = createModal();
      this.video = this.modal.querySelector("[data-camera-video]");
      this.preview = this.modal.querySelector("[data-camera-preview]");
      this.status = this.modal.querySelector("[data-camera-status]");
      this.unregistered = this.modal.querySelector("[data-camera-unregistered]");
      this.closeButton = this.modal.querySelector("[data-camera-close]");
      this.finishButton = this.modal.querySelector("[data-camera-finish]");
      this.torchButton = this.modal.querySelector("[data-camera-torch]");
      this.scanner = null;
      this.mode = "single";
      this.lastDetectedBarcode = "";
      this.lookupResultHandler = event => this.handleLookupResult(event.detail || {});
      this.closeButton.addEventListener("click", () => this.close());
      this.finishButton.addEventListener("click", () => this.close());
      this.torchButton.addEventListener("click", () => this.toggleTorch());
      this.modal.addEventListener("click", event => {
        if (event.target === this.modal) this.close();
      });
    }

    async open({ mode = "single", label = "Escanear codigo" } = {}) {
      this.mode = mode;
      this.lastDetectedBarcode = "";
      this.modal.hidden = false;
      this.modal.querySelector("#camera-modal-title").textContent = label;
      this.finishButton.textContent = mode === "continuous" ? "Terminar escaneo" : "Cerrar camara";
      this.setStatus("Solicitando permiso de camara...", "info");
      this.clearUnregistered();
      document.addEventListener("barcode:lookup-result", this.lookupResultHandler);
      this.scanner = new CameraBarcodeScanner({
        videoElement: this.video,
        onDetected: barcode => this.detected(barcode),
        onError: message => this.setStatus(message, "error"),
        onStatus: (text, kind) => this.setStatus(text, kind),
        onTorchAvailable: available => this.setTorchAvailable(available)
      });
      try {
        await this.scanner.start();
      } catch (error) {
        this.setStatus(cameraErrorMessage(error), "error");
      }
    }

    detected(barcode) {
      this.lastDetectedBarcode = barcode;
      this.clearUnregistered();
      this.flashSuccess();
      this.setStatus(`Codigo detectado: ${barcode}`, "success");
      navigator.vibrate?.(80);
      document.dispatchEvent(new CustomEvent("barcode:detected", {
        detail: { barcode, source: "camera", mode: this.mode }
      }));
      if (this.mode !== "continuous") {
        setTimeout(() => this.close(), 420);
      }
    }

    handleLookupResult(detail) {
      if (this.mode !== "continuous") return;
      if (detail.barcode && detail.barcode !== this.lastDetectedBarcode) return;
      if (detail.status === "found") {
        this.setStatus(detail.message || "Producto agregado.", "success");
        return;
      }
      if (detail.status === "error") {
        this.setStatus(detail.message || "No fue posible consultar el producto.", "error");
        return;
      }
      if (detail.status === "missing") {
        this.scanner?.pause();
        this.setStatus("Producto no registrado.", "error");
        this.unregistered.hidden = false;
        this.unregistered.innerHTML = `
          <strong>Producto no registrado</strong>
          <span>Codigo: ${detail.barcode || this.lastDetectedBarcode}</span>
          <div class="camera-unregistered-actions">
            <a class="btn btn-muted" href="${detail.registerUrl || `/products/new?barcode=${encodeURIComponent(detail.barcode || this.lastDetectedBarcode)}&lookup=true`}">Buscar informacion y registrar</a>
            <button class="btn btn-primary" type="button" data-camera-continue>Continuar escaneando</button>
          </div>`;
        this.unregistered.querySelector("[data-camera-continue]").addEventListener("click", () => {
          this.clearUnregistered();
          this.scanner?.resume();
        }, { once: true });
      }
    }

    setStatus(text, kind = "info") {
      this.status.textContent = text;
      this.status.dataset.kind = kind;
    }

    setTorchAvailable(available) {
      this.torchButton.hidden = !available;
      if (!available) this.torchButton.textContent = "Encender linterna";
    }

    async toggleTorch() {
      const changed = await this.scanner?.toggleTorch();
      if (changed) this.torchButton.textContent = this.scanner.torchOn ? "Apagar linterna" : "Encender linterna";
    }

    flashSuccess() {
      this.preview.classList.add("camera-preview-detected");
      setTimeout(() => this.preview.classList.remove("camera-preview-detected"), 450);
    }

    clearUnregistered() {
      this.unregistered.hidden = true;
      this.unregistered.innerHTML = "";
    }

    close() {
      document.removeEventListener("barcode:lookup-result", this.lookupResultHandler);
      this.scanner?.stop();
      this.scanner = null;
      this.clearUnregistered();
      this.modal.hidden = true;
    }
  }

  function ensureZxingLoaded() {
    if (window.ZXingBrowser?.BrowserMultiFormatReader) return Promise.resolve();
    return new Promise((resolve, reject) => {
      const existing = document.querySelector(`script[src="${ZXING_PATH}"]`);
      if (existing) {
        existing.addEventListener("load", resolve, { once: true });
        existing.addEventListener("error", reject, { once: true });
        return;
      }
      const script = document.createElement("script");
      script.src = ZXING_PATH;
      script.onload = resolve;
      script.onerror = () => reject(new Error("No se pudo cargar ZXing."));
      document.head.appendChild(script);
    });
  }

  function disableUnsupportedButtons() {
    document.querySelectorAll(CAMERA_BUTTON_SELECTOR).forEach(button => {
      if (!navigator.mediaDevices?.getUserMedia) {
        button.disabled = true;
        button.title = "Este navegador no permite utilizar la camara.";
      }
    });
  }

  function bindCameraButtons() {
    disableUnsupportedButtons();
    document.querySelectorAll(CAMERA_BUTTON_SELECTOR).forEach(button => {
      button.addEventListener("click", async () => {
        if (button.disabled) return;
        const modal = window.tiendaCameraModal || (window.tiendaCameraModal = new CameraBarcodeModal());
        try {
          await ensureZxingLoaded();
          await modal.open({
            mode: button.dataset.cameraMode || "single",
            label: button.dataset.cameraTitle || "Escanear codigo"
          });
        } catch (error) {
          modal.modal.hidden = false;
          modal.setStatus(cameraErrorMessage(error), "error");
        }
      });
    });
  }

  window.CameraBarcodeScanner = CameraBarcodeScanner;
  window.CameraBarcodeModal = CameraBarcodeModal;
  window.tiendaCameraUtils = { normalizeBarcode, cameraErrorMessage };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", bindCameraButtons);
  } else {
    bindCameraButtons();
  }
})();