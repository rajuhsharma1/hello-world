"use strict";
(() => {
  const { api, el, notice } = window.Ark;
  const $ = (id) => document.getElementById(id);
  const money = (value) =>
    new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: "INR",
    }).format(value);
  const state = {
    people: [],
    settings: null,
    expensePage: 0,
    expenseRequest: null,
    formAction: null,
    saving: false,
    generation: 0,
  };
  const error = (id, message) => {
    $(id).textContent = message;
    $(id).hidden = !message;
  };
  async function settings() {
    state.settings = await api("/api/operations/settings");
    return state.settings;
  }
  function cards(target, items) {
    $(target).replaceChildren();
    for (const [name, value] of items) {
      const card = el("article", "stat");
      card.append(el("div", "stat-top", name), el("strong", "", value));
      $(target).append(card);
    }
  }
  const currentStaff = () =>
    state.people.find((p) => p.id === $("hr-staff").value);
  function pdfLinks(path) {
    const box = el("div", "document-links");
    const download = el("a", "button secondary", "Download PDF");
    download.href = path + "?download=true";
    const print = el("a", "button secondary", "Open / print PDF");
    print.href = path;
    print.target = "_blank";
    print.rel = "noopener";
    box.append(download, print);
    return box;
  }
  function row(text, extra, path) {
    const item = el("div", "document-row");
    const detail = el("div");
    detail.append(el("strong", "", text), el("p", "muted", extra));
    item.append(detail, pdfLinks(path));
    return item;
  }
  async function loadHr() {
    const generation = ++state.generation;
    error("hr-error", "");
    try {
      state.people = await api("/api/staff");
      if (generation !== state.generation) return;
      const selected = $("hr-staff").value;
      $("hr-staff").replaceChildren();
      for (const person of state.people) {
        const option = el("option", "", `${person.name} · ${person.role}`);
        option.value = person.id;
        $("hr-staff").append(option);
      }
      if (state.people.some((p) => p.id === selected))
        $("hr-staff").value = selected;
      await staffDocuments();
    } catch (e) {
      error("hr-error", e.message);
    }
  }
  async function staffDocuments() {
    const person = currentStaff();
    for (const id of ["edit-staff", "new-contract", "new-salary"])
      $(id).disabled = !person;
    if (!person) {
      $("hr-person").textContent =
        "Add a staff member from Staff attendance first.";
      $("contract-list").replaceChildren();
      $("salary-list").replaceChildren();
      return;
    }
    const selected = person.id;
    $("hr-person").textContent = [
      person.name,
      person.role,
      person.contactNumber || "No phone recorded",
      person.address || "No address recorded",
      person.joinedOn ? "Joined " + person.joinedOn : "",
    ]
      .filter(Boolean)
      .join(" · ");
    try {
      const [contracts, salaries] = await Promise.all([
        api(`/api/contracts?staffId=${selected}`),
        api(`/api/salaries?staffId=${selected}`),
      ]);
      if (selected !== $("hr-staff").value) return;
      $("contract-list").replaceChildren();
      $("salary-list").replaceChildren();
      if (!contracts.length)
        $("contract-list").append(
          el("p", "empty", "No contract drafts saved for this employee."),
        );
      for (const c of contracts)
        $("contract-list").append(
          row(
            "Punjab, India · Draft contract",
            `${c.staff_name} · ${c.staff_role} · Version ${c.id.slice(0, 8)}`,
            `/api/contracts/${c.id}/pdf`,
          ),
        );
      if (!salaries.length)
        $("salary-list").append(
          el("p", "empty", "No salary structure recorded for this employee."),
        );
      for (const s of salaries)
        $("salary-list").append(
          row(
            `Annual CTC ${money(s.annual_ctc)}`,
            `${s.staff_name} · Effective ${s.effective_from} · Monthly gross ${money(Number(s.monthly_basic) + Number(s.monthly_hra) + Number(s.monthly_allowances))}`,
            `/api/salaries/${s.id}/pdf`,
          ),
        );
    } catch (e) {
      error("hr-error", e.message);
    }
  }
  const field = (name, label, type = "text", options = {}) => ({
    name,
    label,
    type,
    ...options,
  });
  const amount = (name, label) =>
    field(name, label, "number", {
      min: "0",
      max: "9999999999.99",
      step: "0.01",
      value: "0",
    });
  function openForm(title, fields, initial, help, action) {
    $("admin-form").reset();
    $("admin-fields").replaceChildren();
    $("admin-title").textContent = title;
    $("admin-help").textContent = help;
    error("admin-error", "");
    $("salary-preview").hidden = true;
    state.formAction = action;
    for (const f of fields) {
      const wrap = el("div", f.type === "textarea" ? "full-width" : "");
      const label = el("label", "", f.label);
      label.htmlFor = "admin-" + f.name;
      let input;
      if (f.type === "select") {
        input = el("select");
        for (const value of f.options) {
          const option = el("option", "", value);
          option.value = value;
          input.append(option);
        }
      } else input = el(f.type === "textarea" ? "textarea" : "input");
      if (input.tagName === "INPUT") input.type = f.type;
      input.id = label.htmlFor;
      input.name = f.name;
      input.required = f.required !== false;
      if (["text", "textarea", "tel"].includes(f.type))
        input.maxLength = f.maxLength || 100;
      for (const attr of ["min", "max", "step", "pattern"])
        if (f[attr] != null) input.setAttribute(attr, f[attr]);
      input.value =
        initial?.[f.name] ??
        f.value ??
        (f.type === "select" ? f.options[0] : "");
      wrap.append(label, input);
      $("admin-fields").append(wrap);
    }
    $("admin-dialog").showModal();
    $("admin-fields").querySelector("input,select,textarea")?.focus();
  }
  function close() {
    if (!state.saving) $("admin-dialog").close();
  }
  $("admin-close").addEventListener("click", close);
  $("admin-cancel").addEventListener("click", close);
  $("admin-dialog").addEventListener("cancel", (e) => {
    if (state.saving) e.preventDefault();
  });
  $("admin-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    if (state.saving) return;
    state.saving = true;
    error("admin-error", "");
    for (const id of ["admin-close", "admin-cancel", "admin-save"])
      $(id).disabled = true;
    try {
      const data = Object.fromEntries(new FormData(e.currentTarget));
      for (const key in data) data[key] = data[key].trim();
      await state.formAction(data);
      $("admin-dialog").close();
      notice("Record saved.");
    } catch (e) {
      error("admin-error", e.message);
    } finally {
      state.saving = false;
      for (const id of ["admin-close", "admin-cancel", "admin-save"])
        $(id).disabled = false;
    }
  });
  const phone = (name, label, required = true) =>
    field(name, label, "tel", {
      pattern: "[+]?[0-9]{7,15}",
      maxLength: 16,
      required,
    });
  $("edit-staff").addEventListener("click", () => {
    const p = currentStaff();
    if (!p) return;
    openForm(
      "Update staff details",
      [
        field("name", "Staff full name"),
        field("role", "Role / designation"),
        phone("contactNumber", "Staff contact number", false),
        field("joinedOn", "Joining date", "date", { required: false }),
        field("address", "Staff address", "textarea", {
          required: false,
          maxLength: 1000,
        }),
      ],
      p,
      "Existing attendance and sales remain linked to the same employee ID.",
      async (data) => {
        data.contactNumber = data.contactNumber || null;
        data.joinedOn = data.joinedOn || null;
        await api(`/api/staff/${p.id}`, {
          method: "PUT",
          body: JSON.stringify(data),
        });
        await loadHr();
      },
    );
  });
  $("new-contract").addEventListener("click", async () => {
    const person = currentStaff();
    if (!person) return;
    try {
      await settings();
      openForm(
        "Employment contract draft",
        [
          field("employerName", "Employer legal name", "text", {
            maxLength: 200,
          }),
          field(
            "registrationNumber",
            "Establishment registration / licence reference",
          ),
          field(
            "workplaceAddress",
            "Registered workplace address",
            "textarea",
            { maxLength: 1000 },
          ),
          field("employeeAddress", "Employee residential address", "textarea", {
            maxLength: 1000,
            value: person.address || "",
          }),
          {
            ...phone("employeeContactNumber", "Employee contact number"),
            value: person.contactNumber || "",
          },
          field("jurisdiction", "Jurisdiction", "text", {
            value: "Punjab, India",
          }),
          field("employmentType", "Employment type", "select", {
            options: [
              "Full-time employee",
              "Part-time employee",
              "Fixed-term employee",
            ],
          }),
          field("startDate", "Employment start date", "date", {
            value: person.joinedOn || state.settings.today,
          }),
          field("endDate", "Employment end date (optional)", "date", {
            required: false,
          }),
          field("probationMonths", "Probation months", "number", {
            min: 0,
            max: 24,
            step: 1,
            value: 0,
          }),
          field("noticeDays", "Notice days", "number", {
            min: 0,
            max: 365,
            step: 1,
            value: "",
          }),
          field(
            "workingHours",
            "Working hours, breaks & overtime terms",
            "textarea",
            { maxLength: 2000 },
          ),
          field("weeklyRest", "Weekly rest day / terms", "textarea", {
            maxLength: 1000,
          }),
          field("leaveTerms", "Leave and holiday entitlement", "textarea", {
            maxLength: 3000,
          }),
          field(
            "remunerationTerms",
            "Pay, payment date & remuneration terms",
            "textarea",
            { maxLength: 3000 },
          ),
          field(
            "statutoryBenefits",
            "Applicable statutory benefits (EPF/ESI etc.)",
            "textarea",
            { maxLength: 3000 },
          ),
          field(
            "terminationTerms",
            "Termination, notice & grievance terms",
            "textarea",
            { maxLength: 3000 },
          ),
          field(
            "confidentialityTerms",
            "Confidentiality & workplace conduct terms",
            "textarea",
            { maxLength: 2000 },
          ),
          field("additionalTerms", "Additional reviewed terms", "textarea", {
            maxLength: 4000,
            required: false,
          }),
        ],
        {},
        "DRAFT for Punjab, India. Enter verified terms from your local adviser. No legal rates or statutory eligibility are assumed.",
        async (data) => {
          data.staffId = person.id;
          data.endDate = data.endDate || null;
          data.probationMonths = Number(data.probationMonths);
          data.noticeDays = Number(data.noticeDays);
          await api("/api/contracts", {
            method: "POST",
            body: JSON.stringify(data),
          });
          await staffDocuments();
        },
      );
    } catch (e) {
      error("hr-error", e.message);
    }
  });
  $("new-salary").addEventListener("click", async () => {
    const person = currentStaff();
    if (!person) return;
    try {
      await settings();
      openForm(
        "Add salary structure",
        [
          field("effectiveFrom", "Effective from", "date", {
            value: state.settings.today,
          }),
          { ...amount("annualCtc", "Annual CTC (INR)"), min: "0.01" },
          amount("monthlyBasic", "Monthly basic (INR)"),
          amount("monthlyHra", "Monthly HRA (INR)"),
          amount("monthlyAllowances", "Monthly allowances (INR)"),
          amount(
            "monthlyEmployerContributions",
            "Monthly employer contributions (INR)",
          ),
          amount(
            "monthlyEmployeeDeductions",
            "Monthly employee deductions (INR)",
          ),
          amount("annualBonus", "Annual bonus (INR)"),
          field(
            "notes",
            "Salary notes / verified statutory applicability",
            "textarea",
            { maxLength: 4000, required: false },
          ),
        ],
        {},
        "Annual CTC = 12 × (basic + HRA + allowances + employer contributions) + bonus. Use verified amounts, not assumed statutory rates.",
        async (data) => {
          data.staffId = person.id;
          await api("/api/salaries", {
            method: "POST",
            body: JSON.stringify(data),
          });
          await staffDocuments();
        },
      );
      $("salary-preview").hidden = false;
      salaryPreview();
    } catch (e) {
      error("hr-error", e.message);
    }
  });
  function salaryPreview() {
    if ($("admin-title").textContent !== "Add salary structure") return;
    const form = $("admin-form");
    const value = (k) => Number(form.elements[k]?.value || 0);
    const gross =
      value("monthlyBasic") + value("monthlyHra") + value("monthlyAllowances");
    $("salary-preview").textContent =
      `Calculated annual CTC: ${money(12 * (gross + value("monthlyEmployerContributions")) + value("annualBonus"))} · Monthly gross: ${money(gross)} · Indicative take-home: ${money(gross - value("monthlyEmployeeDeductions"))}`;
  }
  $("admin-fields").addEventListener("input", salaryPreview);
  async function loadExpenses() {
    try {
      await settings();
      if (!$("expense-date").value)
        $("expense-date").value = state.settings.today;
      $("expense-date").max = state.settings.today;
      if (!$("expense-date").checkValidity()) return;
      error("expense-error", "");
      const date = $("expense-date").value;
      const page = state.expensePage;
      const result = await api(`/api/expenses?date=${date}&page=${page}`);
      if (date !== $("expense-date").value || page !== state.expensePage)
        return;
      cards("expense-summary", [
        ["Paid expenses", money(result.summary.total)],
        ["Entries for this date", result.summary.entries],
      ]);
      $("expense-body").replaceChildren();
      for (const e of result.entries) {
        const tr = el("tr");
        tr.append(
          el("td", "", e.category),
          el("td", "", e.description),
          el("td", "money", money(e.amount)),
          el("td", "", e.payment_method),
        );
        $("expense-body").append(tr);
      }
      $("expense-page").textContent =
        `Page ${page + 1} · ${result.entries.length} shown · ${result.summary.entries} total`;
      $("expense-previous").disabled = page === 0;
      $("expense-next").disabled =
        (page + 1) * 50 >= Number(result.summary.entries);
    } catch (e) {
      $("expense-body").replaceChildren();
      $("expense-summary").replaceChildren();
      error("expense-error", e.message);
    }
  }
  $("new-expense").addEventListener("click", async () => {
    try {
      await settings();
      const requestId = crypto.randomUUID();
      openForm(
        "Record daily expense",
        [
          field("day", "Date paid", "date", {
            value: $("expense-date").value || state.settings.today,
            max: state.settings.today,
          }),
          field("category", "Expense category", "select", {
            options: [
              "RENT",
              "SALARY",
              "UTILITIES",
              "SUPPLIES",
              "MAINTENANCE",
              "OTHER",
            ],
          }),
          field("description", "Expense description", "text", {
            maxLength: 500,
          }),
          field("amount", "Amount paid (INR)", "number", {
            min: ".01",
            max: "99999999.99",
            step: ".01",
          }),
          field("paymentMethod", "Paid using", "select", {
            options: ["UPI", "CASH", "CARD"],
          }),
        ],
        {},
        "Record actual paid expenses once. Salary structures do not automatically create payroll expenses.",
        async (data) => {
          data.requestId = requestId;
          await api("/api/expenses", {
            method: "POST",
            body: JSON.stringify(data),
          });
          $("expense-date").value = data.day;
          state.expensePage = 0;
          await loadExpenses();
        },
      );
    } catch (e) {
      error("expense-error", e.message);
    }
  });
  async function loadContacts() {
    try {
      error("contact-error", "");
      const contacts = await api("/api/contacts");
      $("contacts-list").replaceChildren();
      if (!contacts.length)
        $("contacts-list").append(
          el(
            "p",
            "empty",
            "Add an electrician, plumber or another useful contact.",
          ),
        );
      for (const c of contacts) {
        const card = el("article", "panel contact-card");
        card.append(el("p", "eyebrow", c.service), el("h2", "", c.name));
        const primary = el("a", "contact-phone", c.phone);
        primary.href = "tel:" + c.phone;
        card.append(primary);
        if (c.secondary_phone) {
          const secondary = el("a", "contact-phone", c.secondary_phone);
          secondary.href = "tel:" + c.secondary_phone;
          card.append(secondary);
        }
        card.append(el("p", "muted", c.address), el("p", "muted", c.notes));
        const button = el("button", "button secondary", "Edit contact");
        button.addEventListener("click", () => contactForm(c));
        card.append(button);
        $("contacts-list").append(card);
      }
    } catch (e) {
      error("contact-error", e.message);
    }
  }
  function contactForm(c) {
    openForm(
      c ? "Update emergency contact" : "Add emergency contact",
      [
        field("name", "Contact person / business"),
        field("service", "Service / trade", "text", { value: "Electrician" }),
        { ...phone("phone", "Primary phone"), pattern: "[+]?[0-9]{3,15}" },
        {
          ...phone("secondaryPhone", "Secondary phone (optional)", false),
          pattern: "[+]?[0-9]{3,15}",
        },
        field("address", "Service address / area", "textarea", {
          required: false,
          maxLength: 1000,
        }),
        field("notes", "Emergency contact notes", "textarea", {
          required: false,
          maxLength: 2000,
        }),
      ],
      c ? { ...c, secondaryPhone: c.secondary_phone } : {},
      "Examples: electrician, plumber, AC technician, medical or security. Emergency numbers are manually maintained.",
      async (data) => {
        data.secondaryPhone = data.secondaryPhone || null;
        await api("/api/contacts" + (c ? "/" + c.id : ""), {
          method: c ? "PUT" : "POST",
          body: JSON.stringify(data),
        });
        await loadContacts();
      },
    );
  }
  $("new-contact").addEventListener("click", () => contactForm(null));
  function csvLink() {
    const input = $("export-date"),
      period = $("export-period").value;
    let date = input.value;
    if (period === "MONTH") date += "-01";
    if (period === "YEAR") date += "-01-01";
    $("csv-download").href =
      `/api/reports/sales.csv?period=${period}&date=${date}`;
  }
  $("csv-download").addEventListener("click", (e) => {
    if (!$("export-date").checkValidity()) {
      e.preventDefault();
      $("export-date").reportValidity();
    }
  });
  $("export-period").addEventListener("change", () => {
    const p = $("export-period").value;
    const d = state.settings?.today || new Date().toISOString().slice(0, 10);
    $("export-date").type =
      p === "DAY" ? "date" : p === "MONTH" ? "month" : "number";
    $("export-date").removeAttribute("max");
    $("export-date").removeAttribute("min");
    if (p === "YEAR") {
      $("export-date").min = 1900;
      $("export-date").max = 9998;
    }
    $("export-date").value =
      p === "DAY" ? d : p === "MONTH" ? d.slice(0, 7) : d.slice(0, 4);
    csvLink();
  });
  $("export-date").addEventListener("input", csvLink);
  async function loadReports() {
    try {
      await settings();
      if (!$("export-date").value)
        $("export-date").value = state.settings.today;
      csvLink();
      if (!$("profit-month").value)
        $("profit-month").value = state.settings.today.slice(0, 7);
      if (!$("profit-month").checkValidity()) return;
      const date = $("profit-month").value + "-01";
      error("report-error", "");
      const result = await api("/api/reports/profit-loss?date=" + date);
      if (date !== $("profit-month").value + "-01") return;
      cards("profit-summary", [
        ["Sales collections", money(result.revenue)],
        ["Recorded expenses", money(result.expenses)],
        [
          Number(result.profit) < 0 ? "Operating loss" : "Operating profit",
          money(result.profit),
        ],
      ]);
      $("profit-categories").replaceChildren();
      for (const c of result.categories)
        $("profit-categories").append(
          el("span", "badge", `${c.category}: ${money(c.amount)}`),
        );
      $("profit-download").href =
        `/api/reports/profit-loss.pdf?date=${date}&download=true`;
      $("profit-print").href = `/api/reports/profit-loss.pdf?date=${date}`;
    } catch (e) {
      $("profit-summary").replaceChildren();
      error("report-error", e.message);
    }
  }
  $("hr-staff").addEventListener("change", staffDocuments);
  $("hr-refresh").addEventListener("click", loadHr);
  $("expense-date").addEventListener("change", () => {
    state.expensePage = 0;
    loadExpenses();
  });
  $("expense-refresh").addEventListener("click", loadExpenses);
  $("expense-previous").addEventListener("click", () => {
    if (state.expensePage > 0) {
      state.expensePage--;
      loadExpenses();
    }
  });
  $("expense-next").addEventListener("click", () => {
    state.expensePage++;
    loadExpenses();
  });
  $("profit-month").addEventListener("change", loadReports);
  $("profit-refresh").addEventListener("click", loadReports);
  function activate(view) {
    if (view === "hr") loadHr();
    if (view === "expenses") loadExpenses();
    if (view === "contacts") loadContacts();
    if (view === "reports") loadReports();
  }
  window.addEventListener("ark:view", (e) => activate(e.detail));
  activate(location.hash.slice(1));
})();
