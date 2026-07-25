const fs = require("fs");
const path = require("path");

const loaderPath = path.resolve(config.basePath, "load-skiko.js");
fs.writeFileSync(
    loaderPath,
    [
        "(function () {",
        "  const karma = window.__karma__;",
        "  const originalLoaded = karma.loaded.bind(karma);",
        "  karma.loaded = function () {};",
        "  import('/base/kotlin/js-reexport-symbols.mjs')",
        "    .then((m) => m.api.awaitSkiko)",
        "    .then(() => originalLoaded())",
        "    .catch((e) => karma.error('Failed to load skiko: ' + e));",
        "})();"
    ].join("\n")
);

config.mime = config.mime || {};
config.mime["text/javascript"] = ["mjs"];

config.files = [
    { pattern: loaderPath, included: true, served: true, watched: false },
    { pattern: path.resolve(config.basePath, "kotlin", "js-reexport-symbols.mjs"), included: false, served: true, watched: false },
    { pattern: path.resolve(config.basePath, "kotlin", "skiko.mjs"), included: false, served: true, watched: false },
    { pattern: path.resolve(config.basePath, "kotlin", "skikod8.mjs"), included: false, served: true, watched: false },
    { pattern: path.resolve(config.basePath, "kotlin", "skiko.wasm"), included: false, served: true, watched: false }
].concat(config.files);
