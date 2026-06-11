# 1단계 - 빌드
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# 배포 산출물은 항상 실제 백엔드에 연결한다 — mock 모드를 명시적으로 끈다 (AUDIT P0-4).
ENV VITE_USE_MOCK=false
RUN npm run build

# 2단계 - Nginx로 서빙
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
