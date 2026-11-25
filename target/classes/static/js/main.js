function route() {
  const wanted = (location.hash || "#billing").slice(1);
  const name = pages[wanted] ? wanted : "billing";
  $$("#nav a").forEach(a => a.classList.toggle("active", a.dataset.page === name));
  clearNotice();
  const view = $("#view");
  view.innerHTML = "";
  pages[name](view);
}

window.addEventListener("hashchange", route);
route();
