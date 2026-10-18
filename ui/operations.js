"use strict";
(() => {
  const { api, el, notice } = window.Ark;
  const $ = (id) => document.getElementById(id);
  const state = {
    staff: [],
    attendance: [],
    sales: null,
    settings: null,
    salePage: 0,
    attendanceGeneration: 0,
    salesGeneration: 0,
    requestId: null,
  };
  const money = (value) =>
    new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: "INR",
      maximumFractionDigits: 2,
    }).format(value);
  const labels = {
    PRESENT: "Present",
    ABSENT: "Absent",
    LEAVE: "Leave",
    UPI: "UPI",
    CASH: "Cash",
    CARD: "Card",
  };
  function error(id, message) {
    $(id).textContent = message;
    $(id).hidden = !message;
  }
  function summary(container, items) {
    container.replaceChildren();
    for (const [label, value] of items) {
      const card = el("article", "stat");
      card.append(el("div", "stat-top", label), el("strong", "", value));
      container.append(card);
    }
  }
  async function settings() {
    if (!state.settings) state.settings = await api("/api/operations/settings");
    for (const id of ["attendance-date", "sales-date"]) {
      if (!$(id).value) $(id).value = state.settings.today;
      $(id).max = state.settings.today;
    }
    $("salon-zone").textContent =
      `Business dates and shift times use ${state.settings.zoneId}. Unmarked staff are shown separately from absent staff.`;
    return state.settings;
  }
  async function staff() {
    state.staff = await api("/api/staff");
  }
  function renderAttendance() {
    const marked = new Map(
      state.attendance.map((record) => [record.staffId, record]),
    );
    const counts = { PRESENT: 0, ABSENT: 0, LEAVE: 0 };
    state.attendance.forEach((r) => counts[r.status]++);
    summary($("attendance-stats"), [
      ["Staff members", state.staff.length],
      ["Present", counts.PRESENT],
      ["Absent", counts.ABSENT],
      ["On leave", counts.LEAVE],
      ["Not marked", state.staff.length - marked.size],
    ]);
    const body = $("attendance-body");
    body.replaceChildren();
    for (const person of state.staff) {
      const record = marked.get(person.id);
      const row = el("tr");
      const status = el("td");
      status.append(
        el(
          "span",
          record?.status === "PRESENT" ? "badge" : "badge past",
          record ? labels[record.status] : "Not marked",
        ),
      );
      const hours =
        record?.minutesWorked == null
          ? "—"
          : `${Math.floor(record.minutesWorked / 60)}h ${record.minutesWorked % 60}m`;
      const action = el("td");
      const button = el(
        "button",
        "button secondary",
        record ? "Update" : "Mark",
      );
      button.type = "button";
      button.setAttribute(
        "aria-label",
        `${record ? "Update" : "Mark"} attendance for ${person.name}`,
      );
      button.addEventListener("click", () => openAttendance(person, record));
      action.append(button);
      row.append(
        el("td", "", person.name),
        el("td", "", person.role),
        status,
        el("td", "", record?.checkIn?.slice(0, 5) || "—"),
        el("td", "", record?.checkOut?.slice(0, 5) || "—"),
        el("td", "", hours),
        action,
      );
      body.append(row);
    }
    $("attendance-empty").hidden = state.staff.length > 0;
    $("attendance-empty").textContent =
      "Add your first staff member to start monitoring attendance.";
  }
  async function loadAttendance() {
    $("attendance-body").replaceChildren();
    $("attendance-stats").replaceChildren();
    $("attendance-empty").hidden = false;
    $("attendance-empty").textContent = "Loading attendance…";
    const generation = ++state.attendanceGeneration;
    $("refresh-attendance").disabled = true;
    error("attendance-error", "");
    try {
      await settings();
      if (!$("attendance-date").checkValidity()) {
        $("attendance-date").reportValidity();
        return;
      }
      const [people, records] = await Promise.all([
        api("/api/staff"),
        api(`/api/attendance?date=${$("attendance-date").value}`),
      ]);
      if (generation !== state.attendanceGeneration) return;
      state.staff = people;
      state.attendance = records;
      renderAttendance();
    } catch (e) {
      if (generation === state.attendanceGeneration) {
        error("attendance-error", e.message);
        $("attendance-empty").textContent = "Attendance is temporarily unavailable. Try Refresh.";
      }
    } finally {
      if (generation === state.attendanceGeneration)
        $("refresh-attendance").disabled = false;
    }
  }
  function renderSales() {
    const result = state.sales;
    summary($("sales-stats"), [
      ["Clients attended", result.summary.clients],
      ["Total collections", money(result.summary.total)],
      ["UPI", money(result.summary.upi)],
      ["Cash", money(result.summary.cash)],
      ["Card", money(result.summary.card)],
    ]);
    const body = $("sales-body");
    body.replaceChildren();
    for (const sale of result.entries) {
      const row = el("tr");
      const client = el("td", "", sale.customerName);
      client.append(el("small", "", sale.contactNumber));
      const method = el("td");
      method.append(el("span", "badge", labels[sale.paymentMethod]));
      row.append(
        client,
        el("td", "", sale.treatment),
        el("td", "", sale.staffName),
        el("td", "money", money(sale.amount)),
        method,
      );
      body.append(row);
    }
    $("sales-empty").hidden = result.entries.length > 0;
    $("sales-empty").textContent = "No client entries for this date.";
    $("sales-page-info").textContent =
      `Page ${result.page + 1} · ${result.entries.length} records shown · ${result.summary.clients} visits for this date`;
    $("sales-previous").disabled = state.salePage === 0;
    $("sales-next").disabled = !result.hasNext;
  }
  async function loadSales() {
    $("sales-body").replaceChildren();
    $("sales-stats").replaceChildren();
    $("sales-empty").hidden = false;
    $("sales-empty").textContent = "Loading client entries…";
    const generation = ++state.salesGeneration;
    $("refresh-sales").disabled = true;
    $("sales-next").disabled = true;
    $("sales-previous").disabled = true;
    error("sales-error", "");
    try {
      await settings();
      if (!$("sales-date").checkValidity()) {
        $("sales-date").reportValidity();
        return;
      }
      const [people, result] = await Promise.all([
        api("/api/staff"),
        api(`/api/sales?date=${$("sales-date").value}&page=${state.salePage}`),
      ]);
      if (generation !== state.salesGeneration) return;
      state.staff = people;
      state.sales = result;
      renderSales();
    } catch (e) {
      if (generation === state.salesGeneration) {
        error("sales-error", e.message);
        $("sales-empty").textContent =
          "Client entries are temporarily unavailable. Try Refresh.";
        $("sales-page-info").textContent = "Refresh to load current totals.";
      }
    } finally {
      if (generation === state.salesGeneration)
        $("refresh-sales").disabled = false;
    }
  }
  const busy = new Set();
  document.querySelectorAll("[data-close]").forEach((button) =>
    button.addEventListener("click", () => {
      if (!busy.has(button.dataset.close)) $(button.dataset.close).close();
    }),
  );
  for (const id of ["staff-dialog", "attendance-dialog", "sale-dialog"])
    $(id).addEventListener("cancel", (e) => {
      if (busy.has(id)) e.preventDefault();
    });
  async function submit(form, dialogId, errorId, action) {
    if (busy.has(dialogId)) return;
    busy.add(dialogId);
    error(errorId, "");
    const buttons = [...form.querySelectorAll("button")];
    buttons.forEach((b) => (b.disabled = true));
    try {
      await action();
      $(dialogId).close();
    } catch (e) {
      error(errorId, e.message);
    } finally {
      busy.delete(dialogId);
      buttons.forEach((b) => (b.disabled = false));
    }
  }
  $("new-staff").addEventListener("click", () => {
    $("staff-form").reset();
    error("staff-form-error", "");
    $("staff-dialog").showModal();
  });
  $("staff-form").addEventListener("submit", (e) => {
    e.preventDefault();
    const form = e.currentTarget;
    submit(form, "staff-dialog", "staff-form-error", async () => {
      const fields = Object.fromEntries(new FormData(form));
      fields.name = fields.name.trim();
      fields.role = fields.role.trim();
      if (!fields.name || !fields.role)
        throw new Error("Enter a staff name and role.");
      await api("/api/staff", { method: "POST", body: JSON.stringify(fields) });
      notice(
        "Staff member added. You can now mark attendance and record client visits.",
      );
      await loadAttendance();
    });
  });
  function attendanceTimes() {
    const form = $("attendance-form");
    const present = form.elements.status.value === "PRESENT";
    for (const key of ["checkIn", "checkOut"]) {
      form.elements[key].disabled = !present;
      if (!present) form.elements[key].value = "";
    }
    form.elements.checkIn.required = present;
    if (present && !form.elements.checkIn.value)
      form.elements.checkIn.value = "09:00";
  }
  function openAttendance(person, record) {
    const form = $("attendance-form");
    form.reset();
    form.elements.staffId.value = person.id;
    form.elements.day.value = $("attendance-date").value;
    form.elements.status.value = record?.status || "PRESENT";
    form.elements.checkIn.value = record?.checkIn?.slice(0, 5) || "";
    form.elements.checkOut.value = record?.checkOut?.slice(0, 5) || "";
    attendanceTimes();
    $("attendance-person").textContent =
      `${person.name} · ${$("attendance-date").value}`;
    error("attendance-form-error", "");
    $("attendance-dialog").showModal();
  }
  $("attendance-status").addEventListener("change", attendanceTimes);
  $("attendance-form").addEventListener("submit", (e) => {
    e.preventDefault();
    const form = e.currentTarget;
    submit(form, "attendance-dialog", "attendance-form-error", async () => {
      const fields = Object.fromEntries(new FormData(form));
      fields.checkIn = fields.checkIn || null;
      fields.checkOut = fields.checkOut || null;
      if (fields.checkOut && fields.checkOut <= fields.checkIn)
        throw new Error("Check-out must be after check-in on the same day.");
      await api("/api/attendance", {
        method: "PUT",
        body: JSON.stringify(fields),
      });
      notice("Attendance saved.");
      await loadAttendance();
    });
  });
  $("new-sale").addEventListener("click", async () => {
    try {
      await settings();
      await staff();
      const form = $("sale-form");
      form.reset();
      form.elements.day.value = $("sales-date").value;
      form.elements.day.max = state.settings.today;
      const select = $("sale-staff");
      select.replaceChildren();
      select.append(el("option", "", "Choose a staff member"));
      select.firstChild.value = "";
      for (const person of state.staff) {
        const option = el("option", "", `${person.name} · ${person.role}`);
        option.value = person.id;
        select.append(option);
      }
      $("save-sale").disabled = !state.staff.length;
      $("sale-staff-help").textContent = state.staff.length
        ? "Record the service and payment already received."
        : "Add a staff member on the Staff attendance screen first.";
      state.requestId = crypto.randomUUID();
      error("sale-form-error", "");
      $("sale-dialog").showModal();
    } catch (e) {
      error("sales-error", e.message);
    }
  });
  $("sale-form").addEventListener("submit", (e) => {
    e.preventDefault();
    const form = e.currentTarget;
    submit(form, "sale-dialog", "sale-form-error", async () => {
      const fields = Object.fromEntries(new FormData(form));
      for (const key of ["customerName", "contactNumber", "treatment"])
        fields[key] = fields[key].trim();
      if (!fields.customerName || !fields.treatment)
        throw new Error("Enter the client name and service attended.");
      fields.requestId = state.requestId;
      await api("/api/sales", { method: "POST", body: JSON.stringify(fields) });
      notice("Client visit and payment recorded.");
      $("sales-date").value = fields.day;
      state.salePage = 0;
      await loadSales();
    });
  });
  $("attendance-date").addEventListener("change", loadAttendance);
  $("refresh-attendance").addEventListener("click", loadAttendance);
  $("sales-date").addEventListener("change", () => {
    state.salePage = 0;
    loadSales();
  });
  $("refresh-sales").addEventListener("click", loadSales);
  $("sales-previous").addEventListener("click", () => {
    if (state.salePage > 0) {
      state.salePage--;
      loadSales();
    }
  });
  $("sales-next").addEventListener("click", () => {
    state.salePage++;
    loadSales();
  });
  function activate(view) {
    if (view === "attendance") loadAttendance();
    if (view === "sales") loadSales();
  }
  window.addEventListener("ark:view", (e) => activate(e.detail));
  activate(location.hash.slice(1));
})();
