function invoiceHtml(inv) {
  const rows = inv.items.map(i => {
    const what = i.type === "SET" ? "Set " + esc(i.setName) : "Loose, size " + i.size;
    const unit = i.type === "SET" ? (i.quantity === 1 ? "set" : "sets") : (i.quantity === 1 ? "piece" : "pieces");
    return `<tr>
      <td>${esc(i.articleName)}</td>
      <td>${what}</td>
      <td class="num">${i.quantity} ${unit}</td>
      <td class="num">${i.pieces}</td>
      <td class="num">${money(i.rate)}</td>
      <td class="num">${money(i.lineTotal)}</td>
    </tr>`;
  }).join("");

  const notes = inv.messages && inv.messages.length
    ? `<div class="stocknote no-print"><strong>Not everything you asked for was in stock</strong>
         <ul>${inv.messages.map(m => `<li>${esc(m)}</li>`).join("")}</ul></div>`
    : "";

  return `<article class="invoice">
    <div class="inv-head">
      <div><div class="shop">Shoe Bazaar</div><div class="muted">Wholesale shoes and slippers</div></div>
      <div class="inv-meta"><strong>Invoice ${inv.id}</strong><br>${esc(inv.invoiceDate)}<br>${esc(inv.customerName)}</div>
    </div>
    ${notes}
    <div class="scroll"><table class="grid">
      <thead><tr><th>Article</th><th>Item</th><th class="num">Quantity</th><th class="num">Pairs</th><th class="num">Rate per pair</th><th class="num">Amount</th></tr></thead>
      <tbody>${rows}</tbody>
    </table></div>
    <div class="inv-total"><div class="tag"><small>Total</small>${money(inv.total)}</div></div>
    <div class="actions no-print"><button class="primary" id="printBtn">Print invoice</button></div>
  </article>`;
}

pages.billing = async function (root) {
  const st = {
    articles: [], customers: [], cart: [], avail: null,
    pricing: { bulkThresholdPieces: 32, bulkDiscount: 1, looseExtra: 5 }
  };

  root.innerHTML = `
    <div class="split">
      <section class="panel no-print">
        <h2>Pick an article</h2>
        <label>Article
          <select id="articleSel"><option value="">Choose an article</option></select>
        </label>
        <div id="availBox"><p class="hint">Choose an article to see its sets, loose pieces and rates.</p></div>
      </section>

      <section class="panel no-print">
        <h2>This bill</h2>
        <label>Customer
          <select id="custSel"><option value="">Choose a customer</option></select>
        </label>
        <div class="scroll"><table class="grid">
          <thead><tr><th>Article</th><th>Item</th><th class="num">Qty</th><th class="num">Rate</th><th class="num">Amount</th><th></th></tr></thead>
          <tbody id="cartBody"></tbody>
          <tfoot id="cartFoot"></tfoot>
        </table></div>
        <p class="cartnote" id="cartNote"></p>
        <div class="actions">
          <button class="primary" id="saveInvoice">Save invoice</button>
          <button id="clearCart">Clear bill</button>
        </div>
        <p class="hint">Rates here are an estimate. Stock and rates are checked again when you save.</p>
      </section>
    </div>

    <section id="invoiceView"></section>

    <section class="panel no-print">
      <h2>Open an old invoice</h2>
      <div class="row">
        <input id="findName" placeholder="Customer name (leave empty for the latest invoices)">
        <button id="searchBtn">Find invoices</button>
      </div>
      <div id="searchOut" class="found"></div>
      <div class="row sep">
        <input id="findNo" type="number" min="1" placeholder="Or type an invoice number">
        <button id="findBtn">Open invoice</button>
      </div>
    </section>`;

  try {
    [st.articles, st.customers] = await Promise.all([api("/articles"), api("/customers")]);
  } catch (e) { fail(e); return; }
  try { st.pricing = await api("/pricing"); } catch (e) { /* keep the shop defaults */ }

  $("#articleSel").insertAdjacentHTML("beforeend",
    st.articles.map(a => `<option value="${a.id}">${esc(articleLabel(a))}</option>`).join(""));
  $("#custSel").insertAdjacentHTML("beforeend",
    st.customers.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join(""));

  if (!st.articles.length) {
    $("#availBox").innerHTML = '<p class="hint">There are no articles yet. Add sets and an article in Inventory first.</p>';
  }
  if (!st.customers.length) {
    notify("There are no customers yet. Add one in Customers before saving a bill.", "err");
  }

  // ---- rates (same rules as the server; the server has the final say) ----
  const bulkLimit = () => Number(st.pricing.bulkThresholdPieces);
  const bulkOff = () => Number(st.pricing.bulkDiscount);
  const setRate = (sp, setPairs) => setPairs > bulkLimit() ? sp - bulkOff() : sp;
  const looseRate = sp => sp + Number(st.pricing.looseExtra);
  const cartSetPairs = () => st.cart.reduce((n, c) => c.type === "SET" ? n + c.quantity * c.perSet : n, 0);
  const currentArticle = () => st.avail ? st.articles.find(a => a.id === st.avail.articleId) : null;

  function renderCart() {
    const body = $("#cartBody"), foot = $("#cartFoot"), note = $("#cartNote");
    if (!st.cart.length) {
      body.innerHTML = '<tr><td colspan="6" class="hint">Nothing on the bill yet.</td></tr>';
      foot.innerHTML = "";
      note.textContent = "";
      return;
    }
    const setPairs = cartSetPairs();
    let total = 0;
    body.innerHTML = st.cart.map((c, i) => {
      const pairs = c.type === "SET" ? c.quantity * c.perSet : c.quantity;
      const rate = c.type === "SET" ? setRate(c.sp, setPairs) : looseRate(c.sp);
      const amount = rate * pairs;
      total += amount;
      return `<tr>
        <td>${esc(c.articleName)}</td>
        <td>${esc(c.label)}<br><span class="muted">${pairs} pairs</span></td>
        <td class="num">${c.quantity}</td>
        <td class="num">${money(rate)}</td>
        <td class="num">${money(amount)}</td>
        <td class="num"><button class="link" data-rm="${i}">Remove</button></td>
      </tr>`;
    }).join("");
    foot.innerHTML = `<tr><td colspan="4" class="num"><strong>Estimated total</strong></td>
      <td class="num"><strong>${money(total)}</strong></td><td></td></tr>`;

    if (setPairs > bulkLimit()) {
      note.textContent = `Sets on this bill come to ${setPairs} pairs, more than ${bulkLimit()}, so sets get ${money(bulkOff())} off per pair.`;
    } else if (setPairs > 0) {
      note.textContent = `${setPairs} pairs in sets so far. Sets get ${money(bulkOff())} off per pair once the bill is above ${bulkLimit()} pairs.`;
    } else {
      note.textContent = "";
    }
  }

  function refresh() {
    renderCart();
    if (st.avail) renderAvail();
  }

  function addToCart(item) {
    const same = st.cart.find(c => c.articleId === item.articleId && c.type === item.type
      && (c.setTypeId || null) === (item.setTypeId || null) && (c.size || null) === (item.size || null));
    if (same) same.quantity += item.quantity; else st.cart.push(item);
    refresh();
  }

  function renderAvail() {
    const av = st.avail;
    const art = currentArticle();
    const sp = Number(art.sp);
    const setPairs = cartSetPairs();
    const looseMap = {};
    av.loose.forEach(l => { looseMap[l.size] = l.quantity; });
    let html = "";

    if (!av.sets.length) html += '<p class="hint">This article has no sets.</p>';

    av.sets.forEach(s => {
      const cells = s.sizes.map(sz => {
        const have = (looseMap[sz] || 0) + s.available;
        return `<div class="cell"><span class="sz">${sz}</span><span class="ct ${have ? "" : "zero"}">${have}</span></div>`;
      }).join("");
      const off = s.available ? "" : "disabled";
      html += `<div class="setblock" data-set="${s.setTypeId}" data-name="${esc(s.setName)}" data-per="${s.sizes.length}">
        <div class="sethead">
          <strong>Set ${esc(s.setName)}</strong>
          <span class="${s.available ? "in-stock" : "no-stock"}">${s.available} full ${s.available === 1 ? "set" : "sets"} in stock</span>
        </div>
        <div class="run" title="Pairs in stock for each size">${cells}</div>
        <div class="rateline">
          <span class="rate">${money(setRate(sp, setPairs))} per pair</span>
          <span class="muted">${money(sp - bulkOff())} per pair when sets on the bill are above ${bulkLimit()} pairs</span>
        </div>
        <div class="row">
          <input type="number" min="1" step="1" placeholder="How many sets" ${off}>
          <button data-add="set" ${off}>Add sets</button>
        </div>
        <div class="preview" data-preview></div>
      </div>`;
    });

    if (av.loose.length) {
      html += `<div class="setblock" data-loose="1">
        <div class="sethead"><strong>Loose pieces</strong></div>
        <div class="run">${av.loose.map(l =>
          `<div class="cell"><span class="sz">${l.size}</span><span class="ct">${l.quantity}</span></div>`).join("")}</div>
        <div class="rateline"><span class="rate">${money(looseRate(sp))} per pair</span></div>
        <div class="row">
          <select id="looseSize">${av.loose.map(l =>
            `<option value="${l.size}">Size ${l.size} (${l.quantity} available)</option>`).join("")}</select>
          <input type="number" min="1" step="1" placeholder="Pairs" id="looseQty">
          <button data-add="loose">Add pieces</button>
        </div>
        <div class="preview" data-preview></div>
      </div>`;
    } else {
      html += `<div class="setblock"><div class="sethead"><strong>Loose pieces</strong>
        <span class="no-stock">none left over</span></div></div>`;
    }
    $("#availBox").innerHTML = html;
  }

  // price shown while typing the quantity
  function updatePreview(target) {
    const art = currentArticle();
    const block = target.closest ? target.closest(".setblock") : null;
    if (!art || !block) return;
    const out = $("[data-preview]", block);
    if (!out) return;
    const sp = Number(art.sp);

    if (block.dataset.set) {
      const qty = parseInt($("input", block).value, 10);
      if (!(qty > 0)) { out.textContent = ""; return; }
      const pairs = qty * Number(block.dataset.per);
      const rate = setRate(sp, cartSetPairs() + pairs);
      out.textContent = `${qty} ${qty === 1 ? "set" : "sets"} = ${pairs} pairs at ${money(rate)} = ${money(rate * pairs)}`;
    } else {
      const qty = parseInt($("#looseQty").value, 10);
      if (!(qty > 0)) { out.textContent = ""; return; }
      const rate = looseRate(sp);
      out.textContent = `${qty} ${qty === 1 ? "pair" : "pairs"} at ${money(rate)} = ${money(rate * qty)}`;
    }
  }

  async function loadAvail(id) {
    const box = $("#availBox");
    if (!id) {
      st.avail = null;
      box.innerHTML = '<p class="hint">Choose an article to see its sets, loose pieces and rates.</p>';
      return;
    }
    box.innerHTML = '<p class="hint">Loading stock…</p>';
    try {
      st.avail = await api(`/articles/${id}/availability`);
      renderAvail();
    } catch (e) { fail(e); }
  }

  $("#articleSel").addEventListener("change", e => loadAvail(e.target.value));
  $("#availBox").addEventListener("input", ev => updatePreview(ev.target));
  $("#availBox").addEventListener("change", ev => { if (ev.target.id === "looseSize") updatePreview(ev.target); });

  $("#availBox").addEventListener("click", ev => {
    const btn = ev.target.closest("button[data-add]");
    if (!btn || !st.avail) return;
    const block = btn.closest(".setblock");
    const art = currentArticle();
    const base = { articleId: st.avail.articleId, articleName: st.avail.articleName, sp: Number(art.sp) };

    if (btn.dataset.add === "set") {
      const qty = parseInt($("input", block).value, 10);
      if (!(qty > 0)) { notify("Enter how many sets to add.", "err"); return; }
      addToCart({ ...base, type: "SET", setTypeId: Number(block.dataset.set), perSet: Number(block.dataset.per),
                  label: "Set " + block.dataset.name, quantity: qty });
    } else {
      const size = Number($("#looseSize").value);
      const qty = parseInt($("#looseQty").value, 10);
      if (!(qty > 0)) { notify("Enter how many loose pairs to add.", "err"); return; }
      addToCart({ ...base, type: "LOOSE", size, label: "Loose, size " + size, quantity: qty });
    }
    clearNotice();
  });

  $("#cartBody").addEventListener("click", ev => {
    const b = ev.target.closest("button[data-rm]");
    if (!b) return;
    st.cart.splice(Number(b.dataset.rm), 1);
    refresh();
  });

  $("#clearCart").addEventListener("click", () => { st.cart = []; refresh(); });

  function showInvoice(inv) {
    const view = $("#invoiceView");
    view.innerHTML = invoiceHtml(inv);
    $("#printBtn", view).addEventListener("click", () => window.print());
    view.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  $("#saveInvoice").addEventListener("click", async () => {
    const customerId = Number($("#custSel").value);
    if (!customerId) { notify("Choose a customer for this bill.", "err"); return; }
    if (!st.cart.length) { notify("Add at least one item to the bill.", "err"); return; }
    const btn = $("#saveInvoice");
    btn.disabled = true;
    try {
      const inv = await api("/invoices", "POST", {
        customerId,
        items: st.cart.map(c => ({
          articleId: c.articleId,
          type: c.type,
          setTypeId: c.setTypeId ?? null,
          size: c.size ?? null,
          quantity: c.quantity
        }))
      });
      st.cart = [];
      renderCart();
      showInvoice(inv);
      notify("Invoice " + inv.id + " saved.");
      if ($("#articleSel").value) loadAvail($("#articleSel").value);
    } catch (e) { fail(e); }
    finally { btn.disabled = false; }
  });

  // ---- open an old invoice ----
  async function openInvoice(id) {
    try { showInvoice(await api("/invoices/" + id)); clearNotice(); }
    catch (e) { fail(e); }
  }

  let searchSeq = 0;
  async function searchInvoices() {
    const name = $("#findName").value.trim();
    const out = $("#searchOut");
    const mine = ++searchSeq;
    try {
      const list = await api("/invoices?customer=" + encodeURIComponent(name));
      if (mine !== searchSeq) return;   // a newer search was started, ignore this answer
      if (!list.length) {
        out.innerHTML = '<p class="hint">No invoices found' + (name ? ' for "' + esc(name) + '"' : '') + '.</p>';
        return;
      }
      out.innerHTML = `<div class="scroll"><table class="grid">
        <thead><tr><th>Invoice</th><th>Date</th><th>Customer</th><th class="num">Total</th><th></th></tr></thead>
        <tbody>${list.map(i => `<tr>
          <td>${i.id}</td><td>${esc(i.invoiceDate)}</td><td>${esc(i.customerName)}</td>
          <td class="num">${money(i.total)}</td>
          <td class="num"><button data-open="${i.id}">Open</button></td>
        </tr>`).join("")}</tbody></table></div>`;
    } catch (e) { fail(e); }
  }

  $("#searchBtn").addEventListener("click", searchInvoices);
  $("#findName").addEventListener("keydown", ev => { if (ev.key === "Enter") searchInvoices(); });
  $("#searchOut").addEventListener("click", ev => {
    const b = ev.target.closest("button[data-open]");
    if (b) openInvoice(b.dataset.open);
  });

  $("#findBtn").addEventListener("click", () => {
    const no = parseInt($("#findNo").value, 10);
    if (!(no > 0)) { notify("Type an invoice number.", "err"); return; }
    openInvoice(no);
  });

  renderCart();
};
