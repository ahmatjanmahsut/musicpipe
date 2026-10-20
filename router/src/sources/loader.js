import vm from "node:vm";
import crypto from "node:crypto";
import zlib from "node:zlib";
import { fetchWithTimeout } from "../util.js";

const EVENT_NAMES = { inited: "inited", request: "request", updateAlert: "updateAlert" };

function parseHeader(code) {
  var info = { name: "", description: "", version: "", author: "", homepage: "", rawScript: code };
  var m = /@([a-z]+)\s+([^\n*]+)/g, h;
  var head = code.slice(0, 2000);
  while ((h = m.exec(head)) !== null) {
    var k = h[1].toLowerCase();
    if (k in info) info[k] = h[2].trim();
  }
  return info;
}

function fixJsonKeys(s) {
  return String(s)
    .replace(/([{,]\s*)([A-Za-z_$][A-Za-z0-9_$]*)\s*:/g, '$1"$2":')
    .replace(/,\s*([}\]])/g, "$1");
}

export function loadSourceScript(code, opts) {
  opts = opts || {};
  var initInfo = null;
  var updateAlert = null;
  var handlers = [];
  var scriptInfo = parseHeader(code);

  var utils = {
    buffer: {
      from: function () { return Buffer.from.apply(Buffer, arguments); },
      bufToString: function (buf, format) { return Buffer.from(buf).toString(format); },
    },
    crypto: {
      md5: function (str) { return crypto.createHash("md5").update(str).digest("hex"); },
      randomBytes: function (size) { return crypto.randomBytes(size).toString("hex"); },
      aesEncrypt: function (buf, mode, key, iv) {
        var cipher = crypto.createCipheriv(mode, Buffer.from(key), iv ? Buffer.from(iv) : null);
        return Buffer.concat([cipher.update(Buffer.from(buf)), cipher.final()]);
      },
      rsaEncrypt: function (buf, key) {
        var b = Buffer.from(buf);
        b = Buffer.concat([Buffer.alloc(128 - b.length, 0), b]);
        return crypto.publicEncrypt({ key: key, padding: crypto.constants.RSA_NO_PADDING }, b);
      },
    },
    zlib: {
      inflate: function (buf) { return new Promise(function (res, rej) { zlib.inflate(Buffer.from(buf), function (e, r) { e ? rej(e) : res(r); }); }); },
      deflate: function (buf) { return new Promise(function (res, rej) { zlib.deflate(Buffer.from(buf), function (e, r) { e ? rej(e) : res(r); }); }); },
    },
  };

  function lxRequest(url, options, callback) {
    options = options || {};
    var ctl = new AbortController();
    var timer = setTimeout(function () { ctl.abort(); }, options.timeout || 15000);
    var init = { method: (options.method || "GET").toUpperCase(), headers: Object.assign({}, options.headers), signal: ctl.signal };
    if (options.body !== undefined && options.body !== null) {
      if (typeof options.body === "string" || Buffer.isBuffer(options.body)) init.body = options.body;
      else {
        init.body = JSON.stringify(options.body);
        if (!init.headers["Content-Type"] && !init.headers["content-type"]) init.headers["Content-Type"] = "application/json";
      }
    } else if (options.form && typeof options.form === "object") {
      init.body = new URLSearchParams(options.form).toString();
      init.headers["Content-Type"] = "application/x-www-form-urlencoded";
    }
    fetch(String(url), init).then(async function (r) {
      var ab = await r.arrayBuffer();
      var buf = Buffer.from(ab);
      var text = buf.toString("utf8");
      var parsed = text;
      var ct = r.headers.get("content-type") || "";
      if (/json|text/.test(ct) || /^\s*[{[]/.test(text)) {
        try { parsed = JSON.parse(text); } catch (e) { try { parsed = JSON.parse(fixJsonKeys(text)); } catch (e2) { parsed = text; } }
      }
      clearTimeout(timer);
      var resp = { statusCode: r.status, headers: Object.fromEntries(r.headers.entries()), body: parsed, rawBody: buf };
      if (callback) callback(null, resp, parsed);
      return resp;
    }).catch(function (err) {
      clearTimeout(timer);
      if (callback) callback(err, null, null);
    });
    return function () { ctl.abort(); };
  }

  function makeXHR() {
    return function XMLHttpRequest() {
      var self = this;
      this.readyState = 0; this.status = 0; this.responseText = ""; this.response = "";
      this.onreadystatechange = null; this.onload = null; this.onerror = null; this.ontimeout = null;
      var _method = "GET", _url = "", _headers = {}, _timeout = 0;
      this.open = function (m, u) { _method = String(m).toUpperCase(); _url = String(u); self.readyState = 1; };
      this.setRequestHeader = function (k, v) { _headers[k] = v; };
      this.getAllResponseHeaders = function () { return self._rh || ""; };
      this.getResponseHeader = function (k) { return (self._rhMap || {})[String(k).toLowerCase()] || null; };
      this.setTimeout = function (ms) { _timeout = ms; };
      Object.defineProperty(self, "timeout", { set: function (ms) { _timeout = ms; }, get: function () { return _timeout; } });
      this.abort = function () { if (self._ctl) try { self._ctl.abort(); } catch (e) {} };
      this.send = function (body) {
        self._ctl = new AbortController();
        var tm = setTimeout(function () { self.ontimeout && self.ontimeout(); self._ctl.abort(); }, _timeout || 20000);
        var opts = { method: _method, headers: _headers, signal: self._ctl.signal };
        if (body != null && _method !== "GET") opts.body = body;
        fetch(String(_url), opts).then(async function (r) {
          clearTimeout(tm);
          self.status = r.status; self.readyState = 4;
          self._rhMap = Object.fromEntries(r.headers.entries());
          self._rh = [...r.headers.entries()].map(function (e2) { return e2[0] + ": " + e2[1]; }).join("\r\n");
          self.responseText = await r.text(); self.response = self.responseText;
          self.onreadystatechange && self.onreadystatechange(); self.onload && self.onload();
        }).catch(function (e) {
          clearTimeout(tm); self.readyState = 4;
          self.onerror && self.onerror(e);
        });
      };
    };
  }
  var lx = {
    EVENT_NAMES: EVENT_NAMES,
    version: "1.2.0",
    env: "desktop",
    currentScriptInfo: scriptInfo,
    utils: utils,
    request: lxRequest,
    on: function (name, fn) { if (name === EVENT_NAMES.request && typeof fn === "function") handlers.push(fn); },
    send: function (name, data) {
      if (name === EVENT_NAMES.inited && !initInfo) initInfo = data || {};
      if (name === EVENT_NAMES.updateAlert) updateAlert = data || null;
    },
  };

  var sandbox = {
    lx: lx,
    console: { log: function () {}, warn: function () {}, error: function () {}, info: function () {}, debug: function () {}, group: function () {}, groupEnd: function () {}, table: function () {}, trace: function () {}, dir: function () {}, time: function () {}, timeEnd: function () {}, count: function () {} },
    setTimeout: setTimeout, clearTimeout: clearTimeout, setInterval: setInterval, clearInterval: clearInterval, setImmediate: setImmediate,
    fetch: function (u, o) { return fetchWithTimeout(String(u), o || {}, 15000); },
    AbortController: AbortController, URL: URL, URLSearchParams: URLSearchParams,
    TextEncoder: TextEncoder, TextDecoder: TextDecoder, Buffer: Buffer,
    btoa: function (s) { return Buffer.from(String(s), "binary").toString("base64"); },
    atob: function (s) { return Buffer.from(String(s), "base64").toString("binary"); },
    Math: Math, JSON: JSON, Date: Date, Promise: Promise, RegExp: RegExp, Error: Error,
    Array: Array, Object: Object, String: String, Number: Number, Boolean: Boolean,
    Map: Map, Set: Set, Symbol: Symbol, Function: Function, Reflect: Reflect, Proxy: Proxy,
    crypto: globalThis.crypto, encodeURIComponent: encodeURIComponent, decodeURIComponent: decodeURIComponent,
    navigator: { userAgent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36", platform: "Win32", language: "zh-CN" },
    localStorage: { getItem: function () { return null; }, setItem: function () {}, removeItem: function () {}, clear: function () {} },
    XMLHttpRequest: makeXHR(),
    parseInt: parseInt, parseFloat: parseFloat, isNaN: isNaN,
    process: undefined, require: undefined, module: undefined,
  };
  sandbox.globalThis = sandbox;
  sandbox.window = sandbox;
  sandbox.self = sandbox;

  var context = vm.createContext(sandbox);
  vm.runInContext(code, context, { timeout: opts.evalTimeoutMs || 5000, filename: "source.js" });

  function waitInited() {
    return new Promise(function (resolve, reject) {
      var waited = 0;
      (function loop() {
        if (initInfo) return resolve();
        if (waited >= (opts.initTimeoutMs || 10000)) return reject(new Error("音源脚本未发送 inited 事件（10秒超时），不是标准自定义源或初始化失败"));
        waited += 50;
        setTimeout(loop, 50);
      })();
    });
  }

  return {
    waitInited: waitInited,
    getInfo: function () { return initInfo; },
    scriptInfo: scriptInfo,
    updateAlert: function () { return updateAlert; },
    supports: function (platform) { return !!(initInfo && initInfo.sources && initInfo.sources[platform]); },
    qualitys: function (platform) {
      var s = initInfo && initInfo.sources && initInfo.sources[platform];
      return s ? s.qualitys || [] : [];
    },
    platforms: function () { return Object.keys((initInfo && initInfo.sources) || {}); },
    dispatch: function (source, action, info) {
      if (!handlers.length) return Promise.reject(new Error("音源无 request 处理器"));
      var timeoutMs = opts.requestTimeoutMs || 25000;
      return new Promise(function (resolve, reject) {
        var settled = false, lastErr = null;
        var timer = setTimeout(function () { if (!settled) { settled = true; reject(lastErr || new Error(action + " 请求超时")); } }, timeoutMs);
        var ok = function (v) { if (!settled) { settled = true; clearTimeout(timer); resolve(v); } };
        var ko = function (e) { if (!settled) { settled = true; clearTimeout(timer); reject(e instanceof Error ? e : new Error(String(e))); } };
        var chain = Promise.resolve();
        handlers.forEach(function (h) {
          chain = chain.then(function () {
            if (settled) return;
            return Promise.resolve(h({ source: source, action: action, info: info })).then(function (v) {
              if (v !== undefined && v !== null && v !== "") ok(v);
            }).catch(function (e) { lastErr = e; });
          });
        });
        chain.then(function () { if (!settled) ko(lastErr || new Error(action + " 无有效返回")); }).catch(function (e) { if (!settled) ko(e); });
      });
    },
  };
}
