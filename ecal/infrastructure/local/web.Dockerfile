FROM node:24.19.0-alpine AS build
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
# A supplied corporate CA is optional; TLS verification stays enabled.
RUN --mount=type=secret,id=npm_ca,target=/tmp/npm-ca \
    if [ -f /tmp/npm-ca ]; then export npm_config_cafile=/tmp/npm-ca; fi; \
    npm ci
COPY frontend/ ./
ARG VITE_ECAL_MODE=monolith
ENV VITE_ECAL_MODE=$VITE_ECAL_MODE
RUN npm run build
FROM nginx:1.28-alpine
COPY infrastructure/local/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /frontend/dist/ /usr/share/nginx/html/
# Public files may inherit a restrictive host umask; Nginx workers must be able to read them.
RUN chmod -R a+rX /usr/share/nginx/html
EXPOSE 8080
