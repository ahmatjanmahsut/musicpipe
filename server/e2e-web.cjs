
// Web UI E2E - Edge headless + CDP (Node 24 built-in WebSocket, zero deps)
const { spawn, execSync } = require('child_process');
const fs = require('fs');
const BASE = 'http://127.0.0.1:7800';
const PORT = 9333;
const SHOTS = '/path/to/musicpipe/docs/shots';
const PROFILE = '/path/to/musicpipe/.tmp/edge-e2e';
const APK = '/path/to/musicpipe/.tmp/e2e-ui-888.apk';
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const sleep = ms => new Promise(r => setTimeout(r, ms));
let pass = 0, fail = 0;
const lines = [];
function Check(n, c, x) { if (c) { pass++; lines.push('PASS ' + n + ' ' + (x || '')); } else { fail++; lines.push('FAIL ' + n + ' ' + (x || '')); } }

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  fs.mkdirSync('/path/to/musicpipe/.tmp', { recursive: true });
  const bytes = Buffer.alloc(3000); for (let i = 0; i < 3000; i++) bytes[i] = (i * 7) % 251;
  fs.writeFileSync(APK, bytes);

  const edge = spawn(EDGE, ['--headless=new', '--remote-debugging-port=' + PORT, '--user-data-dir=' + PROFILE, '--no-first-run', '--no-default-browser-check', '--disable-gpu', '--window-size=1366,900', 'about:blank'], { stdio: 'ignore', detached: true });
  edge.unref();

  let target = null;
  for (let i = 0; i < 40; i++) {
    await sleep(500);
    try {
      const list = await (await fetch('http://127.0.0.1:' + PORT + '/json/list')).json();
      target = list.find(t => t.type === 'page');
      if (target) break;
    } catch (e) { /* not up yet */ }
  }
  if (!target) { console.log('FATAL: no CDP page target'); process.exit(1); }

  const ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = () => rej(new Error('ws connect error')); });
  let mid = 0; const pending = new Map(); const pageErrors = [];
  ws.onmessage = ev => {
    const m = JSON.parse(ev.data);
    if (m.id && pending.has(m.id)) { const p = pending.get(m.id); pending.delete(m.id); if (m.error) p.rej(new Error(JSON.stringify(m.error))); else p.res(m.result); }
    else if (m.method === 'Runtime.exceptionThrown') { const d = m.params.exceptionDetails; pageErrors.push(String((d.exception && d.exception.description) || d.text || '').split('\n')[0]); }
  };
  const send = (method, params) => new Promise((res, rej) => { const id = ++mid; pending.set(id, { res: res, rej: rej }); ws.send(JSON.stringify({ id: id, method: method, params: params || {} })); });

  await send('Page.enable'); await send('Runtime.enable'); await send('DOM.enable');
  const ev = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true });
    if (r.exceptionDetails) throw new Error('page-eval: ' + String((r.exceptionDetails.exception && r.exceptionDetails.exception.description) || r.exceptionDetails.text));
    return r.result.value;
  };
  const waitFor = async (expr, ms, tag) => {
    const t0 = Date.now();
    while (Date.now() - t0 < (ms || 15000)) { try { if (await ev(expr)) return true; } catch (e) { /* keep polling */ } await sleep(250); }
    throw new Error('timeout waiting: ' + (tag || expr));
  };
  const shot = async (name) => { const s = await send('Page.captureScreenshot', { format: 'png' }); fs.writeFileSync(SHOTS + '/' + name + '.png', Buffer.from(s.data, 'base64')); };
  const nav = async (url) => {
    await send('Page.navigate', { url: url });
    await waitFor("document.readyState==='complete'", 15000, 'ready');
    await waitFor("document.querySelector('#app') && document.querySelector('#app').childElementCount>0", 15000, 'vue-mount');
    await sleep(500);
  };
  const clickByText = (sel, txt) => ev("(function(){var els=[].slice.call(document.querySelectorAll(" + JSON.stringify(sel) + ")); var t=" + JSON.stringify(txt) + "; var el=els.find(function(e){return e.textContent.replace(/\\s+/g,'').indexOf(t)>=0;}); if(!el) throw new Error('click target not found: '+t); el.click(); return true;})()");
  const noDialog = "[].slice.call(document.querySelectorAll('.el-overlay')).every(function(o){return getComputedStyle(o).display==='none';})";
  const dlgOpen = (title) => "[].slice.call(document.querySelectorAll('.el-overlay')).some(function(o){return getComputedStyle(o).display!=='none' && o.textContent.indexOf(" + JSON.stringify(title) + ")>=0;})";
  const findDlg = (title) => "[].slice.call(document.querySelectorAll('.el-dialog')).find(function(d){return d.textContent.indexOf(" + JSON.stringify(title) + ")>=0;})";

  try {
    // S1 login page
    await nav(BASE + '/login');
    await waitFor("document.querySelector('.el-tabs')", 15000, 'tabs');
    Check('S1-login-render', await ev("document.body.textContent.indexOf('MusicPipe')>=0 && !!document.querySelector('.el-tabs')"));
    await shot('01-login');

    // S2 admin login
    await ev("(function(){var pane=document.querySelectorAll('.el-tab-pane')[0]; var ins=pane.querySelectorAll('input'); ins[0].value='boss'; ins[0].dispatchEvent(new Event('input',{bubbles:true})); ins[1].value='secret1'; ins[1].dispatchEvent(new Event('input',{bubbles:true})); return true;})()");
    await clickByText('.el-tab-pane button', '登录');
    await waitFor("location.pathname==='/users'", 15000, 'admin->/users');
    Check('S2-admin-login-redirect-users', true);

    // S3 menu + table
    const menuTxt = await ev("[].slice.call(document.querySelectorAll('.el-menu-item')).map(function(e){return e.textContent.trim();}).join('|')");
    Check('S3-admin-menu', menuTxt.indexOf('用户管理') >= 0 && menuTxt.indexOf('发布管理') >= 0 && menuTxt.indexOf('仪表盘') >= 0, 'menu=' + menuTxt);
    await waitFor("document.querySelectorAll('.el-table__body tr').length>=2", 15000, 'user rows');
    const usersTxt = await ev("document.querySelector('.el-table__body').textContent");
    Check('S3-users-table', usersTxt.indexOf('boss') >= 0 && usersTxt.indexOf('alice') >= 0);
    await shot('02-admin-users');

    // S5 create user via dialog
    await clickByText('.toolbar button', '新增用户');
    await waitFor(dlgOpen('新增用户'), 10000, 'add dialog');
    await ev("(function(){var dlg=" + findDlg('新增用户') + "; var ins=dlg.querySelectorAll('input'); ins[0].value='webtest1'; ins[0].dispatchEvent(new Event('input',{bubbles:true})); ins[1].value='demo123456'; ins[1].dispatchEvent(new Event('input',{bubbles:true})); return true;})()");
    await ev("(function(){var dlg=" + findDlg('新增用户') + "; [].slice.call(dlg.querySelectorAll('button')).find(function(b){return b.textContent.replace(/\\s+/g,'')==='创建';}).click(); return true;})()");
    await waitFor(noDialog, 10000, 'add dialog closed');
    await waitFor("document.querySelector('.el-table__body').textContent.indexOf('webtest1')>=0", 10000, 'webtest1 row');
    Check('S5-create-user-ui', true);
    await shot('03-user-created');

    // S6 edit role -> admin
    await ev("(function(){var tr=[].slice.call(document.querySelectorAll('.el-table__body tr')).find(function(r){return r.textContent.indexOf('webtest1')>=0;}); [].slice.call(tr.querySelectorAll('button')).find(function(b){return b.textContent.indexOf('编辑')>=0;}).click(); return true;})()");
    await waitFor(dlgOpen('编辑用户'), 10000, 'edit dialog');
    await ev("(function(){var dlg=" + findDlg('编辑用户') + "; dlg.querySelector('.el-select').click(); return true;})()");
    await waitFor("[].slice.call(document.querySelectorAll('.el-select-dropdown__item')).some(function(li){return li.textContent.indexOf('admin')>=0;})", 10000, 'role dropdown');
    await ev("(function(){var li=[].slice.call(document.querySelectorAll('.el-select-dropdown__item')).find(function(l){return l.textContent.indexOf('admin')>=0;}); li.click(); return true;})()");
    await sleep(300);
    await ev("(function(){var dlg=" + findDlg('编辑用户') + "; [].slice.call(dlg.querySelectorAll('button')).find(function(b){return b.textContent.replace(/\\s+/g,'')==='保存';}).click(); return true;})()");
    await waitFor(noDialog, 10000, 'edit closed');
    await waitFor("(function(){var tr=[].slice.call(document.querySelectorAll('.el-table__body tr')).find(function(r){return r.textContent.indexOf('webtest1')>=0;}); return tr && tr.textContent.indexOf('admin')>=0;})()", 10000, 'role=admin in row');
    Check('S6-edit-role-ui', true);

    // S7 deep link /releases (SPA fallback)
    await nav(BASE + '/releases');
    await waitFor("document.body.textContent.indexOf('发布管理')>=0", 15000, 'releases page');
    await waitFor("document.querySelectorAll('.el-table__body tr').length>=2", 15000, 'release rows');
    const relTxt = await ev("document.querySelector('.el-table__body').textContent");
    Check('S7-deeplink-releases', relTxt.indexOf('1.1.0') >= 0, relTxt.slice(0, 60));
    await shot('04-releases');

    // S8 upload release via UI (multipart through el-upload)
    await clickByText('.toolbar button', '上传APK');
    await waitFor(dlgOpen('上传 APK 发布'), 10000, 'upload dialog');
    await ev("(function(){var dlg=" + findDlg('上传 APK 发布') + "; var ins=dlg.querySelectorAll('input'); ins[0].value='8.8.8'; ins[0].dispatchEvent(new Event('input',{bubbles:true})); var num=ins[1]; num.focus(); num.value='888'; num.dispatchEvent(new Event('input',{bubbles:true})); num.dispatchEvent(new Event('change',{bubbles:true})); num.blur(); var ta=dlg.querySelector('textarea'); ta.value='来自浏览器 E2E'; ta.dispatchEvent(new Event('input',{bubbles:true})); return true;})()");
    const doc = await send('DOM.getDocument', { depth: -1, pierce: true });
    const fi = await send('DOM.querySelector', { nodeId: doc.root.nodeId, selector: '.el-overlay input[type=file]' });
    if (!fi.nodeId) throw new Error('file input not found via DOM.querySelector');
    await send('DOM.setFileInputFiles', { files: [APK.replace(/\//g, '\\')], nodeId: fi.nodeId });
    await waitFor("(function(){var dlg=" + findDlg('上传 APK 发布') + "; var b=[].slice.call(dlg.querySelectorAll('button')).find(function(x){return x.textContent.replace(/\\s+/g,'')==='上传';}); return b && !b.disabled;})()", 10000, 'upload button enabled (file picked)');
    await ev("(function(){var dlg=" + findDlg('上传 APK 发布') + "; [].slice.call(dlg.querySelectorAll('button')).find(function(b){return b.textContent.replace(/\\s+/g,'')==='上传';}).click(); return true;})()");
    try { await waitFor(noDialog, 25000, 'upload dialog closed'); } catch (e0) {
      const diag = await ev("(function(){var dlg=" + findDlg('上传 APK 发布') + "; var errs=dlg?[].slice.call(dlg.querySelectorAll('.el-form-item__error')).map(function(x){return x.textContent;}):['no-dlg']; var msgs=[].slice.call(document.querySelectorAll('.el-message')).map(function(x){return x.textContent;}); var ins=dlg?dlg.querySelectorAll('input'):[]; var num=ins[1]?ins[1].value:null; var fl=(dlg&&dlg.querySelector('.el-upload-list'))?dlg.querySelector('.el-upload-list').textContent:'none'; return JSON.stringify({errs:errs,msgs:msgs,numVal:num,fileList:fl});})()").catch(() => 'diag-eval-failed');
      lines.push('S8-DIAG ' + diag);
      throw e0;
    }
    await waitFor("document.querySelector('.el-table__body').textContent.indexOf('8.8.8')>=0", 10000, '8.8.8 row');
    Check('S8-upload-release-ui', true);
    await shot('05-release-uploaded');

    // S9 delete release 8.8.8 (ElMessageBox confirm)
    await ev("(function(){var tr=[].slice.call(document.querySelectorAll('.el-table__body tr')).find(function(r){return r.textContent.indexOf('8.8.8')>=0;}); [].slice.call(tr.querySelectorAll('button')).find(function(b){return b.textContent.indexOf('删除')>=0;}).click(); return true;})()");
    await waitFor("document.querySelector('.el-message-box')", 10000, 'msgbox release');
    await ev("(function(){document.querySelector('.el-message-box__btns .el-button--danger').click(); return true;})()");
    await waitFor("document.querySelector('.el-table__body').textContent.indexOf('8.8.8')<0", 10000, '8.8.8 gone');
    Check('S9-delete-release-ui', true);

    // S10 delete webtest1
    await nav(BASE + '/users');
    await waitFor("document.querySelector('.el-table__body').textContent.indexOf('webtest1')>=0", 15000, 'webtest1 row again');
    await ev("(function(){var tr=[].slice.call(document.querySelectorAll('.el-table__body tr')).find(function(r){return r.textContent.indexOf('webtest1')>=0;}); [].slice.call(tr.querySelectorAll('button')).find(function(b){return b.textContent.indexOf('删除')>=0;}).click(); return true;})()");
    await waitFor("document.querySelector('.el-message-box')", 10000, 'msgbox user');
    await ev("(function(){document.querySelector('.el-message-box__btns .el-button--danger').click(); return true;})()");
    await waitFor("document.querySelector('.el-table__body').textContent.indexOf('webtest1')<0", 10000, 'webtest1 gone');
    Check('S10-delete-user-ui', true);

    // S11 logout
    await clickByText('.el-header button', '退出登录');
    await waitFor("location.pathname==='/login'", 10000, 'back to login');
    Check('S11-logout-ui', true);

    // S12 register normal user -> dashboard, admin menu hidden
    await clickByText('.el-tabs__item', '注册');
    await sleep(400);
    await ev("(function(){var pane=[].slice.call(document.querySelectorAll('.el-tab-pane')).find(function(p){return getComputedStyle(p).display!=='none';}); var ins=pane.querySelectorAll('input'); ins[0].value='uicheck1'; ins[0].dispatchEvent(new Event('input',{bubbles:true})); ins[1].value='demo123456'; ins[1].dispatchEvent(new Event('input',{bubbles:true})); ins[2].value='demo123456'; ins[2].dispatchEvent(new Event('input',{bubbles:true})); return true;})()");
    await clickByText('.el-tab-pane button', '注册');
    await waitFor("location.pathname==='/dashboard'", 15000, 'user->dashboard');
    const menu2 = await ev("[].slice.call(document.querySelectorAll('.el-menu-item')).map(function(e){return e.textContent.trim();}).join('|')");
    Check('S12-user-menu-hidden', menu2.indexOf('用户管理') < 0 && menu2.indexOf('发布管理') < 0 && menu2.indexOf('仪表盘') >= 0, 'menu=' + menu2);
    await shot('06-user-dashboard');

    // S13 normal user deep-link /users -> NotFound, no admin content
    await nav(BASE + '/users');
    await sleep(800);
    const nf = await ev("JSON.stringify({path:location.pathname, hasUserTable: !!document.querySelector('.el-table__body'), main:(document.querySelector('.el-main')||document.body).textContent.replace(/\\s+/g,' ').trim().slice(0,60)})");
    const nfObj = JSON.parse(nf);
    Check('S13-user-route-hidden', !nfObj.hasUserTable, nf);
    await shot('07-user-deeplink-notfound');
  } catch (e) {
    lines.push('FATAL ' + e.message);
    try { await shot('99-fatal'); } catch (e2) { /* ignore */ }
  }

  if (pageErrors.length) lines.push('PAGE-ERRORS(' + pageErrors.length + '): ' + pageErrors.slice(0, 6).join(' ;; '));
  lines.push('RESULT: pass=' + pass + ' fail=' + fail);
  console.log(lines.join('\n'));
  try { ws.close(); } catch (e) { /* ignore */ }
  try { execSync('taskkill /PID ' + edge.pid + ' /T /F', { stdio: 'ignore' }); } catch (e) { /* ignore */ }
  process.exit(0);
})();
