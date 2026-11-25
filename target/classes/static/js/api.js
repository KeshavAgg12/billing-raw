// Shared helpers for every page.
const pages = {};

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

function esc(v) {
  return String(v ?? "").replace(/[&<>"']/g, c =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

function money(n) {
  return "₹" + Number(n || 0).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function today() {
  const d = new Date();
  const p = n => String(n).padStart(2, "0");
  return d.getFullYear() + "-" + p(d.getMonth() + 1) + "-" + p(d.getDate());
}

function articleLabel(a) {
  return a.name + (a.color ? ", " + a.color : "") + (a.companyName ? " (" + a.companyName + ")" : "");
}

function notify(text, kind = "ok") {
  const n = $("#notice");
  n.textContent = text;
  n.className = "notice " + kind;
  n.hidden = false;
  clearTimeout(notify.timer);
  if (kind === "ok") notify.timer = setTimeout(clearNotice, 6000);
}

function clearNotice() {
  const n = $("#notice");
  n.hidden = true;
  n.textContent = "";
}

function fail(e) {
  notify(e.message || "Something went wrong.", "err");
  window.scrollTo({ top: 0 });
}

async function api(path, method = "GET", body) {
  const opts = { method, headers: {} };
  if (body !== undefined) {
    opts.headers["Content-Type"] = "application/json";
    opts.body = JSON.stringify(body);
  }
  let res;
  try {
    res = await fetch("/api" + path, opts);
  } catch (e) {
    throw new Error("Cannot reach the server. Check that the Shoe Bazaar app is running.");
  }
  const text = await res.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch (e) { data = text; }
  }
  if (!res.ok) {
    let msg = data && typeof data === "object" ? (data.message || "") : "";
    if (!msg) {
      msg = res.status === 400 ? "Some fields are missing or wrong. Check the form and try again."
          : res.status === 404 ? "Not found."
          : "The server could not do that (error " + res.status + ").";
    }
    throw new Error(msg);
  }
  return data;
}
