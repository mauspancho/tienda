const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
function contrast(a, b) {
  const luminance = value => {
    const rgb = value.match(/[\d.]+/g).slice(0,3).map(Number).map(c => {
      c /= 255; return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
    });
    return rgb[0]*0.2126 + rgb[1]*0.7152 + rgb[2]*0.0722;
  };
  const values = [luminance(a),luminance(b)].sort((x,y) => y-x);
  return (values[0]+0.05)/(values[1]+0.05);
}
(async () => {
  const base = process.env.PREVIEW_URL;
  assert(base && /^http:\/\/127\.0\.0\.1:\d+$/.test(base), "Use the isolated local preview server");
  const out = path.resolve("target/platform-visual");
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH, headless: true });
  const results = [];
  const errors = [];
  try {
    for (const width of [1440, 390, 320]) {
      const context = await browser.newContext({ viewport: { width, height: 900 } });
      const page = await context.newPage();
      page.on("pageerror", error => errors.push(error.message));
      await page.goto(base + "/admin/login");
      await page.locator('input[name="username"]').fill("preview");
      await page.locator('input[name="password"]').fill("Preview-only-493!");
      await page.locator('button[type="submit"]').click();
      await page.waitForURL("**/platform/tenants");
      for (const theme of ["tienda", "sunset"]) {
        await page.locator(".theme-trigger").click();
        await page.locator('[data-theme-option="' + theme + '"]').click();
        for (const route of ["/platform/tenants", "/platform/tenants/new", "/platform/tenants/2", "/platform/tenants/2/edit"]) {
          const response = await page.goto(base + route);
          assert.equal(response.status(), 200, route);
          await page.locator("h1").waitFor();
          const data = await page.evaluate(() => {
            const controls = [...document.querySelectorAll("main input")].filter(e => e.getBoundingClientRect().height > 0);
            const fields = controls.map(e => { const s = getComputedStyle(e); return { color:s.color, background:s.backgroundColor, border:s.borderColor, height:e.getBoundingClientRect().height }; });
            return { width:innerWidth, scrollWidth:document.documentElement.scrollWidth, fields,
              images:[...document.images].every(e => e.complete && e.naturalWidth > 0),
              theme:document.documentElement.dataset.theme, title:document.querySelector("h1").textContent };
          });
          assert(data.scrollWidth <= width + 1, "Page overflow: " + route + " at " + width);
          assert(data.images, "Missing icon");
          assert.equal(data.theme, theme);
          for (const field of data.fields) {
            assert.equal(field.height,46);
            assert(contrast(field.color,field.background)>=4.5,"Text contrast");
            assert(contrast(field.border,field.background)>=3,"Input border contrast");
          }
          const name = width + "-" + theme + "-" + route.split("/").filter(Boolean).join("-");
          await page.screenshot({ path:path.join(out,name+".png"), fullPage:true });
          await page.screenshot({ path:path.join(out,name+".jpg"), fullPage:true, type:"jpeg", quality:80 });
          results.push({ route, theme, width, ...data });
        }
      }
      if (width === 1440) {
        await page.goto(base + "/platform/tenants/new");
        const unique = Date.now().toString();
        const values={ name:"Tienda creada desde navegador", code:"browser-"+unique,businessName:"Negocio navegador",phone:"5551112233",
          address:"Calle de prueba 14",adminFirstName:"Admin",adminLastName:"Browser",adminUsername:"browser-"+unique,
          password:"Browser-only-493!",confirmPassword:"Browser-only-493!" };
        for (const [id,value] of Object.entries(values)) await page.locator("#"+id).fill(value);
        await page.getByRole("button",{name:"Crear tienda",exact:true}).click();
        await page.waitForURL(/\/platform\/tenants\/\d+$/);
        assert(await page.getByText("Tienda creada correctamente.",{exact:true}).isVisible());
        await page.getByText("Cambiar contraseña",{exact:true}).click();
        assert(await page.locator('input[name="password"]').isVisible());
      }
      await page.goto(base + "/admin/products");
      assert(await page.locator('a[href="/platform/tenants"]').count() > 0);
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), "Operational navigation overflow");
      if (width < 1024) {
        assert(await page.evaluate(() => document.querySelector(".brand-name").getBoundingClientRect().bottom <= document.querySelector(".app-nav-pos").getBoundingClientRect().top), "Brand overlaps point of sale");
        await page.locator(".mobile-menu-trigger").click();
        assert(await page.locator('.mobile-menu-list a[href="/platform/tenants"]').isVisible());
        await page.locator(".mobile-menu-trigger").click();
      }
      await page.screenshot({ path:path.join(out,width+"-operational-navbar.jpg"), fullPage:true, type:"jpeg" });
      await context.close();
    }
    assert.deepEqual(errors,[]);
    fs.writeFileSync(path.join(out,"results.json"),JSON.stringify(results,null,2));
    console.log("VISUAL_QA_SUCCESS: "+results.length+" page/theme/viewport checks; browser tenant creation passed");
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode=1; });
