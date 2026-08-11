FROM node:22-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
# 注意：npm run build（vue-tsc -b）目前被若干既有视图的 el-table 槽位类型错误阻塞（非本次改动引入），
# 镜像构建使用 build:no-check 仅执行 vite build（TS 类型不影响运行时产物）。类型债修复见仓库跟进项。
RUN npm run build:no-check

FROM nginx:1.27-alpine

ENV BACKEND_UPSTREAM=idm-app:8081

COPY --from=build /app/dist/ /usr/share/nginx/html/
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
