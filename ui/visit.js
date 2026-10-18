"use strict";
(() => {
  const node = (tag, text, cls) => {
    const e = document.createElement(tag);
    if (text !== undefined) e.textContent = text;
    if (cls) e.className = cls;
    return e;
  };
  const request = async (url, options = {}) => {
    const r = await fetch(url, { cache: "no-store", ...options });
    const data = await r.json().catch(() => ({}));
    if (!r.ok)
      throw new Error(
        r.status === 429
          ? "Please wait a few minutes before sending another enquiry."
          : data.detail || "Unable to send right now. Please try again.",
      );
    return data;
  };
  request("/api/public/settings")
    .then((s) => {
      const email = document.getElementById("public-email");
      email.textContent = s.recipient_email;
      email.href = "mailto:" + s.recipient_email;
      if (s.hero_video_url) {
        const v = document.getElementById("hero-video");
        v.src = s.hero_video_url;
        v.hidden = false;
        v.addEventListener("error", () => {
          v.hidden = true;
        });
        if (!matchMedia("(prefers-reduced-motion: reduce)").matches) {
          v.autoplay = true;
          v.play().catch(() => {});
        }
      }
    })
    .catch(() => {});
  request("/api/public/blogs")
    .then((posts) => {
      const list = document.getElementById("public-posts");
      list.replaceChildren();
      if (!posts.length)
        list.append(node("p", "Our next story is on its way."));
      for (const p of posts) {
        const article = node("article", undefined, "journal-card");
        article.append(
          node(
            "p",
            new Date(p.created_at).toLocaleDateString("en-IN", {
              year: "numeric",
              month: "long",
              day: "numeric",
            }),
            "eyebrow",
          ),
          node("h3", p.title),
          node("p", p.excerpt),
        );
        const details = node("details");
        details.append(
          node("summary", "Read the story ↗"),
          node("div", p.body.replaceAll("\\n", "\n"), "journal-body"),
        );
        article.append(details);
        list.append(article);
      }
    })
    .catch(() => {
      const e = document.getElementById("public-post-error");
      e.hidden = false;
      e.textContent =
        "The journal is temporarily unavailable. Please try again later.";
      document.getElementById("public-posts").replaceChildren();
    });
  for (const form of document.querySelectorAll("[data-enquiry]")) {
    let reference = crypto.randomUUID();
    const end = form.querySelector(".form-end");
    const trap = node("label", "Leave this field empty", "website-field");
    const input = node("input");
    input.name = "website";
    input.tabIndex = -1;
    input.autocomplete = "off";
    trap.append(input);
    const consent = node("label", undefined, "public-consent");
    const checkbox = node("input");
    checkbox.type = "checkbox";
    checkbox.required = true;
    checkbox.name = "consent";
    consent.append(
      checkbox,
      node(
        "span",
        "I agree that The Ark may use these details to respond to my enquiry or application.",
      ),
    );
    const button = node(
      "button",
      form.dataset.enquiry === "CAREER"
        ? "Send application ↗"
        : "Send enquiry ↗",
      "public-cta",
    );
    button.type = "submit";
    const status = node("div", undefined, "form-status");
    status.setAttribute("role", "status");
    end.append(trap, consent, button, status);
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      if (!form.reportValidity()) return;
      button.disabled = true;
      status.textContent = "Sending…";
      const values = Object.fromEntries(new FormData(form));
      const answers = {};
      for (const key of [
        "position",
        "experience",
        "availability",
        "qualifications",
        "whyJoin",
        "preferredDate",
        "preferredTime",
        "visitDate",
        "bookingReference",
      ])
        if (values[key]) answers[key] = values[key];
      const details = {
        requestId: reference,
        kind: form.dataset.enquiry,
        name: values.name,
        email: values.email,
        phone: values.phone,
        subject: values.subject || "Application: " + values.position,
        message: values.message,
        answers,
        consent: checkbox.checked,
        website: values.website,
      };
      try {
        const body = new FormData();
        body.append(
          "details",
          new Blob([JSON.stringify(details)], { type: "application/json" }),
        );
        const resume = form.elements.resume?.files[0];
        if (resume) {
          if (resume.size > 5 * 1024 * 1024)
            throw new Error("Please choose a PDF resume up to 5 MB.");
          body.append("resume", resume);
        }
        const result = await request("/api/public/enquiries", {
          method: "POST",
          headers: { "X-Ark-Public": "1" },
          body,
        });
        status.textContent = `${result.message} Reference: ${result.reference}`;
        form.reset();
        reference = crypto.randomUUID();
      } catch (error) {
        status.textContent = error.message;
      } finally {
        button.disabled = false;
      }
    });
  }
  for (const a of document.querySelectorAll("[data-service]"))
    a.addEventListener("click", () => {
      document.querySelector(
        '[data-enquiry="BOOKING"] select[name="subject"]',
      ).value = a.dataset.service;
    });
  const today = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Kolkata",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
  document.querySelector('[name="preferredDate"]').min = today;
})();
