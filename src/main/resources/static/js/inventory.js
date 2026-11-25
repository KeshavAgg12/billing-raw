pages.inventory = function (root) {
  const tabs = [["stock", "Stock"], ["article", "New article"], ["purchase", "Add stock"], ["sets", "Sets"]];
  root.innerHTML = `
    <div class="tabs" id="subTabs">
      ${tabs.map(t => `<button data-tab="${t[0]}">${t[1]}</button>`).join("")}
    </div>
    <div id="invBody"></div>`;

  const body = $("#invBody");
  const views = { stock: stockView, article: articleView, purchase: purchaseView, sets: setsView };

  function open(tab) {
    $$("#subTabs button").forEach(b => b.classList.toggle("active", b.dataset.tab === tab));
    clearNotice();
    body.innerHTML = '<p class="hint">Loading…</p>';
    views[tab](body, open).catch(fail);
  }

  $("#subTabs").addEventListener("click", e => {
    const b = e.target.closest("button[data-tab]");
    if (b) open(b.dataset.tab);
  });
  open("stock");
};

async function stockView(body) {
  const articles = await api("/articles");
  if (!articles.length) {
    body.innerHTML = '<div class="panel"><p class="hint">No articles yet. Create the sets first, then add an article.</p></div>';
    return;
  }
  const avs = await Promise.all(articles.map(a => api(`/articles/${a.id}/availability`)));
  const rows = articles.map((a, i) => {
    const av = avs[i];
    const sets = av.sets.length
      ? av.sets.map(s => `${s.available} of set ${esc(s.setName)}`).join("<br>") : "-";
    const loose = av.loose.length
      ? av.loose.map(l => `<span class="chip">size ${l.size}: ${l.quantity}</span>`).join("") : "-";
    return `<tr>
      <td><strong>${esc(a.name)}</strong></td>
      <td>${esc(a.color || "")}</td>
      <td>${esc(a.companyName || "")}</td>
      <td>${esc(a.articleNumber || "")}</td>
      <td class="num">${money(a.cp)}</td>
      <td class="num">${money(a.sp)}</td>
      <td>${sets}</td>
      <td>${loose}</td>
    </tr>`;
  }).join("");
  body.innerHTML = `<div class="panel"><h2>Stock on hand</h2><div class="scroll"><table class="grid">
    <thead><tr><th>Article</th><th>Color</th><th>Company</th><th>Article no.</th>
    <th class="num">Cost price</th><th class="num">Selling price</th><th>Full sets</th><th>Loose pairs</th></tr></thead>
    <tbody>${rows}</tbody></table></div></div>`;
}

async function articleView(body, go) {
  const sets = await api("/set-types");
  if (!sets.length) {
    body.innerHTML = '<div class="panel"><p class="hint">Create at least one set first. Open the Sets tab.</p></div>';
    return;
  }
  body.innerHTML = `<form class="panel" id="articleForm">
    <h2>New article</h2>
    <div class="fields">
      <label>Article name<input name="name" required placeholder="Ruhi-7"></label>
      <label>Article number<input name="articleNumber"></label>
      <label>Company<input name="companyName"></label>
      <label>Color<input name="color"></label>
      <label>Cost price per pair<input name="cp" type="number" min="0.01" step="0.01" required></label>
      <label>Selling price per pair<input name="sp" type="number" min="0.01" step="0.01" required></label>
    </div>
    <fieldset>
      <legend>Sets this article comes in</legend>
      ${sets.map(s => `<label class="check"><input type="checkbox" name="setTypeIds" value="${s.id}">
        <span>Set ${esc(s.name)}</span><span class="muted">sizes ${s.sizes.join(", ")}</span></label>`).join("")}
    </fieldset>
    <div class="actions"><button class="primary" type="submit">Save article</button></div>
  </form>`;

  $("#articleForm").addEventListener("submit", async ev => {
    ev.preventDefault();
    const f = ev.target;
    const setTypeIds = $$('input[name="setTypeIds"]:checked', f).map(i => Number(i.value));
    if (!setTypeIds.length) { notify("Pick at least one set for this article.", "err"); return; }
    try {
      const a = await api("/articles", "POST", {
        name: f.elements.name.value.trim(),
        articleNumber: f.elements.articleNumber.value.trim(),
        companyName: f.elements.companyName.value.trim(),
        color: f.elements.color.value.trim(),
        cp: Number(f.elements.cp.value),
        sp: Number(f.elements.sp.value),
        setTypeIds
      });
      go("stock");
      notify("Saved " + a.name + ". Use Add stock to enter its quantities.");
    } catch (e) { fail(e); }
  });
}

async function purchaseView(body, go) {
  const articles = await api("/articles");
  if (!articles.length) {
    body.innerHTML = '<div class="panel"><p class="hint">Add an article first, then you can add stock to it.</p></div>';
    return;
  }
  body.innerHTML = `<form class="panel" id="purchaseForm">
    <h2>Add stock</h2>
    <div class="fields">
      <label>Article
        <select name="articleId" id="pArticle" required>
          <option value="">Choose an article</option>
          ${articles.map(a => `<option value="${a.id}">${esc(articleLabel(a))}</option>`).join("")}
        </select>
      </label>
      <label>Cost price per pair<input name="cp" type="number" min="0.01" step="0.01" required></label>
      <label>Date<input name="purchaseDate" type="date" value="${today()}"></label>
    </div>
    <div id="sizeInputs"><p class="hint">Choose an article, then type the pairs received for each size.</p></div>
    <div class="actions"><button class="primary" type="submit">Add to stock</button></div>
  </form>`;

  $("#pArticle").addEventListener("change", e => {
    const a = articles.find(x => String(x.id) === e.target.value);
    const box = $("#sizeInputs");
    if (!a) { box.innerHTML = '<p class="hint">Choose an article, then type the pairs received for each size.</p>'; return; }
    $("#purchaseForm").elements.cp.value = a.cp;
    box.innerHTML = a.setTypes.map(s => `<div class="setrow">
      <strong>Set ${esc(s.name)}</strong>
      <div class="run">${s.sizes.map(sz => `<div class="cell"><span class="sz">${sz}</span>
        <input type="number" min="0" step="1" data-size="${sz}" aria-label="Pairs of size ${sz}"></div>`).join("")}</div>
    </div>`).join("");
  });

  $("#purchaseForm").addEventListener("submit", async ev => {
    ev.preventDefault();
    const f = ev.target;
    const items = $$("input[data-size]", f)
      .map(i => ({ size: Number(i.dataset.size), quantity: parseInt(i.value, 10) }))
      .filter(i => i.quantity > 0);
    if (!f.elements.articleId.value) { notify("Choose an article.", "err"); return; }
    if (!items.length) { notify("Type the pairs received for at least one size.", "err"); return; }
    try {
      const res = await api("/purchases", "POST", {
        articleId: Number(f.elements.articleId.value),
        cp: Number(f.elements.cp.value),
        purchaseDate: f.elements.purchaseDate.value || null,
        items
      });
      go("purchase");
      notify("Added " + res.totalPieces + " pairs of " + res.articleName + " to stock. Cost " + money(res.totalCost) + ".");
    } catch (e) { fail(e); }
  });
}

async function setsView(body, go) {
  const sets = await api("/set-types");
  const list = sets.length
    ? `<table class="grid"><thead><tr><th>Set</th><th>Sizes in order</th></tr></thead><tbody>${
        sets.map(s => `<tr><td><strong>${esc(s.name)}</strong></td><td>${s.sizes.join(", ")}</td></tr>`).join("")
      }</tbody></table>`
    : '<p class="hint">No sets yet. Add the first one.</p>';

  body.innerHTML = `<div class="split">
    <section class="panel"><h2>Sets</h2>${list}</section>
    <form class="panel" id="setForm">
      <h2>New set</h2>
      <label>Sizes in order
        <input name="sizes" required placeholder="5, 6, 7, 8   or   11, 12, 13, 1, 2">
      </label>
      <div class="row">
        <span class="muted">Plain range</span>
        <input id="rFrom" type="number" min="1" placeholder="From">
        <input id="rTo" type="number" min="1" placeholder="To">
        <button type="button" id="rFill">Fill sizes</button>
      </div>
      <p class="hint">The set is named from its first and last size, like 5-8 or 11-2. The order you type is kept.</p>
      <div class="actions"><button class="primary" type="submit">Save set</button></div>
    </form>
  </div>`;

  $("#rFill").addEventListener("click", () => {
    const from = parseInt($("#rFrom").value, 10), to = parseInt($("#rTo").value, 10);
    if (!(from > 0) || !(to >= from)) { notify("Type a start size and an end size that is not smaller.", "err"); return; }
    const out = [];
    for (let n = from; n <= to; n++) out.push(n);
    $("#setForm").elements.sizes.value = out.join(", ");
    clearNotice();
  });

  $("#setForm").addEventListener("submit", async ev => {
    ev.preventDefault();
    const sizes = ev.target.elements.sizes.value.split(/[\s,]+/).filter(Boolean).map(Number);
    if (!sizes.length || sizes.some(n => !Number.isInteger(n) || n <= 0)) {
      notify("Sizes must be whole numbers, separated by commas.", "err");
      return;
    }
    try {
      const s = await api("/set-types", "POST", { sizes });
      go("sets");
      notify("Saved set " + s.name + ".");
    } catch (e) { fail(e); }
  });
}
