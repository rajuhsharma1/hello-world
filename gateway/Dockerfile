FROM nginx:1.28-alpine
COPY gateway/proxy_params /etc/nginx/proxy_params_spa
COPY gateway/nginx.conf /etc/nginx/conf.d/default.conf
COPY --chown=nginx:nginx ui/index.html ui/app.js ui/styles.css ui/operations.js ui/administration.js ui/logo.svg /usr/share/nginx/html/
COPY --chown=nginx:nginx ui/assets/ /usr/share/nginx/html/assets/
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
