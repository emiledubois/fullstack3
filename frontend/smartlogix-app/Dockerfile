# Stage 1: build React app
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# Stage 2: serve with nginx
FROM nginxinc/nginx-unprivileged:alpine
# Copy built files
COPY --from=build /app/dist /usr/share/nginx/html
# Copy nginx config for SPA routing
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Picks up base-OS CVE patches (e.g. openssl) ahead of the next base-image tag refresh
USER root
RUN apk upgrade --no-cache
USER nginx
EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
