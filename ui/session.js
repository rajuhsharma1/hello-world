"use strict";
window.ArkReady = (async () => {
  try {
    const response = await fetch("/api/auth/session", { cache: "no-store" });
    if (!response.ok) throw new Error("Authentication service unavailable");
    const session = await response.json();
    if (!session.authenticated || session.user.mustChangePassword) {
      location.replace("/login.html");
      return null;
    }
    window.ArkSession = {
      ...session,
      fetch: async (path, options = {}) => {
        const method = (options.method || "GET").toUpperCase();
        const headers = { ...options.headers };
        if (!["GET", "HEAD", "OPTIONS"].includes(method))
          headers["X-CSRF-Token"] = window.ArkSession.csrfToken;
        const result = await fetch(path, {
          ...options,
          headers,
          cache: "no-store",
        });
        if (result.status === 401) {
          location.replace("/login.html?reason=expired");
          throw new Error("Your session expired. Please sign in again.");
        }
        if (result.status === 403)
          throw new Error(
            "This action is not permitted, or your security token expired. Refresh and try again.",
          );
        return result;
      },
    };
    document.body.classList.remove("auth-pending");
    return window.ArkSession;
  } catch (error) {
    document.body.classList.remove("auth-pending");
    document.getElementById("auth-unavailable").hidden = false;
    document.getElementById("auth-unavailable").textContent =
      "Unable to verify your session. Refresh to reconnect, or open the sign-in page.";
    return null;
  }
})();
