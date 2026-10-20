import { isValidSession } from "../auth.js";
import { WebSocketServer } from "ws";

var wss = null;

export function attachWs(server) {
  wss = new WebSocketServer({ server, path: "/ws", verifyClient: function (info, done) {
    try {
      var cookie = info.req.headers.cookie || "";
      var m = /mp_session=([a-f0-9]+)/.exec(cookie);
      var url = new URL(info.req.url, "http://x");
      var tq = url.searchParams.get("token");
      var ok = (m && isValidSession(m[1])) || (tq && isValidSession(tq));
      done(ok ? true : false, 401, "unauthorized");
    } catch (e) { done(false, 400, "bad"); }
  } });
  wss.on("connection", function (ws) {
    ws.isAlive = true;
    ws.on("pong", function () { ws.isAlive = true; });
    ws.send(JSON.stringify({ event: "hello", data: { ts: Date.now() } }));
  });
  setInterval(function () {
    if (!wss) return;
    wss.clients.forEach(function (ws) {
      if (!ws.isAlive) { try { ws.terminate(); } catch (e) {} return; }
      ws.isAlive = false;
      try { ws.ping(); } catch (e) {}
    });
  }, 30000);
}

export function broadcast(event, data) {
  if (!wss) return;
  var msg = JSON.stringify({ event: event, data: data, ts: Date.now() });
  wss.clients.forEach(function (ws) {
    try { if (ws.readyState === 1) ws.send(msg); } catch (e) {}
  });
}
