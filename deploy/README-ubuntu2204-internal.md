# Ubuntu 22.04 内网共享部署说明

## 1. 外网准备

在外网构建以下内容：

1. 后端 jar
```bash
mvn -DskipTests package
```

2. 前端 dist
```bash
cd frontend
npm install
npm run build
cd ..
```

3. Docker 镜像
```bash
docker build -t corp-idm-platform:internal .
docker build -t corp-idm-web:internal ./frontend
docker pull mysql:8.0
docker pull osixia/openldap:1.5.0
```

4. 导出镜像
```bash
docker save -o corp-idm-platform-internal.tar corp-idm-platform:internal
docker save -o corp-idm-web-internal.tar corp-idm-web:internal
docker save -o mysql-8.0.tar mysql:8.0
docker save -o openldap-1.5.0.tar osixia/openldap:1.5.0
```

## 2. 复制到内网 Ubuntu 22.04

复制以下内容到内网服务器：

- `corp-idm-platform-internal.tar`
- `corp-idm-web-internal.tar`
- `mysql-8.0.tar`
- `openldap-1.5.0.tar`
- `deploy/docker-compose-internal.yml`
- `deploy/.env.internal.example`
- `deploy/nginx/default.conf`
- `frontend/nginx/default.conf`
- `deploy/openldap/bootstrap/`

## 3. 内网服务器安装 Docker

Ubuntu 22.04 最少需要安装：

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-plugin
sudo systemctl enable docker
sudo systemctl start docker
```

## 4. 导入镜像

```bash
docker load -i corp-idm-platform-internal.tar
docker load -i corp-idm-web-internal.tar
docker load -i mysql-8.0.tar
docker load -i openldap-1.5.0.tar
```

## 5. 准备部署目录

```bash
mkdir -p /opt/corp-idm/deploy
mkdir -p /opt/corp-idm/imports
```

将 `docker-compose-internal.yml`、`.env.internal.example`、`nginx/default.conf`、`openldap/bootstrap` 放到 `/opt/corp-idm/deploy/`

```bash
cd /opt/corp-idm/deploy
cp .env.internal.example .env.internal
```

## 6. 修改 .env.internal

至少修改：

- `APP_JWT_SECRET`
- `APP_MAIL_SECURITY_SECRET_KEY`
- `SPRING_MAIL_*` 或后台邮件配置
- 若使用公司现成 MySQL / LDAP，则改 `APP_DB_URL`、`APP_LDAP_URL` 等地址，并在 compose 中去掉对应容器

## 7. 启动

```bash
docker compose --env-file .env.internal -f docker-compose-internal.yml up -d
```

## 8. 访问

浏览器访问：

```text
http://<服务器IP>/
```

后端 API 默认由 Nginx 转发到：

```text
http://idm-app:8081/api
```

## 9. 说明

- 当前方案适合“内网共享使用”
- 若公司已有 MySQL / LDAP，推荐复用现有服务，不要重复起容器
- 若内网不能访问飞书公网接口，请保持 `APP_SYNC_FEISHU_ENABLED=false`
- 文件导入目录通过 `IMPORTS_HOST_DIR` 挂载到 `/app/imports`
