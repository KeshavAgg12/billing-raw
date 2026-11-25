pages.customers = async function (root) {
  root.innerHTML = `<div class="split">
    <div>
      <form class="panel" id="custForm">
        <h2>New customer</h2>
        <label>Name<input name="name" required></label>
        <label>Phone<input name="phone"></label>
        <label>Address<input name="address"></label>
        <div class="actions"><button class="primary" type="submit">Save customer</button></div>
      </form>
      <form class="panel" id="payForm">
        <h2>Record a payment</h2>
        <label>Customer<select name="customerId" id="payCust"><option value="">Choose a customer</option></select></label>
        <label>Amount<input name="amount" type="number" min="0.01" step="0.01" required></label>
        <label>Note<input name="note" placeholder="Cash, UPI or cheque"></label>
        <div class="actions"><button class="primary" type="submit">Save payment</button></div>
      </form>
    </div>
    <section class="panel">
      <h2>Customers and balances</h2>
      <div id="custTable" class="scroll"><p class="hint">Loading…</p></div>
    </section>
  </div>`;

  async function load() {
    const customers = await api("/customers");
    const balances = await Promise.all(customers.map(c => api(`/customers/${c.id}/balance`)));

    $("#payCust").innerHTML = '<option value="">Choose a customer</option>' +
      customers.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join("");

    if (!customers.length) {
      $("#custTable").innerHTML = '<p class="hint">No customers yet. Add the first one.</p>';
      return;
    }
    $("#custTable").innerHTML = `<table class="grid">
      <thead><tr><th>Name</th><th>Phone</th><th class="num">Billed</th><th class="num">Paid</th><th class="num">Owes</th></tr></thead>
      <tbody>${customers.map((c, i) => {
        const b = balances[i];
        const owes = Number(b.outstanding) > 0;
        return `<tr>
          <td><strong>${esc(c.name)}</strong><br><span class="muted">${esc(c.address || "")}</span></td>
          <td>${esc(c.phone || "")}</td>
          <td class="num">${money(b.totalBilled)}</td>
          <td class="num">${money(b.totalPaid)}</td>
          <td class="num ${owes ? "owes" : ""}">${money(b.outstanding)}</td>
        </tr>`;
      }).join("")}</tbody></table>`;
  }

  $("#custForm").addEventListener("submit", async ev => {
    ev.preventDefault();
    const f = ev.target;
    try {
      const c = await api("/customers", "POST", {
        name: f.elements.name.value.trim(), phone: f.elements.phone.value.trim(), address: f.elements.address.value.trim()
      });
      f.reset();
      await load();
      notify("Saved customer " + c.name + ".");
    } catch (e) { fail(e); }
  });

  $("#payForm").addEventListener("submit", async ev => {
    ev.preventDefault();
    const f = ev.target;
    if (!f.elements.customerId.value) { notify("Choose the customer who paid.", "err"); return; }
    try {
      const p = await api("/payments", "POST", {
        customerId: Number(f.elements.customerId.value), amount: Number(f.elements.amount.value), note: f.elements.note.value.trim()
      });
      f.reset();
      await load();
      notify("Saved payment of " + money(p.amount) + ".");
    } catch (e) { fail(e); }
  });

  try { await load(); } catch (e) { fail(e); }
};
