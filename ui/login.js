"use strict";
(() => {
  const $ = (id) => document.getElementById(id);
  let csrf = "",
    busy = false;
  function message(id, text) {
    $(id).textContent = text;
    $(id).hidden = !text;
  }
  async function session() {
    const r = await fetch("/api/auth/session", { cache: "no-store" });
    if (!r.ok)
      throw new Error(
        "Sign-in service is temporarily unavailable. Try again shortly.",
      );
    const s = await r.json();
    csrf = s.csrfToken;
    for (const id of ["login-form", "setup-form", "change-form"])
      $(id).hidden = true;
    if (s.authenticated && !s.user.mustChangePassword) {
      location.replace("/index.html");
      return;
    }
    if (s.authenticated) {
      $("change-form").hidden = false;
      $("login-title").textContent = "Make it your own.";
      $("login-description").textContent =
        "Choose a new password for your account.";
    } else if (s.needsSetup) {
      $("setup-form").hidden = false;
      $("login-title").textContent = "Welcome to The Ark.";
      $("login-description").textContent =
        "Create the first owner account securely.";
      if (!s.setupConfigured)
        message(
          "login-error",
          "Setup is not configured. Run scripts/setup-security.py and restart Docker Compose.",
        );
    } else {
      $("login-form").hidden = false;
      $("login-title").textContent = "Welcome back.";
      $("login-description").textContent = "Sign in to your salon workspace.";
    }
  }
  async function submit(form, path, data, done) {
    if (busy) return;
    busy = true;
    message("login-error", "");
    const button = form.querySelector("[type=submit]");
    button.disabled = true;
    try {
      const r = await fetch(path, {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-CSRF-Token": csrf },
        body: JSON.stringify(data),
        signal: AbortSignal.timeout(15000),
      });
      const result = await r.json().catch(() => ({}));
      if (!r.ok)
        throw new Error(
          r.status === 429
            ? "Too many attempts. Wait a moment and try again."
            : result.detail ||
              "Unable to complete sign-in. Check your details and try again.",
        );
      if (result.csrfToken) csrf = result.csrfToken;
      await done(result);
    } catch (e) {
      message(
        "login-error",
        e.name === "TimeoutError"
          ? "Sign-in timed out. Please try again."
          : e.message,
      );
    } finally {
      busy = false;
      button.disabled = false;
    }
  }
  $("login-form").addEventListener("submit", (e) => {
    e.preventDefault();
    submit(
      e.currentTarget,
      "/api/auth/login",
      Object.fromEntries(new FormData(e.currentTarget)),
      session,
    );
  });
  $("setup-form").addEventListener("submit", (e) => {
    e.preventDefault();
    if ($("owner-password").value !== $("owner-confirm").value) {
      message("login-error", "Passwords do not match.");
      return;
    }
    submit(
      e.currentTarget,
      "/api/auth/setup",
      Object.fromEntries(new FormData(e.currentTarget)),
      session,
    );
  });
  $("change-form").addEventListener("submit", (e) => {
    e.preventDefault();
    if ($("new-password").value !== $("confirm-password").value) {
      message("login-error", "Passwords do not match.");
      return;
    }
    submit(
      e.currentTarget,
      "/api/auth/password",
      Object.fromEntries(new FormData(e.currentTarget)),
      async () => {
        e.target.reset();
        message(
          "login-notice",
          "Password changed. Sign in with your new password.",
        );
        await session();
      },
    );
  });
  $("show-password").addEventListener("click", () => {
    const show = $("password").type === "password";
    $("password").type = show ? "text" : "password";
    $("show-password").textContent = show ? "Hide" : "Show";
    $("show-password").setAttribute(
      "aria-label",
      show ? "Hide password" : "Show password",
    );
    $("show-password").setAttribute("aria-pressed", String(show));
  });
  if (new URLSearchParams(location.search).get("reason") === "expired")
    message("login-notice", "Your session ended. Please sign in again.");
  session().catch((e) => message("login-error", e.message));
})();
