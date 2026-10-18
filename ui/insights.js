"use strict";
(async () => {
  await window.ArkAppReady;
  if (!window.Ark || window.ArkSession.user.role === "STAFF") return;
  const { api, el } = window.Ark,
    $ = (id) => document.getElementById(id),
    currency = new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: "INR",
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }),
    amount = (v) => currency.format(Number(v)),
    today = new Intl.DateTimeFormat("en-CA", {
      timeZone: "Asia/Kolkata",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(new Date());
  let inboxPage = 0,
    posts = [],
    statsVersion = 0;
  $("stats-year").value = today.slice(0, 4);
  $("staff-stats-date").value = today;
  $("overview-insights").hidden = false;
  function cell(row, value, tag = "td") {
    row.append(el(tag, undefined, String(value)));
  }
  function chart(target, months, year, compact = false) {
    const root = $(target);
    root.replaceChildren();
    const legend = el("div", "chart-legend");
    legend.append(
      el("span", "legend-current", String(year)),
      el("span", "legend-previous", String(year - 1)),
    );
    root.append(legend);
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("viewBox", "0 0 1000 265");
    svg.setAttribute("role", "img");
    svg.setAttribute(
      "aria-label",
      `Monthly revenue comparison for ${year} and ${year - 1}. Monthly figures available below.`,
    );
    const max = Math.max(
      1,
      ...months.flatMap((m) => [Number(m.revenue), Number(m.previous_revenue)]),
    );
    function shape(tag, attrs, text) {
      const n = document.createElementNS(svg.namespaceURI, tag);
      for (const [k, v] of Object.entries(attrs)) n.setAttribute(k, String(v));
      if (text !== undefined) n.textContent = text;
      svg.append(n);
      return n;
    }
    for (let i = 0; i <= 4; i++) {
      const y = 210 - i * 45;
      shape("line", { x1: 75, y1: y, x2: 988, y2: y, stroke: "#e3ecef" });
      shape(
        "text",
        {
          x: 65,
          y: y + 4,
          "text-anchor": "end",
          fill: "#6a7d88",
          "font-size": 11,
        },
        new Intl.NumberFormat("en-IN", {
          notation: "compact",
          maximumFractionDigits: 1,
        }).format((max * i) / 4),
      );
    }
    months.forEach((m, i) => {
      const x = 86 + i * 75;
      for (const [key, offset, color] of [
        ["previous_revenue", 0, "#c6d8e3"],
        ["revenue", 25, "#087f8c"],
      ]) {
        const value = Number(m[key]),
          h = (value / max) * 180;
        const bar = shape("rect", {
          x: x + offset,
          y: 210 - h,
          width: 21,
          height: Math.max(value > 0 ? 2 : 0, h),
          rx: 3,
          fill: color,
        });
        const title = document.createElementNS(svg.namespaceURI, "title");
        title.textContent = `${new Date(2000, i, 1).toLocaleString("en-IN", { month: "long" })} ${key === "revenue" ? year : year - 1}: ${amount(value)}`;
        bar.append(title);
      }
      shape(
        "text",
        {
          x: x + 23,
          y: 235,
          "text-anchor": "middle",
          fill: "#6a7d88",
          "font-size": 12,
        },
        new Date(2000, i, 1).toLocaleString("en-IN", { month: "short" }),
      );
    });
    root.append(svg);
    if (months.every((m) => !Number(m.revenue) && !Number(m.previous_revenue)))
      root.append(
        el(
          "p",
          "empty-chart",
          "No recorded sales in either year. Add daily sales to build your revenue history.",
        ),
      );
  }
  function metrics(root, items) {
    $(root).replaceChildren(
      ...items.map(([label, value, hint]) => {
        const card = el("div", "insight-metric");
        card.append(
          el("span", undefined, label),
          el("strong", undefined, value),
          el("small", undefined, hint),
        );
        return card;
      }),
    );
  }
  async function loadStatistics() {
    const version = ++statsVersion;
    if (
      !$("stats-year").reportValidity() ||
      !$("staff-stats-date").reportValidity()
    )
      return;
    $("stats-error").hidden = true;
    try {
      const q = new URLSearchParams({
        year: $("stats-year").value,
        period: $("staff-stats-period").value,
        date: $("staff-stats-date").value,
      });
      const s = await api("/api/statistics?" + q);
      if (version !== statsVersion) return;
      const revenue = s.months.reduce((a, m) => a + Number(m.revenue), 0),
        expenses = s.months.reduce((a, m) => a + Number(m.expenses), 0),
        previous = s.months.reduce((a, m) => a + Number(m.previous_revenue), 0),
        visits = s.months.reduce((a, m) => a + Number(m.visits), 0);
      metrics("stats-metrics", [
        ["Recorded revenue", amount(revenue), `${s.year} calendar year`],
        ["Previous year", amount(previous), `${s.previousYear} calendar year`],
        [
          "Operating result",
          amount(revenue - expenses),
          "Revenue less recorded expenses",
        ],
        ["Client visits", visits, "Recorded sales entries"],
      ]);
      chart("revenue-chart", s.months, s.year);
      $("monthly-statistics").replaceChildren();
      for (const m of s.months) {
        const row = el("tr");
        for (const v of [
          new Date(2000, m.month - 1, 1).toLocaleString("en-IN", {
            month: "long",
          }),
          amount(m.revenue),
          amount(m.previous_revenue),
          amount(m.expenses),
          amount(Number(m.revenue) - Number(m.expenses)),
          m.visits,
        ])
          cell(row, v);
        $("monthly-statistics").append(row);
      }
      $("stats-period-caption").textContent =
        `As recorded · business date ${s.businessDate}`;
      $("staff-stats-range").textContent =
        `${s.from} to ${new Date(Date.parse(s.until + "T00:00:00Z") - 86400000).toISOString().slice(0, 10)}`;
      $("staff-statistics").replaceChildren();
      $("staff-stat-chart").replaceChildren();
      const max = Math.max(1, ...s.staff.map((p) => Number(p.visits)));
      for (const p of s.staff) {
        const row = el("tr");
        for (const v of [
          p.name,
          p.role,
          p.visits,
          p.unique_clients,
          amount(p.revenue),
        ])
          cell(row, v);
        $("staff-statistics").append(row);
        const item = el("div", "staff-chart-row"),
          bar = el("progress");
        bar.max = max;
        bar.value = Number(p.visits);
        bar.setAttribute("aria-label", `${p.name}: ${p.visits} client visits`);
        item.append(
          el("span", undefined, p.name),
          bar,
          el("strong", undefined, String(p.visits)),
        );
        $("staff-stat-chart").append(item);
      }
      if (!s.staff.length)
        $("staff-stat-chart").append(
          el("p", "chart-note", "Add staff to start tracking client visits."),
        );
    } catch (e) {
      $("stats-error").hidden = false;
      $("stats-error").textContent = e.message;
    }
  }
  async function overview() {
    try {
      const year = Number(today.slice(0, 4)),
        s = await api(
          "/api/statistics?" +
            new URLSearchParams({ year, period: "MONTH", date: today }),
        );
      const current = s.months[Number(today.slice(5, 7)) - 1],
        total = s.months.reduce((a, m) => a + Number(m.revenue), 0);
      metrics("overview-financial-metrics", [
        [
          "This month",
          amount(current.revenue),
          `${today.slice(0, 7)} · recorded collections`,
        ],
        [
          "This month’s visits",
          String(current.visits),
          "Based on the daily sales register",
        ],
        ["Year’s recorded revenue", amount(total), `${year} calendar year`],
      ]);
      chart("overview-revenue-chart", s.months, year, true);
    } catch (e) {
      $("overview-insights-error").textContent =
        "Business statistics unavailable: " + e.message;
    }
  }
  async function inbox() {
    try {
      $("inbox-error").hidden = true;
      const entries = await api(
        "/api/website/enquiries?" +
          new URLSearchParams({ page: inboxPage, kind: $("inbox-kind").value }),
      );
      $("enquiry-cards").replaceChildren();
      if (!entries.length)
        $("enquiry-cards").append(
          el("p", "chart-note", "No enquiries on this page."),
        );
      for (const e of entries) {
        const card = el("article", "enquiry-card"),
          heading = el("div", "enquiry-card-heading");
        heading.append(
          el("strong", undefined, e.subject),
          el("span", "status-pill", e.kind),
        );
        card.append(
          heading,
          el("p", "muted", `${e.name} · ${e.email} · ${e.phone}`),
          el("p", "enquiry-message", e.message),
        );
        for (const [k, v] of Object.entries(e.answers))
          card.append(
            el("p", "question-answer", `${k.replace(/([A-Z])/g, " $1")}: ${v}`),
          );
        card.append(
          el(
            "small",
            "muted",
            `${new Date(e.created_at).toLocaleString("en-IN")} · Ref ${e.id} · Email ${e.mail_state} (${e.attempts} attempts)`,
          ),
        );
        const actions = el("div", "enquiry-actions"),
          status = el("select");
        status.setAttribute("aria-label", `Status for ${e.name}`);
        for (const v of ["OPEN", "IN_REVIEW", "RESOLVED"]) {
          const option = el("option", undefined, v.replaceAll("_", " "));
          option.value = v;
          status.append(option);
        }
        status.value = e.status;
        status.addEventListener("change", async () => {
          status.disabled = true;
          try {
            await api("/api/website/enquiries/" + e.id, {
              method: "PUT",
              body: JSON.stringify({ status: status.value }),
            });
          } catch (error) {
            $("inbox-error").hidden = false;
            $("inbox-error").textContent = error.message;
            status.value = e.status;
          } finally {
            status.disabled = false;
          }
        });
        actions.append(status);
        if (e.has_resume) {
          const a = el("a", "button secondary", "Download resume");
          a.href = "/api/website/enquiries/" + e.id + "/resume";
          actions.append(a);
        }
        if (e.mail_state === "FAILED") {
          const b = el("button", "button secondary", "Retry email");
          b.addEventListener("click", async () => {
            b.disabled = true;
            try {
              await api("/api/website/enquiries/" + e.id + "/retry", {
                method: "POST",
                body: "{}",
              });
              await inbox();
            } catch (error) {
              $("inbox-error").hidden = false;
              $("inbox-error").textContent = error.message;
              b.disabled = false;
            }
          });
          actions.append(b);
        }
        card.append(actions);
        $("enquiry-cards").append(card);
      }
      $("inbox-page").textContent = `Page ${inboxPage + 1}`;
      $("inbox-previous").disabled = inboxPage === 0;
      $("inbox-next").disabled = entries.length < 25;
    } catch (e) {
      $("inbox-error").hidden = false;
      $("inbox-error").textContent = e.message;
    }
  }
  async function blogs() {
    posts = await api("/api/website/blogs");
    $("blog-list").replaceChildren();
    for (const p of posts) {
      const b = el(
        "button",
        "blog-edit-button",
        `${p.published ? "Published" : "Draft"} · ${p.title}`,
      );
      b.addEventListener("click", () => {
        const f = $("blog-editor");
        for (const k of ["id", "title", "excerpt", "body"])
          f.elements[k].value = p[k];
        f.elements.published.checked = p.published;
        f.elements.title.focus();
      });
      $("blog-list").append(b);
    }
  }
  async function website() {
    await inbox();
    try {
      const settings = await api("/api/website/settings");
      $("website-settings").elements.recipientEmail.value =
        settings.recipient_email;
      $("website-settings").elements.heroVideoUrl.value =
        settings.hero_video_url;
      await blogs();
    } catch (e) {
      $("website-status").textContent = e.message;
    }
  }
  $("blog-editor").addEventListener("submit", async (e) => {
    e.preventDefault();
    const f = e.currentTarget,
      b = f.querySelector("[type=submit]");
    b.disabled = true;
    try {
      const d = Object.fromEntries(new FormData(f));
      const id = d.id;
      delete d.id;
      d.published = f.elements.published.checked;
      const result = await api("/api/website/blogs" + (id ? "/" + id : ""), {
        method: id ? "PUT" : "POST",
        body: JSON.stringify(d),
      });
      f.elements.id.value = result.id;
      $("blog-status").textContent = "Post saved.";
      await blogs();
    } catch (error) {
      $("blog-status").textContent = error.message;
    } finally {
      b.disabled = false;
    }
  });
  $("new-blog").addEventListener("click", () => {
    $("blog-editor").reset();
    $("blog-editor").elements.id.value = "";
    $("blog-editor").elements.title.focus();
    $("blog-status").textContent = "";
  });
  $("website-settings").addEventListener("submit", async (e) => {
    e.preventDefault();
    const b = e.currentTarget.querySelector("[type=submit]");
    b.disabled = true;
    try {
      await api("/api/website/settings", {
        method: "PUT",
        body: JSON.stringify(Object.fromEntries(new FormData(e.currentTarget))),
      });
      $("website-status").textContent = "Website settings saved.";
    } catch (error) {
      $("website-status").textContent = error.message;
    } finally {
      b.disabled = false;
    }
  });
  $("refresh-statistics").addEventListener("click", loadStatistics);
  for (const id of ["stats-year", "staff-stats-date", "staff-stats-period"])
    $(id).addEventListener("change", loadStatistics);
  $("refresh-inbox").addEventListener("click", inbox);
  $("inbox-kind").addEventListener("change", () => {
    inboxPage = 0;
    inbox();
  });
  $("inbox-previous").addEventListener("click", () => {
    if (inboxPage > 0) {
      inboxPage--;
      inbox();
    }
  });
  $("inbox-next").addEventListener("click", () => {
    inboxPage++;
    inbox();
  });
  const route = () => {
    if (location.hash === "#statistics") loadStatistics();
    if (location.hash === "#website") website();
    if (!location.hash || location.hash === "#overview") overview();
  };
  window.addEventListener("hashchange", route);
  route();
})();
