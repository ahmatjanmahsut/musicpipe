import Fastify from "fastify";
import cookie from "@fastify/cookie";
import multipart from "@fastify/multipart";
import fastifyStatic from "@fastify/static";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { initDb } from "./db/index.js";
import { ENV } from "./config.js";
import { authHook } from "./auth.js";
import { initRegistry } from "./sources/registry.js";
import { startQueue, broadcast as bindBroadcast } from "./downloader/queue.js";
import { attachWs, broadcast } from "./plugins/ws.js";
import { registerRoutes } from "./routes/all.js";
import { registerAppSvc } from "./routes/appsvc.js";
import { registerGateway } from "./gateway.js";
import { startShareScheduler } from "./pipeline/sharelink.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

fs.mkdirSync(ENV.stagingDir, { recursive: true });
fs.mkdirSync(ENV.tmpDir, { recursive: true });

initDb(ENV.dataDir);
initRegistry(path.join(ENV.dataDir, "sources"));

const app = Fastify({ logger: false, bodyLimit: 8 * 1024 * 1024 });
await app.register(cookie);
await app.register(multipart, { limits: { fileSize: 300 * 1024 * 1024 } }); // APK 发布上传

app.addHook("preHandler", authHook);
app.addHook("onSend", function (req, reply, payload, done) {
  // 尊重路由显式设置的 Cache-Control（如封面 max-age=3600），其余一律 no-store
  if (!reply.hasHeader("Cache-Control")) reply.header("Cache-Control", "no-store");
  done();
});

registerRoutes(app, broadcast);
registerGateway(app);
registerAppSvc(app); // App 多用户账号 + 用户管理 + APK 发布/更新服务
startShareScheduler(broadcast);

// vendor 静态：Vue + Element Plus 直接来自 node_modules（零构建）
var vendorMap = {
  "vue.global.prod.js": ["vue", "dist/vue.global.prod.js", "application/javascript"],
  "element-plus.full.min.js": ["element-plus", "dist/index.full.min.js", "application/javascript"],
  "element-plus.css": ["element-plus", "dist/index.css", "text/css"],
  "icons.iife.min.js": ["@element-plus/icons-vue", "dist/index.iife.min.js", "application/javascript"],
};
Object.keys(vendorMap).forEach(function (name) {
  var spec = vendorMap[name];
  var file = path.join(__dirname, "..", "node_modules", spec[0], spec[1]);
  app.get("/vendor/" + name, function (req, reply) {
    reply.type(spec[2]);
    return reply.send(fs.createReadStream(file));
  });
});

await app.register(fastifyStatic, { root: path.join(__dirname, "..", "web"), wildcard: false });

// SPA fallback（hash 路由其实不需要，但保险）
app.get("/*", function (req, reply) {
  if (req.url.startsWith("/api/") || req.url.startsWith("/vendor/")) { reply.code(404); return { error: "not found" }; }
  if (/\.[a-z0-9]{1,8}(\?|$)/i.test(req.url)) { reply.code(404); return "not found"; }
  return reply.type("text/html").send(fs.createReadStream(path.join(__dirname, "..", "web", "index.html")));
});

app.addHook("onClose", function () {});

bindBroadcast(broadcast);

app.listen({ port: ENV.port, host: "0.0.0.0" }, function (err, address) {
  if (err) { console.error("listen fail:", err); process.exit(1); }
  console.log("MusicPipe server at " + address);
  attachWs(app.server);
  startQueue().catch(function (e) { console.error("queue start fail:", e); });
});

process.on("unhandledRejection", function (e) { console.error("unhandled:", e && e.message || e); });
