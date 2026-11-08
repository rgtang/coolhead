# ---- build ----
FROM node:22-alpine AS build
WORKDIR /app
ARG VITE_PAYPAL_CLIENT_ID=""
ARG VITE_API_BASE=""
ENV VITE_PAYPAL_CLIENT_ID=$VITE_PAYPAL_CLIENT_ID VITE_API_BASE=$VITE_API_BASE
COPY package.json package-lock.json* ./
RUN npm install
COPY . .
RUN npm run build

# ---- serve ----
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
