"use strict";
(async () => {
  await window.ArkAppReady;
  if (!window.Ark) return;
  const { api, el, notice } = window.Ark,
    $ = (id) => document.getElementById(id),
    user = window.ArkSession.user;
  $("account-name").textContent = user.displayName;
  $("account-role").textContent = user.role.toLowerCase();
  $("account-initial").textContent = user.displayName
    .trim()
    .slice(0, 1)
    .toUpperCase();
  let busy = false,
    action = null,
    generation = 0;
  function error(id, message) {
    $(id).textContent = message;
    $(id).hidden = !message;
  }
  $("sign-out").addEventListener("click", async () => {
    const button = $("sign-out");
    button.disabled = true;
    try {
      await api("/api/auth/logout", { method: "POST", body: "{}" });
      location.replace("/login.html");
    } catch (e) {
      notice(e.message);
      button.disabled = false;
    }
  });
  function open(title, fields, help, save) {
    $("security-form").reset();
    $("security-title").textContent = title;
    $("security-help").textContent = help;
    error("security-error", "");
    $("security-fields").replaceChildren();
    action = save;
    for (const field of fields) {
      const wrap = el("div");
      const label = el("label", "", field.label);
      label.htmlFor = "security-" + field.name;
      const input = el(field.options ? "select" : "input");
      input.name = field.name;
      input.id = label.htmlFor;
      input.required = true;
      if (field.options) {
        for (const option of field.options) {
          const item = el("option", "", option);
          item.value = option;
          input.append(item);
        }
      } else {
        input.type = field.type || "text";
        input.maxLength = field.type === "password" ? 128 : 100;
        if (field.type === "password") {
          input.minLength = 12;
          input.autocomplete =
            field.name === "currentPassword"
              ? "current-password"
              : "new-password";
        }
      }
      input.value = field.value ?? field.options?.[0] ?? "";
      wrap.append(label, input);
      $("security-fields").append(wrap);
    }
    $("security-dialog").showModal();
    $("security-fields").querySelector("input,select")?.focus();
  }
  function close() {
    if (!busy) $("security-dialog").close();
  }
  $("security-close").addEventListener("click", close);
  $("security-cancel").addEventListener("click", close);
  $("security-dialog").addEventListener("cancel", (e) => {
    if (busy) e.preventDefault();
  });
  $("security-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    if (busy) return;
    busy = true;
    error("security-error", "");
    for (const id of ["security-save", "security-close", "security-cancel"])
      $(id).disabled = true;
    try {
      await action(Object.fromEntries(new FormData(e.currentTarget)));
      $("security-dialog").close();
      notice("Account security updated.");
    } catch (e) {
      error("security-error", e.message);
    } finally {
      busy = false;
      for (const id of ["security-save", "security-close", "security-cancel"])
        $(id).disabled = false;
    }
  });
  $("account-menu").addEventListener("click", () =>
    open(
      "Change your password",
      [
        {
          name: "currentPassword",
          label: "Current password",
          type: "password",
        },
        { name: "newPassword", label: "New password", type: "password" },
        { name: "confirm", label: "Confirm new password", type: "password" },
      ],
      "Changing your password ends all of your sessions. Use at least 12 characters.",
      async (data) => {
        if (data.newPassword !== data.confirm)
          throw new Error("Passwords do not match.");
        await api("/api/auth/password", {
          method: "POST",
          body: JSON.stringify(data),
        });
        location.replace("/login.html");
      },
    ),
  );
  async function loadUsers() {
    if (user.role !== "OWNER") return;
    error("users-error", "");
    try {
      const accounts = await api("/api/auth/users");
      $("users-body").replaceChildren();
      for (const account of accounts) {
        const row = el("tr"),
          name = el("td", "", account.display_name);
        name.append(el("small", "", account.username));
        const role = el("td");
        role.append(el("span", "badge", account.role));
        const status = el(
          "td",
          "",
          !account.enabled
            ? "Disabled"
            : account.must_change
              ? "Password change required"
              : account.locked_until &&
                  new Date(account.locked_until) > new Date()
                ? "Temporarily locked"
                : "Active",
        );
        const actions = el("td", "account-actions");
        const edit = el("button", "button secondary", "Edit access");
        edit.addEventListener("click", () =>
          open(
            "Update account access",
            [
              {
                name: "role",
                label: "Account role",
                options: ["OWNER", "ADMIN", "STAFF"],
                value: account.role,
              },
              {
                name: "enabled",
                label: "Account status",
                options: ["Enabled", "Disabled"],
                value: account.enabled ? "Enabled" : "Disabled",
              },
            ],
            "Disabling an account ends its sessions. At least one enabled owner must remain.",
            async (data) => {
              await api("/api/auth/users/" + account.id, {
                method: "PUT",
                body: JSON.stringify({
                  role: data.role,
                  enabled: data.enabled === "Enabled",
                }),
              });
              if (account.username === user.username) {
                location.reload();
                return;
              }
              await loadUsers();
            },
          ),
        );
        const reset = el("button", "button secondary", "Reset password");
        reset.addEventListener("click", () =>
          open(
            "Reset account password",
            [
              {
                name: "password",
                label: "Temporary password",
                type: "password",
              },
            ],
            "The user must change this password at next sign-in. Their existing sessions will end. Share it securely.",
            async (data) => {
              await api("/api/auth/users/" + account.id + "/password", {
                method: "POST",
                body: JSON.stringify(data),
              });
              await loadUsers();
            },
          ),
        );
        actions.append(edit, reset);
        row.append(name, role, status, actions);
        $("users-body").append(row);
      }
    } catch (e) {
      error("users-error", e.message);
    }
  }
  $("new-account").addEventListener("click", () =>
    open(
      "Create workspace account",
      [
        { name: "username", label: "Account username" },
        { name: "displayName", label: "Account display name" },
        {
          name: "role",
          label: "Account role",
          options: ["STAFF", "ADMIN", "OWNER"],
        },
        { name: "password", label: "Temporary password", type: "password" },
      ],
      "New accounts must change their temporary password before opening records. Give only the access needed.",
      async (data) => {
        await api("/api/auth/users", {
          method: "POST",
          body: JSON.stringify(data),
        });
        await loadUsers();
      },
    ),
  );
  async function loadLogs() {
    if (user.role === "STAFF") return;
    const current = ++generation,
      errorId = "log-error";
    $("refresh-logs").disabled = true;
    error(errorId, "");
    try {
      const audit = $("log-source").value === "audit";
      for (const id of [
        "log-service",
        "log-level",
        "log-period",
        "log-search",
        "log-trace",
      ])
        $(id).disabled = audit;
      if (!audit && !$("log-trace").checkValidity()) {
        $("log-trace").reportValidity();
        return;
      }
      const query = new URLSearchParams({
        service: $("log-service").value,
        level: $("log-level").value,
        minutes: $("log-period").value,
        search: $("log-search").value,
        traceId: $("log-trace").value,
        limit: "100",
      });
      const result = await api(
        audit ? "/api/auth/audit?limit=100" : "/api/auth/logs?" + query,
      );
      if (current !== generation) return;
      const entries = audit ? result : result.entries;
      $("logs-head").replaceChildren();
      $("logs-body").replaceChildren();
      const header = el("tr");
      for (const label of audit
        ? ["When", "Actor", "Action", "Target"]
        : ["When", "Service", "Level", "Message", "Trace"])
        header.append(el("th", "", label));
      $("logs-head").append(header);
      for (const entry of entries) {
        const row = el("tr");
        if (audit) {
          for (const key of ["occurred_at", "actor", "action", "target"])
            row.append(
              el(
                "td",
                "",
                key === "occurred_at"
                  ? new Date(entry[key]).toLocaleString()
                  : entry[key],
              ),
            );
        } else {
          row.append(
            el("td", "log-time", new Date(entry.timestamp).toLocaleString()),
            el("td", "", entry.service),
          );
          const level = el("td");
          level.append(
            el("span", "log-level level-" + entry.level, entry.level),
          );
          row.append(
            level,
            el("td", "log-message", entry.message),
            el("td", "log-trace", entry.traceId || "—"),
          );
        }
        $("logs-body").append(row);
      }
      $("logs-empty").hidden = entries.length > 0;
      $("log-status").textContent =
        `${entries.length} entries loaded · ${audit ? "Latest security events" : "Last " + $("log-period").value + " minutes"} · Refreshed ${new Date().toLocaleTimeString()}`;
    } catch (e) {
      if (current === generation) {
        error(errorId, e.message);
        $("logs-body").replaceChildren();
        $("log-status").textContent = "Unable to load current entries.";
      }
    } finally {
      if (current === generation) $("refresh-logs").disabled = false;
    }
  }
  $("refresh-logs").addEventListener("click", loadLogs);
  $("log-source").addEventListener("change", loadLogs);
  for (const id of ["log-service", "log-level", "log-period"])
    $(id).addEventListener("change", loadLogs);
  function activate(view) {
    if (view === "users") loadUsers();
    if (view === "logs") loadLogs();
  }
  window.addEventListener("ark:view", (e) => activate(e.detail));
  activate(location.hash.slice(1));
  setInterval(() => {
    if (location.hash === "#logs" && $("log-auto").checked && !document.hidden)
      loadLogs();
  }, 10000);
})();
