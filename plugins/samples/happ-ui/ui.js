FasdeqPlugin.define("happ-ui", function (api) {
  var h = api.h;
  document.body.classList.add("happ-ui");

  var name = h("div", { class: "hp-sub-name" }, "Подписка");
  var meta = h("div", { class: "hp-sub-meta" }, "");
  var bar = h("i", {});
  var card = h("div", { class: "hp-sub" },
    h("div", { class: "hp-sub-row" }, name, meta),
    h("div", { class: "hp-bar" }, bar));
  var home = document.getElementById("view-home");
  home.insertBefore(card, home.querySelector(".stage"));
  api.mergeServers(true);

  function gb(n) { return (Number(n) / 1073741824).toFixed(n >= 1e10 ? 0 : 1) + " ГБ"; }
  function days(sec) { return Math.max(0, Math.ceil((sec * 1000 - Date.now()) / 86400000)); }

  api.onState(function (s) {
    var show = api.get("sub") !== false && s.groups && s.groups.length;
    card.style.display = show ? "" : "none";
    if (!show) return;
    var sel = (s.servers || []).find(function (x) { return x.id === s.selected; });
    var g = (s.groups || []).find(function (x) { return sel && x.name === sel.group; }) || s.groups[0];
    name.textContent = g.name || "Подписка";
    var parts = [];
    if (g.total) parts.push(gb(g.used) + " из " + gb(g.total));
    else if (g.used) parts.push(gb(g.used));
    if (g.expire) parts.push(days(g.expire) + " дн.");
    meta.textContent = parts.join(" · ");
    bar.style.width = g.total ? Math.min(100, (g.used / g.total) * 100).toFixed(1) + "%" : "0%";
  });
});
