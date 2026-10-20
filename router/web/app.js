var { createApp, ref, reactive, computed, onMounted, onUnmounted, watch } = Vue;

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
function mb(n) { if (!Number.isFinite(n) || n <= 0) return "0"; if (n > 1048576 * 1024) return (n / 1073741824).toFixed(2) + "GB"; if (n > 1048576) return (n / 1048576).toFixed(1) + "MB"; return Math.round(n / 1024) + "KB"; }
function dur(s) { s = Number(s) || 0; return Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0"); }
function clip(s, n) { s = String(s || ""); return s.length > (n || 90) ? s.slice(0, n || 90) + "…" : s; }
var PLAT_NAMES = { kw: "酷我", kg: "酷狗", tx: "QQ", wy: "网易", mg: "咪咕", bd: "百度", rqfy: "热歌", apple: "苹果" };

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
pages.login = {
  template: "#tpl-login",
  props: ["mode"],
  data: function () { return { username: "", password: "", loading: false, error: "" }; },
  computed: { isSetup: function () { return this.mode === "setup"; } },
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
    serviceRows: function () {
      var s = this.st.services || {}, out = [];
      out.push({ name: "musicpipe", ok: true, info: "workers=" + (this.st.workers || 0) });
      out.push({ name: "rclone 挂载", ok: !!(s.rclone && s.rclone.ok), info: s.rclone ? (s.rclone.ok ? "缓存 " + mb(s.rclone.cache_bytes || 0) : s.rclone.error || "") : "" });
      out.push({ name: "WebDAV(123网盘)", ok: !!(s.webdav && s.webdav.configured), info: s.webdav && s.webdav.configured ? "已配置" : "未配置 → 设置页" });
      out.push({ name: "Navidrome", ok: !!(s.navidrome && s.navidrome.ok), info: this.st.nav_configured ? (s.navidrome && s.navidrome.ok ? "在线，可触发扫描" : (s.navidrome && s.navidrome.error) || "") : "未配置账号(仅定时扫描)" });
      out.push({ name: "Music Tag Web", ok: !!(s.musictag && s.musictag.ok), info: s.musictag ? (s.musictag.ok ? "可访问 :8003" : s.musictag.error || "") : "" });
      return out;
    },
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
  data: function () { return { keyword: "", quality: "auto", results: [], page: 1, loading: false, sel: [], tip: "", tab: "all", rowSeq: 0 }; },
  computed: {
    allRows: function () {
      var out = []; var seq = this.rowSeq; var i2 = 0;
      for (var gi = 0; gi < this.results.length; gi++) {
        var g = this.results[gi]; if (!g.list) continue;
        for (var k = 0; k < g.list.length; k++) { var r = g.list[k]; r.rowid = seq + "-" + g.platform + "-" + k; out.push(r); }
      }
      return out;
    },
    shown: function () { var self = this; var a = this.allRows; if (self.tab === "all") return a; return a.filter(function (r) { return r.source === self.tab; }); }
  },
  methods: {
    platName: function (p) { return PLAT_NAMES[p] || p; },
    platTag: function (p) { return { kw: "primary", kg: "success", tx: "warning", wy: "danger" }[p] || "info"; },
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
        ElementPlus.ElMessage.success("已加入下载队列 x" + items.length);
      } catch (e) { ElementPlus.ElMessage.error(e.message); }
    }
  }
};

pages.tasks = {
  template: "#tpl-tasks",
  data: function () { return { list: [], sel: [], onlyF: false }; },
  computed: {
    shownList: function () { return this.onlyF ? this.list.filter(function (t) { return t.status === "failed" || t.status === "cancelled"; }) : this.list; }
  },
  methods: {
    mb: function (n) { return mb(n); }, clip: function (s) { return clip(s, 60); },
    load: async function () { try { this.list = (await api("/api/tasks")).list; } catch (e) {} },
    retry: function (id) { api("/api/tasks/" + id + "/retry", { method: "POST" }).then(this.load); },
    cancel: function (id) { api("/api/tasks/" + id + "/cancel", { method: "POST" }).then(this.load); },
    bulkCancel: async function () {
      var ids = this.sel.map(function (x) { return x.id; });
      try { var r = await api("/api/tasks/bulk-cancel", { method: "POST", json: { ids: ids } }); ElementPlus.ElMessage.success("已取消 " + r.cancelled + " 个任务"); this.load(); } catch (e) { ElementPlus.ElMessage.error(e.message); }
    },
    bulkDel: async function () {
      var ids = this.sel.map(function (x) { return x.id; });
      try { var r = await api("/api/tasks/bulk-delete", { method: "POST", json: { ids: ids } }); ElementPlus.ElMessage.success("已删除 " + r.deleted + " 条记录"); this.load(); } catch (e) { ElementPlus.ElMessage.error(e.message); }
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
  data: function () { return { list: [], q: "", dlg: false, audiosrc: "", scraping: "", sel: [] }; },
  methods: {
    mb: mb,
    play: function (id) { this.audiosrc = "/api/preview/" + id; this.dlg = true; },
    bulkDel: async function () {
      var ids = this.sel.map(function (x) { return x.id; });
      try { var r = await api("/api/tracks/bulk-delete", { method: "POST", json: { ids: ids } }); ElementPlus.ElMessage.success("已删除 " + r.deleted + " 首（含网盘文件）"); if (r.errors && r.errors.length) ElementPlus.ElMessage.warning("部分失败: " + r.errors.join("; ")); this.load(); } catch (e) { ElementPlus.ElMessage.error(e.message); }
    },
    scrape: async function (id) {
      this.scraping = id;
      try { await api("/api/tracks/" + id + "/scrape", { method: "POST" }); ElementPlus.ElMessage.success("刮削任务已提交，约1分钟后标签生效，可在 Navidrome 查看"); }
      catch (e) { ElementPlus.ElMessage.error("刮削失败: " + e.message); }
      this.scraping = "";
    },
    load: async function () { try { this.list = (await api("/api/tracks" + (this.q ? "?q=" + encodeURIComponent(this.q) : ""))).list; } catch (e) {} },
    del: async function (id) { try { await api("/api/tracks/" + id, { method: "DELETE" }); this.load(); } catch (e) { ElementPlus.ElMessage.error(e.message); } },
    organize: async function (id) { try { await api("/api/tracks/" + id + "/organize", { method: "POST" }); ElementPlus.ElMessage.success("已整理入库"); this.load(); } catch (e) { ElementPlus.ElMessage.error(e.message); } },
    goOrganize: function () { location.hash = "#organize"; }
  },
  mounted: function () { this.load(); }
};

pages.shares = {
  template: "#tpl-shares",
  data: function () { return { list: [], url: "", adding: false, busy: "", pv: false, prev: {}, pvSongs: [], pvSel: [], pvUser: "", pvQuality: "auto", pvLink: "", committing: false }; },
  methods: {
    load: async function () {
      try { var r = await api("/api/sharelinks"); this.list = r.list || []; } catch (e) { ElementPlus.ElMessage.error(e.message); }
    },
    add: async function () {
      if (!this.url.trim()) { ElementPlus.ElMessage.warning("请粘贴网易云/QQ音乐歌单分享链接"); return; }
      this.adding = true;
      try {
        var r = await api("/api/sharelinks", { method: "POST", json: { url: this.url.trim() } });
        this.url = "";
        await this.openPreview(r);
        this.load();
      } catch (e) { ElementPlus.ElMessage.error("解析失败: " + e.message); }
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
        var self = this;
        this.$nextTick(function () {
          if (self.$refs.pvtable) {
            self.$refs.pvtable.clearSelection();
            self.pvSongs.forEach(function (row2) { if (self.pvSel.indexOf(row2) >= 0) self.$refs.pvtable.toggleRowSelection(row2, true); });
          }
        });
      } catch (e) { ElementPlus.ElMessage.error(e.message); }
    },
    doCommit: async function () {
      this.committing = true;
      try {
        var r = await api("/api/sharelinks/" + this.pvLink + "/commit", { method: "POST", json: { share_user: this.pvUser, quality: this.pvQuality, keys: this.pvSel.map(function (x) { return x.ext_key; }) } });
        ElementPlus.ElMessage.success("已入队 " + r.queued + " 首下载（限速逐个执行）");
        this.pv = false;
        this.load();
      } catch (e) { ElementPlus.ElMessage.error(e.message); }
      this.committing = false;
    },
    sync: async function (row) {
      this.busy = row.id;
      try { var r = await api("/api/sharelinks/" + row.id + "/sync", { method: "POST", json: {} }); ElementPlus.ElMessage.success(r.ok ? ("同步完成：补下 " + r.added + " 首") : ("同步失败：" + r.error)); this.load(); }
      catch (e) { ElementPlus.ElMessage.error(e.message); }
      this.busy = "";
    },
    toggle: async function (row) { await api("/api/sharelinks/" + row.id + "/pause", { method: "POST", json: {} }); this.load(); },
    del: async function (row) { await api("/api/sharelinks/" + row.id, { method: "DELETE" }); ElementPlus.ElMessage.success("已退订（已下载文件保留）"); this.load(); },
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
    loadIncoming: async function () { try { this.incoming = (await api("/api/organize/incoming")).list; } catch (e) { ElementPlus.ElMessage.error(e.message); } },
    loadSettings: async function () {
      try {
        var r = await api("/api/settings");
        if (r.values.naming_template) this.template = r.values.naming_template;
        if (r.values.auto_organize !== undefined) this.auto = r.values.auto_organize === true || r.values.auto_organize === "true";
      } catch (e) {}
    },
    saveTemplate: async function () { await api("/api/settings", { method: "PUT", json: { naming_template: this.template } }); ElementPlus.ElMessage.success("已保存"); },
    saveAuto: async function (v) { await api("/api/settings", { method: "PUT", json: { auto_organize: v } }); },
    runAll: async function () {
      this.running = true;
      try { var r = await api("/api/organize/run", { method: "POST" }); ElementPlus.ElMessage.success("整理完成 ok=" + r.ok + " failed=" + r.failed); if (r.errors && r.errors.length) console.warn(r.errors); this.loadIncoming(); }
      catch (e) { ElementPlus.ElMessage.error(e.message); }
      this.running = false;
    },
    scan: async function () { try { var r = await api("/api/system/scan", { method: "POST" }); this.scanMsg = r.ok ? "扫描已触发" : ("失败: " + r.error); } catch (e) { this.scanMsg = "失败: " + e.message; } },
    refreshRclone: async function () { try { await api("/api/system/refresh", { method: "POST" }); this.loadIncoming(); } catch (e) {} }
  },
  mounted: function () { this.loadIncoming(); this.loadSettings(); }
};

pages.sources = {
  template: "#tpl-sources",
  data: function () { return { list: [], fileData: null, name: "", up: false, testing: "", testResult: "" }; },
  methods: {
    clip: clip,
    onFile: function (ev) { var f = ev.target.files[0]; if (f) this.fileData = f; },
    load: async function () { try { this.list = (await api("/api/sources")).list; } catch (e) {} },
    upload: async function () {
      if (!this.fileData) { ElementPlus.ElMessage.error("先选择 .js 源文件"); return; }
      this.up = true;
      try {
        var code = await this.fileData.text();
        await api("/api/sources", { method: "POST", json: { name: this.name || this.fileData.name, code: code } });
        ElementPlus.ElMessage.success("上传成功");
        this.fileData = null; this.name = ""; this.load();
      } catch (e) { ElementPlus.ElMessage.error(e.message); }
      this.up = false;
    },
    toggle: async function (row) { await api("/api/sources/" + row.id + (row.enabled ? "/disable" : "/enable"), { method: "POST" }); this.load(); },
    del: async function (id) { await api("/api/sources/" + id, { method: "DELETE" }); this.load(); },
    test: async function (row) {
      this.testing = row.id; this.testResult = "自测中…";
      try {
        var r = await api("/api/sources/" + row.id + "/test", { method: "POST", json: {} });
        this.testResult = "✔ " + (r.steps || []).map(function (s) { return "[" + s.platform + "] 搜索" + s.search + " 取链" + s.url + (s.quality ? "(" + s.quality + ")" : ""); }).join(" ；");
      }
      catch (e) { this.testResult = "✘ " + e.message; }
      this.testing = "";
      this.load();
    }
  },
  mounted: function () { this.load(); }
};

pages.settings = {
  template: "#tpl-settings",
  data: function () { return { s: { webdav_url: "", webdav_user: "", webdav_pass: "", webdav_root: "/music", default_quality: "320k", dl_concurrency: 3, dl_speed_limit_kbps: 0, cache_target_gb: 10, nav_user: "", nav_pass: "", mt_user: "admin", mt_pass: "", mt_sources: "netease", mt_media_root: "/app/media" }, autoScrape: false, mtTesting: false, mtMsg: "", testing: false, davMsg: "", saving: false, saved: false }; },
  methods: {
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
        var body = Object.assign({}, this.s, { auto_scrape: this.autoScrape });
        ["webdav_pass", "nav_pass", "mt_pass", "mt_token"].forEach(function (k) { if (!body[k]) delete body[k]; });
        await api("/api/settings", { method: "PUT", json: body });
        this.saved = true; var self = this; setTimeout(function () { self.saved = false; }, 2000);
      }
      catch (e) { ElementPlus.ElMessage.error(e.message); }
      this.saving = false;
    },
    testMt: async function () {
      this.mtTesting = true; this.mtMsg = "测试中…";
      try { await api("/api/settings", { method: "PUT", json: { mt_user: this.s.mt_user, mt_pass: this.s.mt_pass, musictag_url: this.s.musictag_url || "" } }); var r = await api("/api/musictag/test", { method: "POST" }); this.mtMsg = "✔ MTW 可达且登录成功"; }
      catch (e) { this.mtMsg = "✘ " + e.message; }
      this.mtTesting = false;
    },
    testDav: async function () {
      this.testing = true; this.davMsg = "测试中…";
      try {
        // 先保存再测（后端读 settings）
        await api("/api/settings", { method: "PUT", json: this.s });
        var r = await api("/api/webdav/test", { method: "POST" });
        this.davMsg = "✔ 列举/写入/删除正常；MOVE支持: " + JSON.stringify(r.detail && r.detail.move);
      } catch (e) { this.davMsg = "✘ " + e.message; }
      this.testing = false;
    }
  },
  mounted: function () { this.load(); }
};

pages.appusers = {
  template: "#tpl-appusers",
  data: function () { return { list: [], total: 0, page: 1, limit: 20, loading: false, dlg: false, editing: null, saving: false, form: { username: "", password: "", role: "user" }, fmtTs: fmtTs }; },
  methods: {
    load: async function (page) {
      if (page) this.page = page;
      this.loading = true;
      try {
        var r = await api("/api/v1/users?page=" + this.page + "&limit=" + this.limit);
        this.list = r.items || []; this.total = r.total || 0;
      } catch (e) { ElementPlus.ElMessage.error(e.message); }
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
          ElementPlus.ElMessage.success("已更新");
        } else {
          await api("/api/v1/users", { method: "POST", json: { username: this.form.username, password: this.form.password, role: this.form.role } });
          ElementPlus.ElMessage.success("已创建");
        }
        this.dlg = false; this.load();
      } catch (e) { ElementPlus.ElMessage.error(e.message); }
      this.saving = false;
    },
    del: async function (row) {
      try { await api("/api/v1/users/" + row.id, { method: "DELETE" }); ElementPlus.ElMessage.success("已删除"); this.load(); }
      catch (e) { ElementPlus.ElMessage.error(e.message); }
    }
  },
  mounted: function () { this.load(); }
};

pages.releases = {
  template: "#tpl-releases",
  data: function () { return { list: [], loading: false, dlg: false, uploading: false, picked: null, fileName: "", form: { versionName: "", versionCode: 2, changelog: "" }, fmtTs: fmtTs, mb: mb, clip: clip }; },
  methods: {
    load: async function () {
      this.loading = true;
      try { var r = await api("/api/v1/app/releases"); this.list = r.items || []; }
      catch (e) { ElementPlus.ElMessage.error(e.message); }
      this.loading = false;
    },
    openUpload: function () {
      var next = (this.list[0] && this.list[0].versionCode || 0) + 1;
      this.form = { versionName: "", versionCode: next, changelog: "" };
      this.picked = null; this.fileName = ""; this.dlg = true;
    },
    onFile: function (ev) {
      var f = ev.target.files && ev.target.files[0];
      if (!f) return;
      if (!/\.apk$/i.test(f.name)) { ElementPlus.ElMessage.error("只能上传 .apk 文件"); ev.target.value = ""; return; }
      if (f.size > 300 * 1024 * 1024) { ElementPlus.ElMessage.error("文件超过 300MB"); ev.target.value = ""; return; }
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
        ElementPlus.ElMessage.success("发布成功");
        this.dlg = false; this.load();
      } catch (e) { ElementPlus.ElMessage.error(e.message); }
      this.uploading = false;
    },
    del: async function (row) {
      try { await api("/api/v1/app/releases/" + row.id, { method: "DELETE" }); ElementPlus.ElMessage.success("已删除"); this.load(); }
      catch (e) { ElementPlus.ElMessage.error(e.message); }
    }
  },
  mounted: function () { this.load(); }
};

// ====== 壳 ======
var shell = {
  template: "#tpl-shell",
  data: function () { return { route: "dashboard", wsOk: false, who: (window.__auth && window.__auth.username) || "", menus: [
    { k: "dashboard", label: "概览", icon: "📊" },
    { k: "search", label: "搜索", icon: "🔍" },
    { k: "tasks", label: "任务", icon: "⬇️" },
    { k: "tracks", label: "音乐库", icon: "💿" },
    { k: "organize", label: "整理入库", icon: "🗂️" },
    { k: "shares", label: "分享歌单", icon: "🔗" },
    { k: "sources", label: "音源", icon: "🎛️" },
    { k: "appusers", label: "用户管理", icon: "👤" },
    { k: "releases", label: "App发布", icon: "📦" },
    { k: "settings", label: "设置", icon: "⚙️" },
  ] }; },
  methods: {
    go: function (k) { location.hash = "#" + k; this.route = k; },
    logout: async function () { try { await api("/api/auth/logout", { method: "POST" }); } catch (e) {} window.__auth.loggedIn = false; window.__mprefresh(); }
  },
  mounted: function () {
    var h = (location.hash || "#dashboard").slice(1);
    if (h) this.route = h;
    var self = this;
    window.addEventListener("hashchange", function () { self.route = (location.hash || "#dashboard").slice(1); });
    this._t = setInterval(function () { self.wsOk = wsState.ok; }, 1500);
  },
  unmounted: function () { clearInterval(this._t); }
};

// ====== 根 ======
window.__auth = { loggedIn: false, needsSetup: true };
var app = null;
var el = document.getElementById("app");
var root = {
  data: function () { return { view: "loading", mode: "login" }; },
  render: function () {
    if (this.view === "shell") return Vue.h(shell);
    return Vue.h(pages.login, { mode: this.mode, onDone: () => { this.view = "shell"; } });
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
  app.use(ElementPlus);
  Object.keys(pages).forEach(function (k) { if (k !== "login") app.component("page-" + k, pages[k]); });
  app.mount(el);
}
mountRoot();
