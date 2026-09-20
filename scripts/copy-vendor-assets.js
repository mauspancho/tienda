const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const assets = [
  {
    source: path.join(root, "node_modules", "@zxing", "browser", "umd", "zxing-browser.min.js"),
    target: path.join(root, "src", "main", "resources", "static", "vendor", "zxing", "zxing-browser.min.js")
  },
  {
    source: path.join(root, "node_modules", "jquery", "dist", "jquery.min.js"),
    target: path.join(root, "src", "main", "resources", "static", "vendor", "jquery", "jquery.min.js")
  },
  {
    source: path.join(root, "node_modules", "datatables.net", "js", "dataTables.min.js"),
    target: path.join(root, "src", "main", "resources", "static", "vendor", "datatables", "dataTables.min.js")
  },
  {
    source: path.join(root, "node_modules", "datatables.net-dt", "js", "dataTables.dataTables.min.js"),
    target: path.join(root, "src", "main", "resources", "static", "vendor", "datatables", "dataTables.dataTables.min.js")
  },
  {
    source: path.join(root, "node_modules", "datatables.net-dt", "css", "dataTables.dataTables.min.css"),
    target: path.join(root, "src", "main", "resources", "static", "vendor", "datatables", "dataTables.dataTables.min.css")
  }
];

for (const asset of assets) {
  if (!fs.existsSync(asset.source)) {
    throw new Error(`No se encontro ${asset.source}. Ejecuta pnpm install primero.`);
  }
  fs.mkdirSync(path.dirname(asset.target), { recursive: true });
  fs.copyFileSync(asset.source, asset.target);
  console.log(`Vendor copiado a ${path.relative(root, asset.target)}`);
}
