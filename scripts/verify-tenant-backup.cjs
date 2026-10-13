const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs");

// Use only the disposable PlatformPreviewServer fixture.
(async () => {
  const base = process.env.PREVIEW_URL;
  assert(/^http:\/\/127\.0\.0\.1:\d+$/.test(base || ""));
  const out = "target/tenant-backup-visual";
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH, headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = [];
    page.on("pageerror", error => errors.push(error.message));
    await page.goto(base + "/admin/login");
    await page.locator('[name="username"]').fill("preview");
    await page.locator('[name="password"]').fill("Preview-only-493!");
    await page.locator('button[type="submit"]').click();
    await page.waitForURL(base + "/platform/tenants");
    for (const width of [1920, 1440, 1024, 768, 390, 320]) {
      await page.setViewportSize({ width, height: 1000 });
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), "Tenant list overflow at " + width);
      assert(await page.locator(".platform-table-wrap").evaluate(el => el.scrollWidth <= el.clientWidth + 1), "Table overflow at " + width);
      await page.screenshot({ path: `${out}/tenants-${width}.png`, fullPage: true });
    }
    await page.locator("tbody tr").filter({ hasText: "tienda-centro" })
      .getByRole("link", { name: "Importar / Exportar", exact: true }).click();
    assert(await page.locator("#backup-file").isEnabled(), "Active store must allow file selection");
    assert(await page.getByRole("button", { name: "Importar y reemplazar datos" }).isDisabled(), "Active store cannot be restored");
    const activePicker = page.waitForEvent("filechooser");
    await page.locator("#backup-file").click();
    await (await activePicker).setFiles([]);
    await page.goto(base + "/platform/tenants");
    const row = page.locator("tbody tr").filter({ hasText: "tienda-norte" });
    await row.getByRole("link", { name: "Importar / Exportar", exact: true }).click();
    assert(await page.locator("#backup-file").isEnabled());
    assert(await page.getByRole("heading", { name: "Importar / Exportar", exact: true }).isVisible());
    for (const width of [1440, 390, 320]) {
      await page.setViewportSize({ width, height: 1000 });
      for (const theme of ["tienda", "sunset"]) {
        await page.locator(".theme-trigger").click();
        await page.locator('[data-theme-option="' + theme + '"]').click();
        await page.waitForTimeout(400);
        assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), "Overflow at " + width);
        const contrast = await page.locator("#backup-confirm").evaluate(el => {
          const style = getComputedStyle(el); return [style.color, style.backgroundColor];
        });
        assert.notEqual(contrast[0], contrast[1]);
        await page.screenshot({ path: `${out}/${width}-${theme}.png`, fullPage: true });
      }
    }
    const downloadEvent = page.waitForEvent("download");
    await page.getByRole("link", { name: "Descargar ZIP" }).click();
    const download = await downloadEvent;
    assert.equal(await download.failure(), null);
    await download.saveAs(out + "/preview-backup.zip");
    const picker = page.waitForEvent("filechooser");
    await page.locator("#backup-file").click();
    await (await picker).setFiles(out + "/preview-backup.zip");
    await page.locator("#backup-confirm").fill("IMPORTAR tienda-norte");
    await page.getByRole("button", { name: "Importar y reemplazar datos" }).click();
    await page.getByText("Respaldo importado exclusivamente", { exact: false }).waitFor();
    assert.deepEqual(errors, []);
    console.log("Tenant backup UI: desktop/mobile, light/dark, ZIP download/upload passed");
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
