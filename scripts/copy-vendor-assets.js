const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const source = path.join(root, "node_modules", "@zxing", "browser", "umd", "zxing-browser.min.js");
const targetDir = path.join(root, "src", "main", "resources", "static", "vendor", "zxing");
const target = path.join(targetDir, "zxing-browser.min.js");

if (!fs.existsSync(source)) {
  throw new Error(`No se encontro ${source}. Ejecuta pnpm install primero.`);
}
fs.mkdirSync(targetDir, { recursive: true });
fs.copyFileSync(source, target);
console.log(`ZXing copiado a ${path.relative(root, target)}`);