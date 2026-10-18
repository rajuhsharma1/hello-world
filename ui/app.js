"use strict";
window.ArkAppReady = (async () => {
  const session = await window.ArkReady;
  if (!session) return;
  const $ = (id) => document.getElementById(id);
  const state = {
    appointments: [],
    notifications: [],
    page: 0,
    loading: false,
    bookingError: "",
    notificationError: "",
    generation: 0,
    saving: false,
  };
  const date = new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
  const time = new Intl.DateTimeFormat(undefined, {
    hour: "numeric",
    minute: "2-digit",
  });
  const fullDate = (value) =>
    `${date.format(new Date(value))} · ${time.format(new Date(value))}`;
  const el = (tag, className, text) => {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  };
  async function api(path, options = {}) {
    let response;
    try {
      response = await window.ArkSession.fetch(path, {
        ...options,
        signal: AbortSignal.timeout(15000),
        headers: { "Content-Type": "application/json", ...options.headers },
      });
    } catch (error) {
      throw new Error(
        error.name === "TimeoutError"
          ? "The service took too long to respond. Please try again."
          : "We couldn’t reach the service. Please check your connection and try again.",
      );
    }
    if (!response.ok) {
      if (
        path.startsWith("/api/contracts") ||
        path.startsWith("/api/salaries") ||
        path.startsWith("/api/expenses") ||
        path.startsWith("/api/contacts") ||
        path.startsWith("/api/reports") ||
        path.startsWith("/api/staff") ||
        path.startsWith("/api/attendance") ||
        path.startsWith("/api/sales")
      ) {
        const problem = await response.json().catch(() => ({}));
        throw new Error(
          problem.detail || "Check the entry details and try again.",
        );
      }
      if (response.status === 409)
        throw new Error(
          "This therapist already has an appointment at that time. Please choose another time.",
        );
      if (response.status === 400)
        throw new Error(
          "Please check the guest details and choose a valid future date and time.",
        );
      throw new Error(
        "The service is temporarily unavailable. Please try again shortly.",
      );
    }
    return response.status === 204 ? null : response.json();
  }
  function notice(message) {
    $("notice").textContent = message;
    $("notice").hidden = !message;
  }
  function serviceMessage() {
    const errors = [];
    if (state.bookingError) errors.push(`Appointments: ${state.bookingError}`);
    if (state.notificationError)
      errors.push(`Confirmations: ${state.notificationError}`);
    $("service-message").textContent = errors.join(" ");
    $("service-message").hidden = !errors.length;
  }
  function guestInfo(appointment) {
    const details = el("div", "guest-details");
    details.append(
      el("strong", "", appointment.customerName),
      el("p", "", `${appointment.treatment} · ${appointment.therapist}`),
    );
    return details;
  }
  function renderAppointments() {
    const now = Date.now();
    const upcoming = state.appointments.filter(
      (a) => new Date(a.startsAt).getTime() > now,
    );
    $("booking-count").textContent = state.bookingError
      ? "—"
      : state.appointments.length;
    $("upcoming-count").textContent = state.bookingError
      ? "—"
      : upcoming.length;
    const list = $("upcoming-list");
    list.replaceChildren();
    if (!upcoming.length)
      list.append(
        el(
          "p",
          "empty",
          state.bookingError
            ? "Your schedule is temporarily unavailable."
            : "A little breathing room. No upcoming appointments on this page.",
        ),
      );
    for (const appointment of upcoming.slice(0, 4)) {
      const row = el("div", "appointment-row");
      const when = el("div", "time-block");
      when.append(
        el("strong", "", time.format(new Date(appointment.startsAt))),
        el("small", "", date.format(new Date(appointment.startsAt))),
      );
      const initials = appointment.customerName
        .trim()
        .split(/\s+/)
        .map((p) => p[0])
        .slice(0, 2)
        .join("")
        .toUpperCase();
      row.append(
        when,
        el("div", "avatar", initials),
        guestInfo(appointment),
        el("span", "badge", "Upcoming"),
      );
      list.append(row);
    }
    const term = $("search").value.trim().toLocaleLowerCase();
    const filtered = state.appointments.filter((a) =>
      [a.customerName, a.contactNumber, a.treatment, a.therapist].some((v) =>
        v.toLocaleLowerCase().includes(term),
      ),
    );
    const body = $("appointments-body");
    body.replaceChildren();
    for (const appointment of filtered) {
      const row = el("tr");
      const guest = el("td", "", appointment.customerName);
      guest.append(el("small", "", appointment.contactNumber));
      const when = el("td", "", date.format(new Date(appointment.startsAt)));
      when.append(el("small", "", time.format(new Date(appointment.startsAt))));
      const status = el("td");
      const isFuture = new Date(appointment.startsAt).getTime() > now;
      status.append(
        el(
          "span",
          isFuture ? "badge" : "badge past",
          isFuture ? "Upcoming" : "Past",
        ),
      );
      row.append(
        guest,
        el("td", "", appointment.treatment),
        el("td", "", appointment.therapist),
        when,
        status,
      );
      body.append(row);
    }
    $("appointments-empty").hidden = filtered.length > 0;
    $("appointments-empty").textContent = state.bookingError
      ? "Appointments are temporarily unavailable. Try Refresh."
      : term
        ? "No matching appointments on this page."
        : "No appointments on this page. Make room for your next guest.";
    $("page-info").textContent =
      `Page ${state.page + 1} · Up to 50 records · Local time`;
    $("result-count").textContent =
      `${filtered.length} of ${state.appointments.length} records on this page`;
    $("previous-page").disabled = state.loading || state.page === 0;
    $("next-page").disabled =
      state.loading ||
      Boolean(state.bookingError) ||
      state.appointments.length < 50;
  }
  function confirmationRow(notification) {
    const row = el("div", "confirmation-row");
    const details = el("div", "guest-details");
    details.append(
      el("strong", "", notification.message),
      el(
        "p",
        "",
        `${notification.contactNumber} · Booking ${notification.appointmentId.slice(0, 8)}`,
      ),
    );
    row.append(
      el("div", "confirmation-symbol", "✓"),
      details,
      el("time", "confirmation-time", fullDate(notification.createdAt)),
    );
    row.lastChild.dateTime = notification.createdAt;
    return row;
  }
  function renderNotifications() {
    $("confirmation-count").textContent = state.notificationError
      ? "—"
      : state.notifications.length;
    $("nav-count").textContent = state.notifications.length;
    for (const [id, limit] of [
      ["recent-confirmations", 3],
      ["all-confirmations", 50],
    ]) {
      const container = $(id);
      container.replaceChildren();
      if (!state.notifications.length)
        container.append(
          el(
            "p",
            "empty",
            state.notificationError
              ? "Confirmations are temporarily unavailable."
              : "Nothing to confirm just yet. New bookings will appear here shortly.",
          ),
        );
      state.notifications
        .slice(0, limit)
        .forEach((n) => container.append(confirmationRow(n)));
    }
  }
  async function loadAppointments() {
    const generation = ++state.generation;
    state.loading = true;
    $("refresh-appointments").disabled = true;
    renderAppointments();
    try {
      const appointments = await api(`/api/appointments?page=${state.page}`);
      if (generation !== state.generation) return;
      state.appointments = appointments;
      state.bookingError = "";
    } catch (error) {
      if (generation !== state.generation) return;
      state.bookingError = error.message;
      state.appointments = [];
    } finally {
      if (generation === state.generation) {
        state.loading = false;
        $("refresh-appointments").disabled = false;
        renderAppointments();
        serviceMessage();
      }
    }
  }
  let notificationLoading = false;
  async function loadNotifications() {
    if (notificationLoading) return;
    notificationLoading = true;
    $("refresh-notifications").disabled = true;
    try {
      state.notifications = await api("/api/notifications");
      state.notificationError = "";
      $("last-updated").textContent =
        `Confirmations updated ${time.format(new Date())}`;
    } catch (error) {
      state.notificationError = error.message;
      $("last-updated").textContent = "Confirmation refresh unavailable";
    } finally {
      notificationLoading = false;
      $("refresh-notifications").disabled = false;
      renderNotifications();
      serviceMessage();
    }
  }
  function navigate() {
    const view = location.hash.slice(1);
    const current = [
      "overview",
      "appointments",
      "notifications",
      "attendance",
      "sales",
      "hr",
      "expenses",
      "reports",
      "contacts",
      "logs",
      "users",
      "statistics",
      "website",
    ].includes(view)
      ? view
      : "overview";
    const allowed = {
      OWNER: [
        "overview",
        "appointments",
        "notifications",
        "attendance",
        "sales",
        "hr",
        "expenses",
        "reports",
        "contacts",
        "logs",
        "users",
        "statistics",
        "website",
      ],
      ADMIN: [
        "statistics",
        "website",
        "overview",
        "appointments",
        "notifications",
        "attendance",
        "sales",
        "hr",
        "expenses",
        "reports",
        "contacts",
        "logs",
      ],
      STAFF: ["overview", "appointments", "notifications", "sales", "contacts"],
    }[session.user.role];
    if (!allowed.includes(current)) {
      location.hash = "overview";
      return;
    }
    document
      .querySelectorAll("[data-view]")
      .forEach((a) => (a.hidden = !allowed.includes(a.dataset.view)));
    document.getElementById("sidebar-logs").hidden =
      session.user.role === "STAFF";
    const titles = {
      statistics: [
        "See the story in your numbers.",
        "Monthly revenue, yearly comparisons and staff client visits.",
        "Statistics",
      ],
      website: [
        "Stay connected to your community.",
        "Careers, enquiries, complaints, journal and website settings.",
        "Website & enquiries",
      ],
      logs: [
        "A clear view behind the scenes.",
        "Search application logs and review security activity.",
        "System logs",
      ],
      users: [
        "The right access for every person.",
        "Manage workspace accounts and permissions.",
        "Access control",
      ],
      hr: [
        "Your people, properly documented.",
        "Staff profiles, employment drafts and salary structures.",
        "Staff records",
      ],
      expenses: [
        "Keep track of the everyday costs.",
        "Daily expenses, paid and recorded.",
        "Expenses",
      ],
      reports: [
        "A clearer view of your business.",
        "Sales exports and monthly operating results.",
        "Reports & exports",
      ],
      contacts: [
        "The right person, when you need them.",
        "Emergency and maintenance contacts for The Ark.",
        "Emergency contacts",
      ],
      attendance: [
        "A team that’s ready for the day.",
        "Daily attendance and working hours at The Ark.",
        "Staff attendance",
      ],
      sales: [
        "Every visit. Every payment.",
        "Your daily client register and payment totals.",
        "Daily sales",
      ],
      overview: [
        "A calmer way to manage your day.",
        "Appointments, people, and the moments in between.",
        "Overview",
      ],
      appointments: [
        "A little time, thoughtfully arranged.",
        "Give every guest a place in your day.",
        "Appointments",
      ],
      notifications: [
        "Every booking, acknowledged.",
        "Follow confirmations as your appointments are processed.",
        "Confirmations",
      ],
    };
    for (const key of [
      "overview",
      "appointments",
      "notifications",
      "attendance",
      "sales",
      "hr",
      "expenses",
      "reports",
      "contacts",
      "logs",
      "users",
      "statistics",
      "website",
    ])
      $(`${key}-view`).hidden = key !== current;
    document.querySelectorAll("[data-view]").forEach((a) => {
      if (a.dataset.view === current) a.setAttribute("aria-current", "page");
      else a.removeAttribute("aria-current");
    });
    [
      $("page-title").textContent,
      $("page-description").textContent,
      $("breadcrumb").textContent,
    ] = titles[current];
    window.dispatchEvent(new CustomEvent("ark:view", { detail: current }));
    document.title = `${titles[current][2]} · The Ark Salon & Spa`;
  }
  function openBooking() {
    $("booking-form").reset();
    $("form-error").hidden = true;
    const start = new Date(Date.now() + 3600000);
    start.setMinutes(0, 0, 0);
    if (start <= new Date()) start.setHours(start.getHours() + 1);
    const localValue = (d) =>
      `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}T${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
    const field = $("booking-form").elements.startsAt;
    field.min = localValue(new Date());
    field.value = localValue(start);
    $("booking-dialog").showModal();
  }
  function closeBooking() {
    if (!state.saving) $("booking-dialog").close();
  }
  $("new-booking").addEventListener("click", openBooking);
  $("welcome-booking").addEventListener("click", openBooking);
  $("close-dialog").addEventListener("click", closeBooking);
  $("cancel-booking").addEventListener("click", closeBooking);
  $("booking-dialog").addEventListener("cancel", (event) => {
    if (state.saving) event.preventDefault();
  });
  $("booking-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    if (state.saving) return;
    const fields = Object.fromEntries(new FormData(event.currentTarget));
    for (const key of [
      "customerName",
      "contactNumber",
      "therapist",
      "treatment",
    ])
      fields[key] = fields[key].trim();
    const start = new Date(fields.startsAt);
    $("form-error").hidden = true;
    if (
      !fields.customerName ||
      !fields.therapist ||
      !Number.isFinite(start.getTime()) ||
      start <= new Date()
    ) {
      $("form-error").textContent =
        "Enter the guest and therapist names and choose a future appointment time.";
      $("form-error").hidden = false;
      return;
    }
    fields.startsAt = start.toISOString();
    state.saving = true;
    $("submit-booking").disabled = true;
    $("cancel-booking").disabled = true;
    $("close-dialog").disabled = true;
    $("submit-booking").textContent = "Creating…";
    try {
      await api("/api/appointments", {
        method: "POST",
        body: JSON.stringify(fields),
      });
      $("booking-dialog").close();
      notice("Appointment created. Your confirmation will appear shortly.");
      state.page = 0;
      $("search").value = "";
      await Promise.all([loadAppointments(), loadNotifications()]);
    } catch (error) {
      $("form-error").textContent = error.message;
      $("form-error").hidden = false;
    } finally {
      state.saving = false;
      $("submit-booking").disabled = false;
      $("cancel-booking").disabled = false;
      $("close-dialog").disabled = false;
      $("submit-booking").textContent = "Create appointment";
    }
  });
  $("search").addEventListener("input", renderAppointments);
  $("refresh-appointments").addEventListener("click", loadAppointments);
  $("refresh-notifications").addEventListener("click", loadNotifications);
  $("previous-page").addEventListener("click", () => {
    if (state.page > 0) {
      state.page--;
      loadAppointments();
    }
  });
  $("next-page").addEventListener("click", () => {
    state.page++;
    loadAppointments();
  });
  window.addEventListener("hashchange", navigate);
  $("today").textContent = new Intl.DateTimeFormat(undefined, {
    weekday: "long",
    month: "long",
    day: "numeric",
  })
    .format(new Date())
    .toUpperCase();
  window.Ark = { api, el, notice };
  navigate();
  Promise.all([loadAppointments(), loadNotifications()]);
  setInterval(() => {
    if (!document.hidden) loadNotifications();
  }, 10000);
})();
