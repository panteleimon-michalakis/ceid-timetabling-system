# ===== Build stage =====
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# Σχετικό API path: τα /api requests τα προωθεί ο nginx στο backend (βλ. nginx.conf)
ENV VITE_API_URL=/api
RUN npm run build

# ===== Runtime stage (nginx) =====
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
