const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

// Run only against PlatformPreviewServer's disposable, in-memory database.
(async () => {
  const base = process.env.PREVIEW_URL;
  assert(/^http:\/\/127\.0\.0\.1:\d+$/.test(base || ""), "Isolated preview URL required");
  const out = process.env.VISUAL_OUTPUT || "target/product-table-visual";
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH, headless: true });
  const errors = [];
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    page.on("pageerror", error => errors.push(error.message));
    page.on("dialog", async dialog => { errors.push(dialog.message()); await dialog.dismiss(); });
    await page.goto(base + "/admin/login");
    await page.locator('[name="username"]').fill("tienda-centro");
    await page.locator('[name="password"]').fill("Test-only-493!");
    await page.locator('button[type="submit"]').click();
    await page.waitForURL(base + "/admin");
    await page.goto(base + "/admin/products/new");
    const csrf = await page.locator('input[name="_csrf"]').first().inputValue();
    let response = await page.request.get(base + "/admin/api/products/table");
    const before = await response.json();
    if (before.recordsTotal === 0) {
      for (let i = 0; i < 45; i++) {
        response = await page.request.post(base + "/admin/products", {
          form: { _csrf: csrf, code: "QA-" + String(i).padStart(3, "0"),
            name: i === 0 ? 'QA Cafe "quoted" <b>text</b>' : "QA Cafe " + String(i).padStart(3, "0"),
            barcode: "998800" + String(i).padStart(7, "0"), brand: i % 2 ? "Marca B" : "Marca A",
            purchaseCost: "10", salePrice: "20", currentStock: "100", minimumStock: "0", unit: "PIEZA", active: "true" },
          maxRedirects: 0
        });
        assert.equal(response.status(), 302, "Fixture product " + i);
      }
    } else assert.equal(before.recordsTotal, 45, "Unexpected database: use a fresh preview");
    await page.goto(base + "/admin/products");
    const info = () => page.evaluate(() => jQuery("#productsTable").DataTable().page.info());
    const waitTotal = total => page.waitForFunction(total => window.jQuery?.fn.DataTable.isDataTable("#productsTable")
      && jQuery("#productsTable").DataTable().page.info().recordsDisplay === total, total);
    await waitTotal(45);
    assert.equal((await info()).pages, 3);
    await page.locator(".dt-paging-button.next").click();
    await page.waitForFunction(() => jQuery("#productsTable").DataTable().page.info().page === 1);
    await page.locator('[data-open-label-modal]').first().click();
    assert(await page.locator("[data-label-modal]").isVisible());
    await page.locator("[data-label-cancel]").click();
    await page.locator('[data-product-filter-form] [name="q"]').fill("QA-044");
    await waitTotal(1);
    assert.equal((await info()).page, 0);
    await page.locator("[data-clear-product-filters]").click();
    await waitTotal(45);
    await page.locator(".dt-scroll-head thead th").nth(2).locator(".dtcc-button_dropdown").click();
    await page.locator(".dtcc-dropdown:visible").getByRole("button", { name: "Marca A", exact: true }).click();
    await waitTotal(23);
    await page.locator("h1").click();
    await page.locator("[data-clear-product-filters]").click();
    await waitTotal(45);
    await page.locator('[data-product-filter-form] [name="brand"]').fill("Marca A");
    await waitTotal(23);
    await page.locator("[data-clear-product-filters]").click();
    await waitTotal(45);
    await page.locator('[data-whatsapp-promotion]').first().check({ force: true });
    await page.waitForFunction(() => jQuery("#productsTable").DataTable().rows().data().toArray().some(p => p.whatsapp));
    await page.locator('[name="whatsapp"]').first().selectOption("true");
    await waitTotal(1);
    assert.equal(await page.locator("[data-products-table] strong b").count(), 0, "Product names must be text");
    await page.locator("[data-clear-product-filters]").click();
    await waitTotal(45);
    for (const width of [1440, 390, 320]) {
      await page.setViewportSize({ width, height: 1000 });
      for (const theme of ["tienda", "sunset"]) {
        await page.locator(".theme-trigger").click();
        await page.locator('[data-theme-option="' + theme + '"]').click();
        await page.locator('[data-autocomplete-field="name"]').fill("QA");
        await page.locator("[data-autocomplete-menu]:visible button").first().waitFor();
        const background = await page.locator("[data-autocomplete-menu]:visible").evaluate(el => getComputedStyle(el).backgroundColor);
        assert.notEqual(background, "rgba(0, 0, 0, 0)");
        assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), "Viewport overflow at " + width);
        await page.screenshot({ path: path.join(out, width + "-" + theme + ".png"), fullPage: true });
        await page.locator("[data-clear-product-filters]").click();
        await waitTotal(45);
      }
    }
    await page.goto(base + "/admin/pos");
    if (await page.locator('[data-cash-open="true"]').count()) {
      await page.getByRole("button", { name: "Cerrar caja", exact: true }).click();
      await page.waitForSelector('[data-cash-open="false"]');
    }
    assert(await page.locator("[data-barcode-input]").isDisabled(), "Closed register must block scanning");
    await page.getByRole("button", { name: "Abrir caja", exact: true }).click();
    await page.waitForSelector('[data-cash-open="true"]');
    await page.locator("[data-barcode-input]").fill("5*9988000000000");
    await page.locator("[data-barcode-input]").press("Enter");
    await page.waitForFunction(() => document.querySelector("[data-qty]")?.value === "5");
    await page.locator("[data-barcode-input]").fill("0*9988000000000");
    await page.locator("[data-barcode-input]").press("Enter");
    assert.equal(await page.locator("[data-qty]").inputValue(), "5");
    assert.deepEqual(errors, []);
    console.log("PASS: pagination, live filters, labels, WhatsApp, escaped names, 6 theme/viewport checks, POS quantity and closed-register guard.");
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
