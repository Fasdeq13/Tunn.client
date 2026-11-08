TunnPlugin.define("session-card", function (api) {
  var body = api.h("div", { class: "sc-body" }, "—");
  var card = api.h("div", { class: "svc-card sc-card" }, api.h("div", { class: "svc-head" }, api.h("div", { class: "title" }, "Трафик за сессию")), body);
  api.addHomeCard(card);
  function fmt(n) {
    n = Number(n) || 0;
    var u = ["Б", "КБ", "МБ", "ГБ"], i = 0;
    while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
    return n.toFixed(i ? 1 : 0) + " " + u[i];
  }
  api.onState(function (s) {
    var show = api.get("show");
    card.style.display = show === false ? "none" : "";
    body.textContent = "Получено " + fmt(s.rx) + " · отправлено " + fmt(s.tx);
  });
});
