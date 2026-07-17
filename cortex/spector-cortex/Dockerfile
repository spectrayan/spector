# ── Stage 1: Build Angular ──
FROM node:24-alpine AS builder

WORKDIR /app/spector-cortex
COPY cortex/spector-cortex/package*.json ./
RUN npm ci --prefer-offline
COPY cortex/spector-cortex/ .
RUN npx ng build --configuration=production

# ── Stage 2: Serve with Nginx ──
FROM nginx:alpine

COPY --from=builder /app/spector-cortex/dist/spector-cortex/browser /usr/share/nginx/html

# Nginx config for SPA routing
RUN echo 'server { \
    listen 80; \
    root /usr/share/nginx/html; \
    index index.html; \
    location / { try_files $uri $uri/ /index.html; } \
    location /api/ { proxy_pass http://synapse:7070; } \
}' > /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
