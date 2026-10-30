var { createApp, reactive } = Vue;

// ====== 保留 helper（与旧版逐字一致） ======
var api = async function (path, opts) {
  opts = opts || {};
  var o = { method: opts.method || "GET", headers: {}, credentials: "same-origin" };
  if (opts.json !== undefined) { o.headers["Content-Type"] = "application/json"; o.body = JSON.stringify(opts.json); }
  if (opts.formData) { o.body = opts.formData; } // 先挂 body，且不设 Content-Type，让浏览器自动生成 multipart boundary
  if (o.method !== "GET" && o.body === undefined) o.headers["Content-Type"] = "text/plain";
  var r = await fetch(path, o);
  var j = null;
  try { j = await r.json(); } catch (e) { j = { error: "HTTP " + r.status }; }
  if (r.status === 401) { throw new Error("unauthorized:401"); }
  if (!r.ok && j && j.error) { throw new Error(j.error); }
  return j;
};

// ====== 全局 WS ======
var wsState = reactive({ ok: false, lastTask: null, listeners: [] });
var wsRetry = 0;
function connectWs() {
  try {
    var proto = location.protocol === "https:" ? "wss:" : "ws:";
    var ws = new WebSocket(proto + "//" + location.host + "/ws");
    ws.onopen = function () { wsState.ok = true; wsRetry = 0; };
    ws.onmessage = function (ev) {
      try {
        var m = JSON.parse(ev.data);
        if (m.event === "task") { wsState.lastTask = m.data; wsState.listeners.forEach(function (f) { f(m.data); }); }
      } catch (e) {}
    };
    ws.onclose = function () {
      wsState.ok = false;
      wsRetry++;
      setTimeout(connectWs, Math.min(30000, 2000 * wsRetry));
    };
    ws.onerror = function () { try { ws.close(); } catch (e) {} };
    window._mpws = ws;
  } catch (e) { setTimeout(connectWs, 5000); }
}
connectWs();

function fmtTs(ts) { if (!ts) return ""; var d = new Date(Number(ts)); return d.toLocaleString("zh-CN", { hour12: false }); }
function mb(n) {
  if (!Number.isFinite(n) || n <= 0) return "0";
  var KB = 1024, MB = KB * 1024, GB = MB * 1024, TB = GB * 1024;
  // 超过 1TB 用 TB（rclone 挂载会报 1PiB 级伪容量，用 GB 显示会变成 1048576.00GB 这种噪音）
  if (n > TB) return (n / TB).toFixed(2) + "TB";
  if (n > GB) return (n / GB).toFixed(2) + "GB";
  if (n > MB) return (n / MB).toFixed(1) + "MB";
  return Math.round(n / KB) + "KB";
}
function dur(s) { s = Number(s) || 0; return Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0"); }
function clip(s, n) { s = String(s || ""); return s.length > (n || 90) ? s.slice(0, n || 90) + "…" : s; }
var PLAT_NAMES = { kw: "酷我", kg: "酷狗", tx: "QQ", wy: "网易", mg: "咪咕", bd: "百度", rqfy: "热歌", apple: "苹果" };

// ====== 环境探针 ======
var REDUCED = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;
var TOUCH = window.matchMedia && (matchMedia("(pointer: coarse)").matches || matchMedia("(hover: none)").matches);
var FINE = window.matchMedia && matchMedia("(pointer: fine)").matches;
var HAS_GSAP = !!(window.gsap);
var HAS_ST = !!(window.gsap && window.ScrollTrigger);
if (HAS_ST) gsap.registerPlugin(ScrollTrigger);

// ====== MP 命名空间：confirm / motion / toast ======
var MP = window.MP = {};
MP.reduced = REDUCED;

// ---- 机制 2：$toast（kind=ok|warn|err，3.5s，gsap 进退场；替换全部旧消息条） ----
var mpToasts = reactive({ list: [], seq: 0 });
function toastFindEl(id) { return document.querySelector('.mp-toast__item[data-tid="' + id + '"]'); }
function toastRemove(t) {
  var i = mpToasts.list.indexOf(t);
  if (!HAS_GSAP || REDUCED) { if (i >= 0) mpToasts.list.splice(i, 1); return; }
  var el = toastFindEl(t.id);
  if (i >= 0) mpToasts.list.splice(i, 1);
  if (el) gsap.to(el, { x: 32, opacity: 0, duration: .3, ease: "power2.in" });
}
MP.toast = function (kind, msg) {
  var t = { id: ++mpToasts.seq, kind: kind, msg: String(msg == null ? "" : msg) };
  mpToasts.list.push(t);
  setTimeout(function () {
    if (!HAS_GSAP || REDUCED) return;
    Vue.nextTick(function () {
      var el = toastFindEl(t.id);
      if (el) gsap.fromTo(el, { x: 32, opacity: 0 }, { x: 0, opacity: 1, duration: .35, ease: "expo.out" });
    });
  }, 0);
  setTimeout(function () { toastRemove(t); }, 3500);
};

// ---- 机制：界面风格 6 套 × 暗/亮/跟随系统（本机偏好，不进后端 /api/settings） ----
// 契约：docs/STYLE-SYSTEM-CONTRACT.md §0/§5。页面/组件只读 token，这里只负责切
// <html data-style> / <html data-scheme> + localStorage + meta[theme-color]，不碰任何色值字面量。
var MP_STYLES = [
  { id: "awwwards", name: "Editorial", tagline: "暗底 · 噪点 · 硬边 · 编辑排版" },
  { id: "classic", name: "Classic", tagline: "红白配色 · 圆角卡片 · 旧版 MusicPipe" },
  { id: "material", name: "Material You", tagline: "Google 官方 · 色调表面 · 大圆角" },
  { id: "cupertino", name: "Cupertino", tagline: "Apple 风格 · 分组列表 · 系统蓝" },
  { id: "shadcn", name: "Shadcn", tagline: "GitHub 最多 star · 中性 · 细边框" },
  { id: "brutal", name: "Brutalist", tagline: "粗描边 · 零圆角 · 高对比" }
];
var MP_STYLE_IDS = MP_STYLES.map(function (x) { return x.id; });
var MP_SCHEME_PREFS = ["system", "dark", "light"];
var mpStyleCur = "awwwards", mpSchemePref = "system";
var mpSchemeMQ = window.matchMedia ? window.matchMedia("(prefers-color-scheme: dark)") : null;

function mpLSGet(k, def) { try { var v = localStorage.getItem(k); return v == null ? def : v; } catch (e) { return def; } }
function mpLSSet(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }
function mpStyleNameOf(id) { for (var i = 0; i < MP_STYLES.length; i++) if (MP_STYLES[i].id === id) return MP_STYLES[i].name; return id; }
// 偏好 system 时按 prefers-color-scheme 实时解析；dark/light 直接就是结果
function mpSchemeResolved() {
  if (mpSchemePref === "dark") return "dark";
  if (mpSchemePref === "light") return "light";
  return (mpSchemeMQ && mpSchemeMQ.matches) ? "dark" : "light";
}
// theme-color 永远取当前皮肤 --color-paper 的计算值（JS 里零色值字面量 → 自然跟随风格/明暗）
function mpSyncThemeColor() {
  var meta = document.querySelector('meta[name="theme-color"]');
  if (!meta) { meta = document.createElement("meta"); meta.setAttribute("name", "theme-color"); document.head.appendChild(meta); }
  try {
    var paper = getComputedStyle(document.documentElement).getPropertyValue("--color-paper").trim();
    if (paper) meta.setAttribute("content", paper);
  } catch (e) {}
}
// 切换后重算滚动动效（ScrollTrigger 只在存在时调一次）
function mpStyleRefreshFx() {
  if (!window.Vue) return;
  Vue.nextTick(function () {
    if (window.ScrollTrigger && ScrollTrigger.refresh) { try { ScrollTrigger.refresh(); } catch (e) {} }
  });
}

var mpStyleApi = {
  list: MP_STYLES,
  // get() → { id, style, scheme(解析后 dark|light), pref(system|dark|light) }
  get: function () { return { id: mpStyleCur, style: mpStyleCur, scheme: mpSchemeResolved(), pref: mpSchemePref }; },
  // 把当前状态落到 DOM（不写 localStorage：启动收敛 / 系统明暗变化都复用它）
  apply: function () {
    var d = document.documentElement;
    d.setAttribute("data-style", mpStyleCur);
    d.setAttribute("data-scheme", mpSchemeResolved());
    mpSyncThemeColor();
  },
  setStyle: function (id) {
    if (MP_STYLE_IDS.indexOf(id) < 0 || id === mpStyleCur) return;
    mpStyleCur = id;
    mpLSSet("mp.style", id);
    mpStyleApi.apply();
    mpStyleRefreshFx();
    MP.toast("ok", "已切换为 " + mpStyleNameOf(id));
  },
  setScheme: function (pref) {
    if (MP_SCHEME_PREFS.indexOf(pref) < 0) return;
    var changed = pref !== mpSchemePref;
    mpSchemePref = pref;
    mpLSSet("mp.scheme", pref);
    mpStyleApi.apply();
    if (changed) {
      mpStyleRefreshFx();
      MP.toast("ok", "明暗：" + ({ system: "跟随系统", dark: "深色", light: "浅色" }[pref] || pref));
    }
  }
};
MP.style = mpStyleApi;

// 启动：读偏好 → 应用（head 内联脚本已先做过一次，这里保证 localStorage 不可用时也收敛到默认）
(function initMPStyle() {
  var sid = mpLSGet("mp.style", "awwwards");
  if (MP_STYLE_IDS.indexOf(sid) < 0) sid = "awwwards";
  var pref = mpLSGet("mp.scheme", "system");
  if (MP_SCHEME_PREFS.indexOf(pref) < 0) pref = "system";
  mpStyleCur = sid; mpSchemePref = pref;
  mpStyleApi.apply();
  // 跟随系统：仅当偏好为 system 时，OS 明暗变化才重算
  if (mpSchemeMQ) {
    var onMQ = function () { if (mpSchemePref === "system") mpStyleApi.apply(); };
    if (mpSchemeMQ.addEventListener) mpSchemeMQ.addEventListener("change", onMQ);
    else if (mpSchemeMQ.addListener) mpSchemeMQ.addListener(onMQ);
  }
})();

// ---- 机制 3：MP.confirm → Promise（root modal 单例，替代全部旧气泡确认） ----
var mpConfirm = reactive({ open: false, title: "", body: "", danger: false, _res: null });
MP.confirm = function (opts) {
  opts = opts || {};
  return new Promise(function (res) {
    if (mpConfirm.open && mpConfirm._res) { var p = mpConfirm._res; mpConfirm._res = null; p(false); }
    mpConfirm.title = opts.title || "确认操作";
    mpConfirm.body = opts.body || "";
    mpConfirm.danger = !!opts.danger;
    mpConfirm.open = true;
    mpConfirm._res = res;
  });
};
function resolveConfirm(v) { mpConfirm.open = false; var f = mpConfirm._res; mpConfirm._res = null; if (f) f(v); };
MP.confirmResolve = resolveConfirm;

// ---- 机制 4：MP.motion（页面级注册/unmounted 清理；六类动效） ----
var loaderDone = false, revealQueue = [];
function flushReveals() { revealQueue.splice(0).forEach(function (f) { try { f(); } catch (e) {} }); }
MP.motion = {
  // 文字遮罩揭示：中文按字逐字 .rv-line>span，--rv-delay 错峰 .06，加 .rv-line--in 触发
  splitChars: function (line) {
    if (!line || line.dataset.rvDone) return;
    line.dataset.rvDone = "1";
    var inner = line.firstElementChild || line;
    var text = inner.textContent || "";
    line.classList.remove("rv-line");
    line.textContent = "";
    var cells = [];
    for (var i = 0, n = 0; i < text.length; i++) {
      var ch = text.charAt(i);
      if (ch === "\n" || ch === "\r") continue;
      var cell = document.createElement("span");
      cell.className = "rv-line rv-line--ch" + (ch === " " ? " rv-line--sp" : "");
      var sp = document.createElement("span");
      sp.textContent = ch;
      sp.style.transitionDelay = (n * 0.06).toFixed(3) + "s";
      cell.appendChild(sp);
      line.appendChild(cell);
      cells.push(cell);
      n++;
    }
    var reveal = function () {
      requestAnimationFrame(function () { requestAnimationFrame(function () {
        cells.forEach(function (c) { c.classList.add("rv-line--in"); });
      }); });
    };
    if (REDUCED) { cells.forEach(function (c) { c.classList.add("rv-line--in"); }); return; }
    if (loaderDone) reveal(); else revealQueue.push(reveal);
  },
  // countUp：数字滚动（配合 v-count 指令）
  countUp: function (el, from, to, fmt) {
    if (!HAS_GSAP || REDUCED) { el.textContent = fmt(to); return; }
    if (el._mpTw) el._mpTw.kill();
    var o = { v: from };
    el._mpTw = gsap.to(o, { v: to, duration: 1.05, ease: "power2.out", onUpdate: function () { el.textContent = fmt(o.v); }, onComplete: function () { el._cv = to; el.textContent = fmt(to); } });
  },
  // 磁吸：80px 半径 ×0.25，离开回弹
  magnet: function (el) {
    if (!HAS_GSAP || REDUCED || !FINE) return null;
    var onMove = function (e) {
      var r = el.getBoundingClientRect();
      var cx = r.left + r.width / 2, cy = r.top + r.height / 2;
      var dx = e.clientX - cx, dy = e.clientY - cy;
      if (Math.sqrt(dx * dx + dy * dy) < 80) gsap.to(el, { x: dx * 0.25, y: dy * 0.25, duration: .5, ease: "power3.out" });
    };
    var onLeave = function () { gsap.to(el, { x: 0, y: 0, duration: .7, ease: "elastic.out(1, .5)" }); };
    document.addEventListener("mousemove", onMove);
    el.addEventListener("mouseleave", onLeave);
    return function () { document.removeEventListener("mousemove", onMove); el.removeEventListener("mouseleave", onLeave); if (HAS_GSAP) gsap.killTweensOf(el); };
  },
  // 视差：[data-parallax] yPercent ±12 scrub
  parallax: function (el) {
    if (!HAS_ST || REDUCED) return null;
    var st = ScrollTrigger.create({
      trigger: el.closest(".page, .foot") || el, start: "top bottom", end: "bottom top", scrub: true,
      animation: gsap.fromTo(el, { yPercent: -12 }, { yPercent: 12, ease: "none" })
    });
    return st;
  },
  // 横向滚动 pin 版本墙（六类动效之一）；降级 = 原生横向滚动
  pinWall: function (section) {
    if (!HAS_ST || REDUCED || TOUCH) return null;
    var track = section.querySelector(".rel-wall__track");
    if (!track) return null;
    var dist = function () { return Math.max(0, track.scrollWidth - section.clientWidth + 16); };
    var tw = gsap.to(track, { x: function () { return -dist(); }, ease: "none" });
    var st = ScrollTrigger.create({
      trigger: section, start: "top top", end: function () { return "+=" + dist(); },
      pin: true, scrub: 1, invalidateOnRefresh: true, animation: tw,
      onRefresh: function (self) { if (dist() <= 0) { self.disable(); tw.progress(0); tw.kill(); gsap.set(track, { x: 0 }); } else self.enable(); }
    });
    section.classList.add("rel-pin--on");
    return function () { section.classList.remove("rel-pin--on"); st.kill(true); tw.kill(); gsap.set(track, { clearProps: "x" }); };
  },
  // 路由切换遮罩 wipe：相位类名由 base.css 定义（cover → reveal → reveal--out），0.6s
  wipe: function (before, after) {
    var el = document.querySelector(".route-wipe");
    if (!el || REDUCED) { before(); after(); return; }
    el.classList.add("route-wipe--cover");
    setTimeout(function () {
      before();
      el.classList.add("route-wipe--reveal");
      void el.offsetWidth;
      el.classList.add("route-wipe--out");
      setTimeout(function () { el.classList.remove("route-wipe--cover", "route-wipe--reveal", "route-wipe--out"); after(); }, 320);
    }, 300);
  },
  // 页面生命周期：由全局 mixin 调用（mounted 注册 / unmounted kill）
  page: function (vm) {
    var el = vm.$el;
    if (!el || el.nodeType !== 1) return;
    vm._mpFx = vm._mpFx || [];
    var fx = vm._mpFx;
    var kids = el.querySelectorAll(".rv-line, [data-reveal]");
    for (var i = 0; i < kids.length; i++) MP.motion.splitChars(kids[i]);
    var pars = el.querySelectorAll("[data-parallax]");
    for (i = 0; i < pars.length; i++) { var p = MP.motion.parallax(pars[i]); if (p) fx.push({ kill: function () { p.kill(); } }); }
    var mags = el.querySelectorAll("[data-magnet]");
    for (i = 0; i < mags.length; i++) { var m = MP.motion.magnet(mags[i]); if (m) fx.push({ kill: m }); }
    var wall = el.querySelector("[data-pin-x]");
    if (wall) { var w = MP.motion.pinWall(wall); if (w) fx.push({ kill: w }); }
  },
  leave: function (vm) {
    if (vm._mpFx) { vm._mpFx.forEach(function (f) { try { f.kill(); } catch (e) {} }); vm._mpFx = null; }
  }
};

// ---- Lenis 全局平滑滚动（reduced-motion / 触屏禁用） ----
var lenis = null;
if (!REDUCED && !TOUCH && window.Lenis && HAS_GSAP) {
  try {
    lenis = new Lenis({ lerp: 0.12, smoothWheel: true });
    gsap.ticker.add(function (time) { lenis.raf(time * 1000); });
    gsap.ticker.lagSmoothing(0);
    if (HAS_ST) lenis.on("scroll", ScrollTrigger.update);
  } catch (e) { lenis = null; }
}
MP.lenis = lenis;
MP.stopScroll = function () { if (lenis) lenis.stop(); };
MP.startScroll = function () { if (lenis) lenis.start(); };

// ---- 机制 5：自定义光标（(pointer:fine) + 非 reduced 才启用） ----
(function initCursor() {
  var cur = document.getElementById("mp-cursor");
  if (!cur) return;
  if (REDUCED || !FINE || TOUCH || !HAS_GSAP) { cur.remove(); return; }
  var dot = cur.querySelector(".c-dot"), ring = cur.querySelector(".c-ring"), label = cur.querySelector(".c-label");
  var xDot = gsap.quickTo(dot, "x", { duration: .08, ease: "power2.out" }), yDot = gsap.quickTo(dot, "y", { duration: .08, ease: "power2.out" });
  var xRing = gsap.quickTo(ring, "x", { duration: .35, ease: "power2.out" }), yRing = gsap.quickTo(ring, "y", { duration: .35, ease: "power2.out" });
  var shown = false, over = null;
  document.addEventListener("mousemove", function (e) {
    if (!shown) { shown = true; cur.classList.add("mp-cursor--on"); }
    xDot(e.clientX); yDot(e.clientY); xRing(e.clientX); yRing(e.clientY);
    var t = e.target.closest ? e.target.closest("[data-cursor]") : null;
    if (t !== over) {
      if (over) { ring.classList.remove("c-ring--big"); label.textContent = ""; }
      over = t;
      if (over) { ring.classList.add("c-ring--big"); label.textContent = over.getAttribute("data-cursor") || "View"; }
    }
  });
  document.addEventListener("mouseleave", function () { cur.classList.remove("mp-cursor--on"); });
})();

// ---- 机制 6：加载页（fonts.ready + load → 0→100 约1.2s → 遮罩上滑 → 首屏揭示） ----
(function initLoader() {
  var loader = document.getElementById("mp-loader");
  if (!loader) { loaderDone = true; return; }
  var count = loader.querySelector(".pl-count");
  function slideOut() {
    loaderDone = true;
    flushReveals();
    loader.classList.add("mp-loader--done");
    var kill = function () { loader.remove(); };
    loader.addEventListener("transitionend", kill, { once: true });
    setTimeout(kill, 1600);
  }
  function finish() {
    if (!HAS_GSAP || REDUCED) { loaderDone = true; flushReveals(); loader.remove(); return; }
    var o = { v: 0 };
    gsap.to(o, { v: 100, duration: 1.2, ease: "power1.inOut", onUpdate: function () { count.textContent = String(Math.round(o.v)); }, onComplete: slideOut });
  }
  var fonts = (document.fonts && document.fonts.ready) ? document.fonts.ready.catch(function () {}) : Promise.resolve();
  var loaded = new Promise(function (res) { if (document.readyState === "complete") res(); else window.addEventListener("load", res); });
  Promise.all([fonts, loaded]).then(finish);
  setTimeout(function () { if (!loaderDone) finish(); }, 5000); // 兜底：远端资源挂起也不锁死界面
})();

// ====== 指令 ======
function iconSvg(key) {
  var shapes = (window.MP_ICONS || {})[key];
  if (!shapes) { console.warn("MP_ICONS: unknown icon", key); return ""; }
  var out = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" class="i-16">';
  for (var i = 0; i < shapes.length; i++) {
    var tag = shapes[i][0], a = shapes[i][1], s = "<" + tag;
    for (var k in a) s += " " + k + '="' + a[k] + '"';
    out += s + " />";
  }
  return out + "</svg>";
}
var dirIc = {
  mounted: function (el, b) { el.innerHTML = iconSvg(b.value); },
  updated: function (el, b) { if (b.value !== b.oldValue) el.innerHTML = iconSvg(b.value); }
};
var dirIndet = {
  mounted: function (el, b) { el.indeterminate = !!b.value; },
  updated: function (el, b) { el.indeterminate = !!b.value; }
};
var dirCount = {
  mounted: function (el, b) {
    if (b.value === null || b.value === undefined || b.value === "") { el._cv = null; el.textContent = "-"; return; }
    var target = Number(b.value);
    if (!Number.isFinite(target)) { el._cv = null; el.textContent = String(b.value); return; }
    MP.motion.countUp(el, 0, target, function (v) { return String(Math.round(v)); });
    el._cv = target;
  },
  updated: function (el, b) {
    if (b.value === null || b.value === undefined || b.value === "") { if (el._cv !== null) { el._cv = null; el.textContent = "-"; } return; }
    var target = Number(b.value);
    if (!Number.isFinite(target)) { el._cv = null; el.textContent = String(b.value); return; }
    var from = Number(el.textContent);
    if (!Number.isFinite(from)) from = 0;
    if (from === target && el._cv === target) return;
    MP.motion.countUp(el, from, target, function (v) { return String(Math.round(v)); });
    el._cv = target;
  }
};

// ====== mp-modal 组件（props title；默认 slot + foot slot；emit close） ======
var mpModal = {
  template: "#tpl-modal",
  props: { title: { type: String, default: "" }, wide: { type: Boolean, default: false } },
  emits: ["close"],
  mounted: function () {
    var self = this;
    Vue.nextTick(function () {
      if (self.$el && self.$el.nodeType === 1) self.$el.classList.add("mp-modal--on");
      MP.stopScroll();
      if (self.$refs.panel) self.$refs.panel.focus();
    });
  },
  unmounted: function () { MP.startScroll(); },
  methods: {
    close: function () {
      var self = this;
      if (HAS_GSAP && !REDUCED && this.$el && this.$el.nodeType === 1) {
        this.$el.classList.remove("mp-modal--on");
        setTimeout(function () { self.$emit("close"); }, 300);
      } else this.$emit("close");
    }
  }
};

// toast 宿主 + confirm 单例宿主
var toastHost = { template: "#tpl-toasts", computed: { list: function () { return mpToasts.list; } } };
MP.confirmHost = {
  computed: {
    c: function () { return mpConfirm; }
  },
  render: function () {
    var c = this.c;
    if (!c.open) return null;
    return Vue.h(mpModal, { title: c.title, onClose: function () { resolveConfirm(false); } }, {
      default: function () { return c.body ? [Vue.h("p", { "class": "hint" }, c.body)] : []; },
      foot: function () {
        return [
          Vue.h("button", { "class": "btn", onClick: function () { resolveConfirm(false); } }, "取消"),
          Vue.h("button", { "class": "btn " + (c.danger ? "btn--danger" : "btn--cta"), onClick: function () { resolveConfirm(true); } }, "确认")
        ];
      }
    });
  }
};

// ====== 页面组件 ======
var pages = {};

var LOGIN_ERR_ZH = {
  user_not_found: "用户名或密码错误",
  wrong_password: "用户名或密码错误",
  admin_only: "该账号不是管理员，无法登录控制台（可在 App 中正常使用）",
  invalid_username: "用户名需3-32位，字母/数字/下划线/中文",
  invalid_password: "密码至少6位",
  unauthorized: "会话已失效，请重新登录"
};

// 行选择 helper（input.ck 原生行选择：表头全选 + indeterminate）
var rowSel = {
  computed: {
    allSel: function () { var self = this; var src = this.selSource(); return src.length > 0 && src.every(function (r) { return self.sel.indexOf(r) >= 0; }); },
    partialSel: function () { return this.sel.length > 0 && !this.allSel; }
  },
  methods: {
    toggleAll: function (ev) { var src = this.selSource(); this.sel = ev.target.checked ? src.slice() : []; }
  }
};

pages.login = {
  template: "#tpl-login",
  props: ["mode"],
  data: function () { return { username: "", password: "", loading: false, error: "", showPwd: false }; },
  computed: {
    isSetup: function () { return this.mode === "setup"; },
    platsA: function () { return Object.keys(PLAT_NAMES).map(function (k) { return PLAT_NAMES[k]; }); },
    platsB: function () { return this.platsA.slice(); }
  },
  methods: {
    submit: async function () {
      this.loading = true; this.error = "";
      try {
        var r;
        if (this.isSetup) r = await api("/api/auth/setup", { method: "POST", json: { username: this.username, password: this.password } });
        else r = await api("/api/auth/login", { method: "POST", json: { username: this.username, password: this.password } });
        window.__auth = { loggedIn: true, needsSetup: false, username: (r.user || {}).username || "", role: (r.user || {}).role || "admin" };
        this.$emit("done");
      } catch (e) { this.error = LOGIN_ERR_ZH[e.message] || e.message; }
      this.loading = false;
    }
  }
};

pages.dashboard = {
  template: "#tpl-dashboard",
  data: function () { return { st: {}, events: [] }; },
  computed: {
    counts: function () { return this.st.counts || {}; },
    disk: function () { return this.st.disk || {}; },
    pct: function () { var d = this.st.disk || {}; if (!d.total) return "-"; return Math.round((d.total - d.free) / d.total * 100) + "%"; },
    pctNum: function () { var d = this.st.disk || {}; if (!d.total) return null; return Math.round((d.total - d.free) / d.total * 100); },
    diskRatio: function () { return this.pctNum === null ? 0 : this.pctNum / 100; },
    importedRatio: function () { var c = this.counts; return c.tracks ? Math.min(1, (c.tracks_imported || 0) / c.tracks) : 0; },
    serviceRows: function () {
      var s = this.st.services || {}, out = [];
      out.push({ name: "musicpipe", ok: true, info: "workers=" + (this.st.workers || 0) });
      out.push({ name: "rclone 挂载", ok: !!(s.rclone && s.rclone.ok), info: s.rclone ? (s.rclone.ok ? "缓存 " + mb(s.rclone.cache_bytes || 0) : s.rclone.error || "") : "" });
      out.push({ name: "WebDAV(123网盘)", ok: !!(s.webdav && s.webdav.configured), info: s.webdav && s.webdav.configured ? "已配置" : "未配置 → 设置页" });
      out.push({ name: "Navidrome", ok: !!(s.navidrome && s.navidrome.ok), info: this.st.nav_configured ? (s.navidrome && s.navidrome.ok ? "在线，可触发扫描" : (s.navidrome && s.navidrome.error) || "") : "未配置账号(仅定时扫描)" });
      out.push({ name: "Music Tag Web", ok: !!(s.musictag && s.musictag.ok), info: s.musictag ? (s.musictag.ok ? "可访问 :8003" : s.musictag.error || "") : "" });
      return out;
    }
  },
  methods: {
    gb: mb, fmt: fmtTs, clip: clip,
    load: async function () {
      try { this.st = await api("/api/system/status"); } catch (e) {}
      try { this.events = (await api("/api/events")).list; } catch (e) {}
    }
  },
  mounted: function () { this.load(); this._t = setInterval(this.load, 8000); },
  unmounted: function () { clearInterval(this._t); }
};

pages.search = {
  template: "#tpl-search",
  mixins: [rowSel],
  data: function () { return { keyword: "", quality: "auto", results: [], page: 1, loading: false, sel: [], tip: "", tab: "all", rowSeq: 0 }; },
  computed: {
    allRows: function () {
      var out = []; var seq = this.rowSeq;
      for (var gi = 0; gi < this.results.length; gi++) {
        var g = this.results[gi]; if (!g.list) continue;
        for (var k = 0; k < g.list.length; k++) { var r = g.list[k]; r.rowid = seq + "-" + g.platform + "-" + k; out.push(r); }
      }
      return out;
    },
    shown: function () { var self = this; var a = this.allRows; if (self.tab === "all") return a; return a.filter(function (r) { return r.source === self.tab; }); }
  },
  methods: {
    selSource: function () { return this.shown; },
    platName: function (p) { return PLAT_NAMES[p] || p; },
    platTag: function (p) { return { kw: "accent", kg: "ok", tx: "warn", wy: "err" }[p] || "info"; },
    fmtTypes: function (row) { return (row.types || []).map(function (t) { return t.type; }).join(" / "); },
    dur: dur,
    doSearch: async function () {
      if (!this.keyword) { this.tip = "请输入关键词"; return; }
      this.loading = true; this.sel = []; this.tip = "正在并发搜索 " + (this.results.length || 4) + " 个平台…";
      try {
        var r = await api("/api/search/all?keyword=" + encodeURIComponent(this.keyword) + "&page=" + this.page);
        this.rowSeq++;
        this.results = r.results || [];
        var okP = this.results.filter(function (x) { return x.ok; });
        var badP = this.results.filter(function (x) { return !x.ok; });
        this.tip = "第" + this.page + "页 · 共 " + this.allRows.length + " 条 · " + okP.length + "/" + this.results.length + " 平台返回" + (badP.length ? "（" + badP.map(function (x) { return x.name; }).join("、") + " 无结果）" : "");
      } catch (e) { this.tip = "搜索失败: " + e.message; this.results = []; }
      this.loading = false;
    },
    dlOne: function (row) { this.download([row]); },
    downloadSelected: function () { this.download(this.sel); },
    download: async function (items) {
      if (!items.length) return;
      try {
        await api("/api/tasks/download", { method: "POST", json: { items: items.map(function (i) { return { source: i.source, id: i.id, name: i.name, singer: i.singer, album: i.album, duration: i.duration, types: i.types, img: i.img, raw: i.raw }; }), quality: this.quality } });
        this.$toast("ok", "已加入下载队列 x" + items.length);
      } catch (e) { this.$toast("err", e.message); }
    }
  }
};

pages.tasks = {
  template: "#tpl-tasks",
  mixins: [rowSel],
  data: function () { return { list: [], sel: [], onlyF: false }; },
  computed: {
    shownList: function () { return this.onlyF ? this.list.filter(function (t) { return t.status === "failed" || t.status === "cancelled"; }) : this.list; }
  },
  methods: {
    selSource: function () { return this.shownList; },
    mb: function (n) { return mb(n); }, clip: function (s) { return clip(s, 60); },
    cancellable: function (s) { return ["pending", "downloading", "uploading", "tagging", "verifying", "organizing"].includes(s); },
    taskDot: function (s) {
      if (s === "failed") return "dot--bad";
      if (s === "success" || s === "done") return "dot--ok";
      if (s === "cancelled") return "dot--warn";
      return "dot--warn";
    },
    load: async function () { try { this.list = (await api("/api/tasks")).list; } catch (e) {} },
    retry: function (id) { api("/api/tasks/" + id + "/retry", { method: "POST" }).then(this.load); },
    cancel: function (id) { api("/api/tasks/" + id + "/cancel", { method: "POST" }).then(this.load); },
    bulkCancel: async function () {
      var ids = this.sel.map(function (x) { return x.id; });
      try { var r = await api("/api/tasks/bulk-cancel", { method: "POST", json: { ids: ids } }); this.$toast("ok", "已取消 " + r.cancelled + " 个任务"); this.load(); } catch (e) { this.$toast("err", e.message); }
    },
    bulkDel: async function () {
      var ids = this.sel.map(function (x) { return x.id; });
      try { var r = await api("/api/tasks/bulk-delete", { method: "POST", json: { ids: ids } }); this.$toast("ok", "已删除 " + r.deleted + " 条记录"); this.load(); } catch (e) { this.$toast("err", e.message); }
    },
    clearFin: function () { api("/api/tasks/finished", { method: "DELETE" }).then(this.load); },
    onWs: function () { this.load(); }
  },
  mounted: function () {
    this.load();
    wsState.listeners.push(this.onWs);
    this._t = setInterval(this.load, 5000);
  },
  unmounted: function () { wsState.listeners = wsState.listeners.filter((f) => f !== this.onWs); clearInterval(this._t); }
};

pages.tracks = {
  template: "#tpl-tracks",
  mixins: [rowSel],
  data: function () { return { list: [], q: "", dlg: false, audiosrc: "", scraping: "", sel: [] }; },
  methods: {
    selSource: function () { return this.list; },
    mb: mb,
    trackTag: function (s) { return { uploaded: "ok", imported: "accent", downloaded: "info" }[s] || "warn"; },
    play: function (id) { this.audiosrc = "/api/preview/" + id; this.dlg = true; },
    bulkDel: async function () {
      var ok = await MP.confirm({ title: "删除选中的 " + this.sel.length + " 首？", body: "网盘文件一并删除", danger: true });
      if (!ok) return;
      var ids = this.sel.map(function (x) { return x.id; });
      try { var r = await api("/api/tracks/bulk-delete", { method: "POST", json: { ids: ids } }); this.$toast("ok", "已删除 " + r.deleted + " 首（含网盘文件）"); if (r.errors && r.errors.length) this.$toast("warn", "部分失败: " + r.errors.join("; ")); this.load(); } catch (e) { this.$toast("err", e.message); }
    },
    scrape: async function (id) {
      this.scraping = id;
      try { await api("/api/tracks/" + id + "/scrape", { method: "POST" }); this.$toast("ok", "刮削任务已提交，约1分钟后标签生效，可在 Navidrome 查看"); }
      catch (e) { this.$toast("err", "刮削失败: " + e.message); }
      this.scraping = "";
    },
    load: async function () { try { this.list = (await api("/api/tracks" + (this.q ? "?q=" + encodeURIComponent(this.q) : ""))).list; this.sel = []; } catch (e) {} },
    del: async function (id) {
      var ok = await MP.confirm({ title: "删除网盘文件+记录？", danger: true });
      if (!ok) return;
      try { await api("/api/tracks/" + id, { method: "DELETE" }); this.load(); } catch (e) { this.$toast("err", e.message); }
    },
    organize: async function (id) { try { await api("/api/tracks/" + id + "/organize", { method: "POST" }); this.$toast("ok", "已整理入库"); this.load(); } catch (e) { this.$toast("err", e.message); } },
    goOrganize: function () { location.hash = "#organize"; }
  },
  mounted: function () { this.load(); }
};

pages.shares = {
  template: "#tpl-shares",
  data: function () { return { list: [], url: "", adding: false, busy: "", pv: false, prev: {}, pvSongs: [], pvSel: [], pvUser: "", pvQuality: "auto", pvLink: "", committing: false }; },
  computed: {
    platsA: function () { return Object.keys(PLAT_NAMES).map(function (k) { return PLAT_NAMES[k]; }); },
    platsB: function () { return this.platsA.slice(); },
    pvSelCount: function () { return this.pvSel.filter(function (x) { return x.state === "preview" && !x.in_lib; }).length; },
    pvAllSel: function () { return this.pvSongs.length > 0 && this.pvSel.length === this.pvSongs.length; },
    pvPartialSel: function () { return this.pvSel.length > 0 && this.pvSel.length < this.pvSongs.length; }
  },
  methods: {
    shareLabel: function (s) { return { active: "同步中", pending: "待确认", paused: "已暂停", expired: "链接失效" }[s] || s; },
    shareTag: function (s) { return { active: "ok", pending: "warn", paused: "info", expired: "err" }[s] || "info"; },
    pvStateTag: function (s) { return s === "done" ? "ok" : "warn"; },
    inPv: function (row) { return this.pvSel.indexOf(row) >= 0; },
    pvToggleRow: function (row) { var i = this.pvSel.indexOf(row); if (i < 0) this.pvSel.push(row); else this.pvSel.splice(i, 1); },
    pvToggleAll: function (ev) { this.pvSel = ev.target.checked ? this.pvSongs.slice() : []; },
    load: async function () {
      try { var r = await api("/api/sharelinks"); this.list = r.list || []; } catch (e) { this.$toast("err", e.message); }
    },
    add: async function () {
      if (!this.url.trim()) { this.$toast("warn", "请粘贴网易云/QQ音乐歌单分享链接"); return; }
      this.adding = true;
      try {
        var r = await api("/api/sharelinks", { method: "POST", json: { url: this.url.trim() } });
        this.url = "";
        await this.openPreview(r);
        this.load();
      } catch (e) { this.$toast("err", "解析失败: " + e.message); }
      this.adding = false;
    },
    openPreview: async function (row) {
      try {
        var r = await api("/api/sharelinks/" + row.id + "/preview");
        this.prev = r;
        this.pvSongs = r.songs || [];
        this.pvUser = (r.link && r.link.share_user) || "";
        this.pvQuality = (r.link && r.link.quality) || "flac";
        this.pvLink = row.id;
        var preselect = (r.songs || []).filter(function (x) { return x.state === "preview" && !x.in_lib; });
        this.pvSel = preselect.length ? preselect : (r.songs || []).filter(function (x) { return x.state === "preview"; });
        this.pv = true;
      } catch (e) { this.$toast("err", e.message); }
    },
    doCommit: async function () {
      this.committing = true;
      try {
        var r = await api("/api/sharelinks/" + this.pvLink + "/commit", { method: "POST", json: { share_user: this.pvUser, quality: this.pvQuality, keys: this.pvSel.map(function (x) { return x.ext_key; }) } });
        this.$toast("ok", "已入队 " + r.queued + " 首下载（限速逐个执行）");
        this.pv = false;
        this.load();
      } catch (e) { this.$toast("err", e.message); }
      this.committing = false;
    },
    sync: async function (row) {
      this.busy = row.id;
      try { var r = await api("/api/sharelinks/" + row.id + "/sync", { method: "POST", json: {} }); this.$toast("ok", r.ok ? ("同步完成：补下 " + r.added + " 首") : ("同步失败：" + r.error)); this.load(); }
      catch (e) { this.$toast("err", e.message); }
      this.busy = "";
    },
    toggle: async function (row) { await api("/api/sharelinks/" + row.id + "/pause", { method: "POST", json: {} }); this.load(); },
    del: async function (row) {
      var ok = await MP.confirm({ title: "退订？", body: "已下载文件保留", danger: true });
      if (!ok) return;
      await api("/api/sharelinks/" + row.id, { method: "DELETE" }); this.$toast("ok", "已退订（已下载文件保留）"); this.load();
    }
  },
  mounted: function () { this.load(); }
};

pages.organize = {
  template: "#tpl-organize",
  data: function () { return { incoming: [], template: "{{artist}}/{{album}}/{{trackNumber}} - {{title}}", auto: true, running: false, scanMsg: "", host: location.hostname }; },
  computed: {
    preview: function () {
      return this.template
        .split("{{artist}}").join("周杰伦")
        .split("{{album}}").join("叶惠美")
        .split("{{trackNumber}}").join("01")
        .split("{{title}}").join("晴天")
        .split("{{year}}").join("2003")
        .split("{{quality}}").join("flac");
    },
    navUrl: function () { return "http://" + this.host + ":4533"; },
    mtUrl: function () { return "http://" + this.host + ":8003"; }
  },
  methods: {
    mb: mb,
    loadIncoming: async function () { try { this.incoming = (await api("/api/organize/incoming")).list; } catch (e) { this.$toast("err", e.message); } },
    loadSettings: async function () {
      try {
        var r = await api("/api/settings");
        if (r.values.naming_template) this.template = r.values.naming_template;
        if (r.values.auto_organize !== undefined) this.auto = r.values.auto_organize === true || r.values.auto_organize === "true";
      } catch (e) {}
    },
    setAuto: function (v) { this.auto = v; this.saveAuto(v); },
    saveAuto: async function (v) { await api("/api/settings", { method: "PUT", json: { auto_organize: v } }); },
    saveTemplate: async function () { await api("/api/settings", { method: "PUT", json: { naming_template: this.template } }); this.$toast("ok", "已保存"); },
    runAll: async function () {
      this.running = true;
      try { var r = await api("/api/organize/run", { method: "POST" }); this.$toast("ok", "整理完成 ok=" + r.ok + " failed=" + r.failed); if (r.errors && r.errors.length) console.warn(r.errors); this.loadIncoming(); }
      catch (e) { this.$toast("err", e.message); }
      this.running = false;
    },
    scan: async function () { try { var r = await api("/api/system/scan", { method: "POST" }); this.scanMsg = r.ok ? "扫描已触发" : ("失败: " + r.error); } catch (e) { this.scanMsg = "失败: " + e.message; } },
    refreshRclone: async function () { try { await api("/api/system/refresh", { method: "POST" }); this.loadIncoming(); } catch (e) {} }
  },
  mounted: function () { this.loadIncoming(); this.loadSettings(); }
};

pages.sources = {
  template: "#tpl-sources",
  data: function () { return { list: [], fileData: null, name: "", up: false, testing: "", testResult: null }; },
  methods: {
    clip: function (s) { return clip(s, 80); },
    fmtPlatforms: function (row) { return (row.platforms || []).join(" "); },
    onFile: function (ev) { var f = ev.target.files[0]; if (f) this.fileData = f; },
    load: async function () { try { this.list = (await api("/api/sources")).list; } catch (e) {} },
    upload: async function () {
      if (!this.fileData) { this.$toast("err", "先选择 .js 源文件"); return; }
      this.up = true;
      try {
        var code = await this.fileData.text();
        await api("/api/sources", { method: "POST", json: { name: this.name || this.fileData.name, code: code } });
        this.$toast("ok", "上传成功");
        this.fileData = null; this.name = ""; this.load();
      } catch (e) { this.$toast("err", e.message); }
      this.up = false;
    },
    toggle: async function (row) { await api("/api/sources/" + row.id + (row.enabled ? "/disable" : "/enable"), { method: "POST" }); this.load(); },
    del: async function (id) {
      var ok = await MP.confirm({ title: "删除该源？", danger: true });
      if (!ok) return;
      await api("/api/sources/" + id, { method: "DELETE" }); this.load();
    },
    test: async function (row) {
      this.testing = row.id; this.testResult = { ok: null, text: "自测中…" };
      try {
        var r = await api("/api/sources/" + row.id + "/test", { method: "POST", json: {} });
        this.testResult = { ok: true, text: (r.steps || []).map(function (s) { return "[" + s.platform + "] 搜索" + s.search + " 取链" + s.url + (s.quality ? "(" + s.quality + ")" : ""); }).join(" ；") };
      }
      catch (e) { this.testResult = { ok: false, text: e.message }; }
      this.testing = "";
      this.load();
    }
  },
  mounted: function () { this.load(); }
};

pages.settings = {
  template: "#tpl-settings",
  data: function () {
    // 界面风格是本机偏好（localStorage mp.style/mp.scheme），与下面的 s（后端 /api/settings）完全分离
    var g = (window.MP && MP.style) ? MP.style.get() : { style: "awwwards", pref: "system" };
    return {
      s: { webdav_url: "", webdav_user: "", webdav_pass: "", webdav_root: "/music", default_quality: "320k", dl_concurrency: 3, dl_speed_limit_kbps: 0, cache_target_gb: 10, nav_user: "", nav_pass: "", mt_user: "admin", mt_pass: "", mt_sources: "netease", mt_media_root: "/app/media" },
      style: g.style,                                // 当前风格 id（awwwards/classic/material/cupertino/shadcn/brutal）
      scheme: g.pref,                                // 明暗偏好 system|dark|light（= localStorage mp.scheme；解析后的 dark|light 由 MP.style 负责）
      styleList: (window.MP && MP.style) ? MP.style.list : [],
      autoScrape: false, mtTesting: false, mtMsg: null, testing: false, davMsg: null, saving: false, saved: false
    };
  },
  methods: {
    // 风格切换：只走 MP.style（写 html 属性 + localStorage），不进 /api/settings，不触发「保存全部设置」
    pickStyle: function (id) {
      if (window.MP && MP.style) MP.style.setStyle(id);
      this.style = id;
    },
    pickScheme: function (pref) {
      if (window.MP && MP.style) MP.style.setScheme(pref);
      this.scheme = pref;
    },
    // mounted / 重新进入页面时从 MP.style 同步（可能被系统明暗变化或在别的页面切过）
    syncStyle: function () {
      if (!window.MP || !MP.style) return;
      var g = MP.style.get();
      this.style = g.style;
      this.scheme = g.pref;
      this.styleList = MP.style.list;
    },
    stepNum: function (key, dir, min, max, step) {
      var v = Number(this.s[key]);
      if (!Number.isFinite(v)) v = min;
      v = v + dir * step;
      this.s[key] = Math.max(min, Math.min(max, v));
    },
    clampNum: function (key, min, max) {
      var v = Number(this.s[key]);
      if (!Number.isFinite(v)) v = min;
      this.s[key] = Math.max(min, Math.min(max, v));
    },
    load: async function () {
      try {
        var r = await api("/api/settings");
        var v = r.values || {};
        for (var k in this.s) if (v[k] !== undefined && v[k] !== "***") this.s[k] = v[k];
        if (v.auto_scrape !== undefined) this.autoScrape = v.auto_scrape === true || v.auto_scrape === "true";
      } catch (e) {}
    },
    save: async function () {
      this.saving = true; this.saved = false;
      try {
        // 只提交后端设置；界面风格 style/scheme 是本机偏好，故意不放进 body（契约 §4）
        var body = Object.assign({}, this.s, { auto_scrape: this.autoScrape });
        ["webdav_pass", "nav_pass", "mt_pass", "mt_token"].forEach(function (k) { if (!body[k]) delete body[k]; });
        await api("/api/settings", { method: "PUT", json: body });
        this.saved = true; var self = this; setTimeout(function () { self.saved = false; }, 2000);
      }
      catch (e) { this.$toast("err", e.message); }
      this.saving = false;
    },
    testMt: async function () {
      this.mtTesting = true; this.mtMsg = { ok: null, text: "测试中…" };
      try { await api("/api/settings", { method: "PUT", json: { mt_user: this.s.mt_user, mt_pass: this.s.mt_pass, musictag_url: this.s.musictag_url || "" } }); var r = await api("/api/musictag/test", { method: "POST" }); this.mtMsg = { ok: true, text: "MTW 可达且登录成功" }; }
      catch (e) { this.mtMsg = { ok: false, text: e.message }; }
      this.mtTesting = false;
    },
    testDav: async function () {
      this.testing = true; this.davMsg = { ok: null, text: "测试中…" };
      try {
        // 先保存再测（后端读 settings）
        await api("/api/settings", { method: "PUT", json: this.s });
        var r = await api("/api/webdav/test", { method: "POST" });
        this.davMsg = { ok: true, text: "列举/写入/删除正常；MOVE支持: " + JSON.stringify(r.detail && r.detail.move) };
      } catch (e) { this.davMsg = { ok: false, text: e.message }; }
      this.testing = false;
    }
  },
  mounted: function () { this.syncStyle(); this.load(); }
};

pages.appusers = {
  template: "#tpl-appusers",
  data: function () { return { list: [], total: 0, page: 1, limit: 20, loading: false, dlg: false, editing: null, saving: false, form: { username: "", password: "", role: "user" } }; },
  computed: {
    pages: function () { return Math.ceil(this.total / this.limit); },
    pageList: function () {
      var n = this.pages, p = this.page, out = [];
      var start = Math.max(1, Math.min(p - 2, n - 6 + 1 > p ? p - 2 : n - 6)), end = Math.min(n, start + 5);
      for (var i = start; i <= end; i++) out.push(i);
      return out;
    }
  },
  methods: {
    fmtTs: fmtTs,
    load: async function (page) {
      if (page) this.page = page;
      this.loading = true;
      try {
        var r = await api("/api/v1/users?page=" + this.page + "&limit=" + this.limit);
        this.list = r.items || []; this.total = r.total || 0;
      } catch (e) { this.$toast("err", e.message); }
      this.loading = false;
    },
    gotoPage: function (p) { this.load(p); },
    openCreate: function () { this.editing = null; this.form = { username: "", password: "", role: "user" }; this.dlg = true; },
    openEdit: function (row) { this.editing = row; this.form = { username: row.username, password: "", role: row.role }; this.dlg = true; },
    submit: async function () {
      this.saving = true;
      try {
        if (this.editing) {
          var body = { role: this.form.role };
          if (this.form.password) body.password = this.form.password;
          await api("/api/v1/users/" + this.editing.id, { method: "PUT", json: body });
          this.$toast("ok", "已更新");
        } else {
          await api("/api/v1/users", { method: "POST", json: { username: this.form.username, password: this.form.password, role: this.form.role } });
          this.$toast("ok", "已创建");
        }
        this.dlg = false; this.load();
      } catch (e) { this.$toast("err", e.message); }
      this.saving = false;
    },
    del: async function (row) {
      var ok = await MP.confirm({ title: "删除该用户？", body: "其会话将同时失效", danger: true });
      if (!ok) return;
      try { await api("/api/v1/users/" + row.id, { method: "DELETE" }); this.$toast("ok", "已删除"); this.load(); }
      catch (e) { this.$toast("err", e.message); }
    }
  },
  mounted: function () { this.load(); }
};

pages.releases = {
  template: "#tpl-releases",
  data: function () { return { list: [], loading: false, dlg: false, uploading: false, picked: null, fileName: "", form: { versionName: "", versionCode: 2, changelog: "" } }; },
  methods: {
    fmtTs: fmtTs, mb: mb,
    clipMd5: function (s) { return clip(s, 32); },
    clipLog: function (s) { return clip(s, 60); },
    stepCode: function (d) { var v = Math.floor(Number(this.form.versionCode)); if (!Number.isFinite(v)) v = 1; this.form.versionCode = Math.max(1, v + d); },
    load: async function () {
      this.loading = true;
      try { var r = await api("/api/v1/app/releases"); this.list = r.items || []; }
      catch (e) { this.$toast("err", e.message); }
      this.loading = false;
      var self = this;
      Vue.nextTick(function () { if (self.$el && self.$el.querySelector && self.$el.querySelector("[data-pin-x]")) { if (self._mpFx) { self._mpFx.forEach(function (f) { try { f.kill(); } catch (e) {} }); self._mpFx = []; } MP.motion.page(self); } });
    },
    openUpload: function () {
      var next = (this.list[0] && this.list[0].versionCode || 0) + 1;
      this.form = { versionName: "", versionCode: next, changelog: "" };
      this.picked = null; this.fileName = ""; this.dlg = true;
    },
    onFile: function (ev) {
      var f = ev.target.files && ev.target.files[0];
      if (!f) return;
      if (!/\.apk$/i.test(f.name)) { this.$toast("err", "只能上传 .apk 文件"); ev.target.value = ""; return; }
      if (f.size > 300 * 1024 * 1024) { this.$toast("err", "文件超过 300MB"); ev.target.value = ""; return; }
      this.picked = f; this.fileName = f.name + " (" + mb(f.size) + ")";
    },
    upload: async function () {
      if (!this.picked) return;
      this.uploading = true;
      try {
        var fd = new FormData();
        fd.append("versionName", String(this.form.versionName || "").trim());
        fd.append("versionCode", String(this.form.versionCode));
        fd.append("changelog", String(this.form.changelog || ""));
        fd.append("file", this.picked, this.picked.name);
        await api("/api/v1/app/releases", { method: "POST", formData: fd });
        this.$toast("ok", "发布成功");
        this.dlg = false; this.load();
      } catch (e) { this.$toast("err", e.message); }
      this.uploading = false;
    },
    del: async function (row) {
      var ok = await MP.confirm({ title: "删除该发布？", body: "APK 文件一并删除", danger: true });
      if (!ok) return;
      try { await api("/api/v1/app/releases/" + row.id, { method: "DELETE" }); this.$toast("ok", "已删除"); this.load(); }
      catch (e) { this.$toast("err", e.message); }
    }
  },
  mounted: function () { this.load(); }
};

// ====== 壳 ======
var shell = {
  template: "#tpl-shell",
  data: function () { return { route: "dashboard", wsOk: false, navOpen: false, who: (window.__auth && window.__auth.username) || "", year: new Date().getFullYear(), menus: [
    { k: "dashboard", label: "概览", icon: "activity" },
    { k: "search", label: "搜索", icon: "search" },
    { k: "tasks", label: "任务", icon: "download" },
    { k: "tracks", label: "音乐库", icon: "music" },
    { k: "organize", label: "整理入库", icon: "folder" },
    { k: "shares", label: "分享歌单", icon: "link" },
    { k: "sources", label: "音源", icon: "sliders-horizontal" },
    { k: "appusers", label: "用户管理", icon: "users" },
    { k: "releases", label: "App发布", icon: "file-archive" },
    { k: "settings", label: "设置", icon: "settings" }
  ] }; },
  computed: {
    navNow: function () {                       // 窄屏报头里的“我在哪”：折叠导航后仍能读出当前页
      var r = this.route;
      for (var i = 0; i < this.menus.length; i++) if (this.menus[i].k === r) return this.menus[i].label;
      return "";
    }
  },
  methods: {
    go: function (k) {
      this.closeNav();
      if (k === this.route) { this.scrollTop(); return; }  // 点当前页也回顶部
      location.hash = "#" + k;
    },
    scrollTop: function () {
      if (MP.lenis && MP.lenis.scrollTo) MP.lenis.scrollTo(0, { immediate: true });
      window.scrollTo(0, 0);
      document.documentElement.scrollTop = 0;
    },
    openNav: function () {
      this.navOpen = true;
      document.documentElement.classList.add("mp-nav-lock");   // 抽屉打开时锁背景滚动
      if (MP.stopScroll) MP.stopScroll();
      var self = this;
      Vue.nextTick(function () {
        var el = document.querySelector(".navmenu__link");
        if (el) el.focus();
      });
      this._onEsc || (this._onEsc = function (e) { if (e.key === "Escape") self.closeNav(); });
      document.addEventListener("keydown", this._onEsc);
    },
    closeNav: function () {
      if (!this.navOpen) return;
      this.navOpen = false;
      document.documentElement.classList.remove("mp-nav-lock");
      if (MP.startScroll) MP.startScroll();
      if (this._onEsc) { document.removeEventListener("keydown", this._onEsc); this._onEsc = null; }
      var t = this.$refs.toggle;
      if (t) t.focus();
    },
    toggleNav: function () { this.navOpen ? this.closeNav() : this.openNav(); },
    applyRoute: function (k) {
      var self = this;
      if (!self.menus.some(function (m) { return m.k === k; })) k = "dashboard"; // 非法/登录态旧 hash 一律回落概览
      if (k === self.route) return;
      MP.motion.wipe(function () {
        self.route = k;
        self.scrollTop();                        // 换页必须回顶：否则新页停在上页中段，被 sticky 报头/表头压住
        Vue.nextTick(function () {
          window.ScrollTrigger && window.ScrollTrigger.refresh();
        });
      }, function () {});
    },
    syncNavH: function () {                      // 把实测报头高写进 token，sticky 表头才压在报头之下
      var nav = this.$refs.nav;
      if (!nav) return;
      var h = Math.round(nav.getBoundingClientRect().height);
      if (h > 0) document.documentElement.style.setProperty("--nav-h-live", h + "px");
    },
    onScroll: function () {
      var nav = this.$refs.nav;
      if (!nav) return;
      var y = lenis ? lenis.scroll : window.scrollY || document.documentElement.scrollTop || 0;
      nav.classList.toggle("nav--scrolled", y > 24);
    },
    logout: async function () { try { await api("/api/auth/logout", { method: "POST" }); } catch (e) {} window.__auth.loggedIn = false; window.__mprefresh(); }
  },
  mounted: function () {
    var h = (location.hash || "#dashboard").slice(1);
    if (!this.menus.some(function (m) { return m.k === h; })) { h = "dashboard"; location.hash = "#dashboard"; }
    this.route = h;
    var self = this;
    this._onHash = function () { self.applyRoute((location.hash || "#dashboard").slice(1)); };
    window.addEventListener("hashchange", this._onHash);
    this._onScrollFn = function () { self.onScroll(); };
    window.addEventListener("scroll", this._onScrollFn, { passive: true });
    this._navMQ = window.matchMedia("(max-width: 1199px)");  // 与 base.css 报头断点保持一致
    this._onResize = function () {
      self.syncNavH();
      if (!self._navMQ.matches) self.closeNav();      // 回到桌面宽度必须收抽屉，避免遮住页面
    };
    window.addEventListener("resize", this._onResize);
    this.syncNavH();
    this.onScroll();
    this._t = setInterval(function () { self.wsOk = wsState.ok; }, 1500);
    Vue.nextTick(function () { if (self.$refs.wipe) self.$refs.wipe.setAttribute("data-mp", "wipe"); });
  },
  unmounted: function () {
    window.removeEventListener("hashchange", this._onHash);
    window.removeEventListener("scroll", this._onScrollFn);
    window.removeEventListener("resize", this._onResize);
    if (this._onEsc) document.removeEventListener("keydown", this._onEsc);
    document.documentElement.classList.remove("mp-nav-lock");
    clearInterval(this._t);
  }
};

// ====== 根 ======
window.__auth = { loggedIn: false, needsSetup: true };
var app = null;
var el = document.getElementById("app");
var root = {
  data: function () { return { view: "loading", mode: "login" }; },
  render: function () {
    var main;
    if (this.view === "shell") main = Vue.h(shell);
    else main = Vue.h(pages.login, { mode: this.mode, onDone: () => { this.view = "shell"; } });
    return [main, Vue.h(MP.confirmHost), Vue.h(toastHost)];
  },
  mounted: async function () {
    try {
      var st = await api("/api/auth/status");
      window.__auth = st;
      this.mode = st.needsSetup ? "setup" : "login";
      this.view = st.loggedIn ? "shell" : "login"; // status 现在返回 username/role，shell 侧栏显示当前账号
    } catch (e) { this.mode = "setup"; this.view = "login"; }
  }
};
window.__mprefresh = function () { if (app) { app.unmount(); mountRoot(); } };
function mountRoot() {
  app = Vue.createApp(root);
  app.config.globalProperties.$toast = function (kind, msg) { MP.toast(kind, msg); };
  app.directive("ic", dirIc);
  app.directive("indet", dirIndet);
  app.directive("count", dirCount);
  app.component("mp-modal", mpModal);
  app.mixin({
    mounted: function () { var tp = String(this.$options.template || ""); if (this !== this.$root && tp.charAt(0) === "#" && tp.indexOf("tpl-") === 1) MP.motion.page(this); },
    unmounted: function () { MP.motion.leave(this); }
  });
  Object.keys(pages).forEach(function (k) { if (k !== "login") app.component("page-" + k, pages[k]); });
  app.mount(el);
}
mountRoot();