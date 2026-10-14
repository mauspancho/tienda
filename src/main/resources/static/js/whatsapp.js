document.addEventListener('DOMContentLoaded', () => {
  const feedback = document.querySelector('#copy-feedback');
  const tell = text => { if (feedback) feedback.textContent = text; };
  const copy = async text => { await navigator.clipboard.writeText(text); tell('Copiado al portapapeles.'); };
  document.querySelectorAll('[data-confirm]').forEach(form => form.addEventListener('submit', event => {
    if (!window.confirm(form.dataset.confirm)) event.preventDefault();
  }));
  document.querySelectorAll('.copy-group').forEach(button => button.addEventListener('click', () => copy(button.dataset.id)));
  document.querySelectorAll('[data-copy-target]').forEach(button => button.addEventListener('click', () => copy(document.getElementById(button.dataset.copyTarget).textContent)));
  const checks = () => [...document.querySelectorAll('.group-check:checked')].map(input => input.value);
  document.querySelector('#groups-all')?.addEventListener('change', event => document.querySelectorAll('.group-check').forEach(input => input.checked = event.target.checked));
  document.querySelector('#copy-selected')?.addEventListener('click', () => copy(checks().join('\n')));
  document.querySelector('#copy-json')?.addEventListener('click', () => copy(JSON.stringify(checks().map(phone => ({phone, isGroup: true})), null, 2)));
  document.querySelector('#copy-n8n')?.addEventListener('click', () => {
    const lines = checks().map(phone => `  { phone: '${phone}', isGroup: true }`).join(',\n');
    copy(`const destinatarios = [\n${lines}\n];`);
  });
  document.querySelector('#group-search')?.addEventListener('input', event => {
    const query = event.target.value.trim().toLowerCase();
    document.querySelectorAll('#whatsapp-groups tbody tr[data-search]').forEach(row => row.hidden = !row.dataset.search.includes(query));
  });
  const page = document.querySelector('.whatsapp-page[data-status-url]');
  const qrImage = document.querySelector('#whatsapp-qr-image[data-src]');
  const qrStatus = document.querySelector('#whatsapp-qr-status');
  let qrObjectUrl;
  const loadQr = async () => {
    if (!qrImage?.isConnected) return;
    try {
      const response = await fetch(`${qrImage.dataset.src}?t=${Date.now()}`, {headers: {'Accept': 'image/png'}, cache: 'no-store'});
      if (!response.ok || !response.headers.get('content-type')?.startsWith('image/')) throw new Error('QR unavailable');
      const nextUrl = URL.createObjectURL(await response.blob());
      if (qrObjectUrl) URL.revokeObjectURL(qrObjectUrl);
      qrObjectUrl = nextUrl;
      qrImage.src = nextUrl;
      qrImage.hidden = false;
      if (qrStatus) qrStatus.textContent = 'El código se actualizará automáticamente hasta completar la vinculación.';
    } catch (_) {
      qrImage.hidden = true;
      if (qrStatus) qrStatus.textContent = 'WPPConnect está preparando el código QR. Reintentando…';
    }
  };
  if (qrImage) {
    loadQr();
    setInterval(loadQr, 5000);
  }
  if (page) setInterval(async () => {
    try {
      const response = await fetch(page.dataset.statusUrl, {headers: {'Accept': 'application/json'}});
      if (!response.ok) return;
      const data = await response.json();
      document.querySelector('#whatsapp-status').textContent = data.status;
      document.querySelector('#whatsapp-phone').textContent = data.phone || 'Sin vincular';
      if (data.status === 'CONNECTED') {
        document.querySelector('.whatsapp-qr')?.remove();
        if (qrObjectUrl) URL.revokeObjectURL(qrObjectUrl);
      }
    } catch (_) { }
  }, 5000);
});
