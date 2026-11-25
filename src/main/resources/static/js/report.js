pages.report = function (root) {
  root.innerHTML = `<section class="panel">
    <h2>Profit report</h2>
    <form id="repForm" class="row">
      <label>From<input name="from" type="date" value="${today()}" required></label>
      <label>To<input name="to" type="date" value="${today()}" required></label>
      <button class="primary" type="submit">Show report</button>
    </form>
    <div id="repOut"></div>
  </section>`;

  async function show() {
    const f = $("#repForm");
    try {
      const r = await api(`/reports/summary?from=${f.elements.from.value}&to=${f.elements.to.value}`);
      $("#repOut").innerHTML = `<table class="ledger">
        <tr><td>Stock bought<small>pairs received times their cost price</small></td><td>${money(r.totalPurchase)}</td></tr>
        <tr><td>Sales<small>total of all invoices</small></td><td>${money(r.totalSales)}</td></tr>
        <tr><td>Cost of what was sold<small>cost price of the pairs on those invoices</small></td><td>${money(r.costOfGoodsSold)}</td></tr>
        <tr class="result"><td>Profit<small>sales minus cost of what was sold</small></td><td>${money(r.profit)}</td></tr>
      </table>`;
    } catch (e) { fail(e); }
  }

  $("#repForm").addEventListener("submit", ev => { ev.preventDefault(); clearNotice(); show(); });
  show();
};
