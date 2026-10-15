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
  const groupRows = [...document.querySelectorAll('#whatsapp-groups tbody tr[data-search]')];
  const groupPageSize = 50;
  let groupPage = 0;
  let groupQuery = '';
  const renderGroupPage = () => {
    const matches = groupRows.filter(row => row.dataset.search.includes(groupQuery));
    const pages = Math.max(1, Math.ceil(matches.length / groupPageSize));
    groupPage = Math.min(groupPage, pages - 1);
    const visible = new Set(matches.slice(groupPage * groupPageSize, (groupPage + 1) * groupPageSize));
    groupRows.forEach(row => row.hidden = !visible.has(row));
    const status = document.querySelector('#groups-page-status');
    if (status) status.textContent = `Página ${groupPage + 1} de ${pages} · ${matches.length} grupos`;
    const previous = document.querySelector('#groups-previous');
    const next = document.querySelector('#groups-next');
    if (previous) previous.disabled = groupPage === 0;
    if (next) next.disabled = groupPage >= pages - 1;
  };
  document.querySelector('#group-search')?.addEventListener('input', event => {
    groupQuery = event.target.value.trim().toLowerCase();
    groupPage = 0;
    renderGroupPage();
  });
  document.querySelector('#groups-previous')?.addEventListener('click', () => {
    if (groupPage > 0) groupPage -= 1;
    renderGroupPage();
  });
  document.querySelector('#groups-next')?.addEventListener('click', () => {
    groupPage += 1;
    renderGroupPage();
  });
  renderGroupPage();
  const announcementChecks = [...document.querySelectorAll('.announcement-group-check')];
  const saveAnnouncements = document.querySelector('#save-announcement-groups');
  const pendingAnnouncements = document.querySelector('#announcement-pending');
  const initialAnnouncements = new Map(announcementChecks.map(input => [input.value, input.checked]));
  const updateAnnouncementPending = () => {
    const changed = announcementChecks.some(input => initialAnnouncements.get(input.value) !== input.checked);
    if (saveAnnouncements) saveAnnouncements.disabled = !changed;
    if (pendingAnnouncements) pendingAnnouncements.textContent = changed ? 'Hay selecciones pendientes de guardar' : 'Sin cambios pendientes';
    document.querySelector('#announcement-groups-form')?.classList.toggle('has-pending-changes', changed);
  };
  announcementChecks.forEach(input => input.addEventListener('change', updateAnnouncementPending));
  const page = document.querySelector('.whatsapp-page[data-status-url]');
  const syncForm = document.querySelector('#group-sync-form');
  const syncButton = document.querySelector('#group-sync-button');
  const syncButtonLabel = document.querySelector('#group-sync-button-label');
  const syncStatus = document.querySelector('#group-sync-status');
  const syncMessage = document.querySelector('#group-sync-message');
  const syncCount = document.querySelector('#group-sync-count');
  const syncLastSuccess = document.querySelector('#group-sync-last-success');
  const syncError = document.querySelector('#group-sync-error');
  let syncRunning = syncStatus?.dataset.running === 'true';
  let syncWasRunning = syncRunning;
  let syncTimer;
  const formatDate = value => value ? new Intl.DateTimeFormat('es-MX', {
    dateStyle: 'short', timeStyle: 'short'
  }).format(new Date(value)) : 'Sin sincronizaciones completadas';
  const renderSync = data => {
    syncRunning = Boolean(data.running);
    if (syncButton) syncButton.disabled = syncRunning;
    if (syncButtonLabel) syncButtonLabel.textContent = syncRunning ? 'Actualizando…' : 'Actualizar grupos';
    if (syncCount) syncCount.textContent = data.groupCount ?? 0;
    if (syncLastSuccess) syncLastSuccess.textContent = data.lastSuccessfulAt
      ? `Última actualización: ${formatDate(data.lastSuccessfulAt)}` : 'Sin sincronizaciones completadas';
    if (syncError) {
      syncError.textContent = data.error || '';
      syncError.hidden = !data.error;
    }
    if (syncMessage) {
      if (syncRunning) syncMessage.textContent = 'Actualizando grupos de WhatsApp…';
      else if (data.status === 'COMPLETED') syncMessage.textContent = `Sincronización completada. ${data.groupCount} grupos actualizados correctamente.`;
      else if (data.status === 'FAILED') syncMessage.textContent = 'No se pudieron actualizar los grupos. Se conservará la información de la última sincronización.';
      else syncMessage.textContent = 'Grupos almacenados disponibles.';
    }
    if (syncWasRunning && data.status === 'COMPLETED') {
      syncWasRunning = false;
      window.setTimeout(() => window.location.reload(), 1200);
    }
    if (syncRunning) syncWasRunning = true;
  };
  const pollSync = async () => {
    if (!page?.dataset.groupSyncStatusUrl) return;
    try {
      const response = await fetch(page.dataset.groupSyncStatusUrl, {headers: {'Accept': 'application/json'}, cache: 'no-store'});
      if (response.ok) renderSync(await response.json());
    } catch (_) { }
    if (syncRunning) syncTimer = window.setTimeout(pollSync, 1500);
  };
  syncForm?.addEventListener('submit', async event => {
    event.preventDefault();
    if (syncRunning || !page?.dataset.groupSyncStartUrl) return;
    syncRunning = true;
    syncWasRunning = true;
    if (syncButton) syncButton.disabled = true;
    if (syncButtonLabel) syncButtonLabel.textContent = 'Actualizando…';
    if (syncMessage) syncMessage.textContent = 'Actualizando grupos de WhatsApp…';
    if (syncError) { syncError.textContent = ''; syncError.hidden = true; }
    try {
      const response = await fetch(page.dataset.groupSyncStartUrl, {
        method: 'POST', body: new FormData(syncForm), headers: {'Accept': 'application/json'}
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || 'No fue posible iniciar la sincronización.');
      renderSync(data);
      window.clearTimeout(syncTimer);
      syncTimer = window.setTimeout(pollSync, 750);
    } catch (error) {
      syncRunning = false;
      syncWasRunning = false;
      if (syncButton) syncButton.disabled = false;
      if (syncButtonLabel) syncButtonLabel.textContent = 'Actualizar grupos';
      if (syncMessage) syncMessage.textContent = 'No se pudo iniciar la actualización de grupos.';
      if (syncError) { syncError.textContent = error.message; syncError.hidden = false; }
    }
  });
  if (syncRunning) syncTimer = window.setTimeout(pollSync, 500);
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
  let statusRequestInFlight = false;
  if (page) setInterval(async () => {
    if (statusRequestInFlight || syncRunning) return;
    statusRequestInFlight = true;
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
    finally { statusRequestInFlight = false; }
  }, 5000);
});
