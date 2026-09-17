param(
    [string]$ContainerName = 'corp-idm-backend-dev',
    [string]$PrimaryNetwork = 'deploy_default',
    [string]$MailNetwork = '',
    [int]$HostPort = 8083,
    [string]$DbHost = 'corp-idm-mysql',
    [string]$DbName = 'corp_idm',
    [string]$DbUsername = 'corp_idm',
    [string]$DbPassword = 'corp_idm',
    [string]$LdapHost = 'corp-idm-openldap',
    [int]$LdapPort = 389,
    [string]$LdapBindDn = 'cn=admin,dc=corp,dc=local',
    [string]$LdapBindPassword = 'admin'
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path $PSScriptRoot -Parent
$jarPath = Join-Path $projectRoot 'target\corp-idm-platform-0.1.0-SNAPSHOT.jar'
$feishuImportPath = Join-Path $projectRoot 'docs\feishu-import'

if (-not (Test-Path $jarPath)) {
    throw "Jar not found: $jarPath. Build it first with Maven."
}

docker network inspect $PrimaryNetwork *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Docker network not found: $PrimaryNetwork. Start deploy/docker-compose-dev.yml first."
}

if ($MailNetwork) {
    docker network inspect $MailNetwork *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker network not found: $MailNetwork. Start the CrowncadEmail verify stack first, or pass -MailNetwork ''."
    }
}

$existingContainerId = docker ps -aq --filter "name=^/$ContainerName$"
if ($existingContainerId) {
    docker rm -f $ContainerName | Out-Null
}

$datasourceUrl = "jdbc:mysql://$($DbHost):3306/$DbName" `
    + "?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_general_ci&serverTimezone=Asia/Shanghai" `
    + "&allowPublicKeyRetrieval=true&useSSL=false"
$ldapUrl = "ldap://$($LdapHost):$LdapPort"

$dockerArgs = @(
    'run',
    '-d',
    '--name', $ContainerName,
    '--restart', 'unless-stopped',
    '--network', $PrimaryNetwork,
    '-p', "$($HostPort):8083",
    '-v', "$($jarPath):/app/app.jar:ro",
    # 容器时区：与其他容器（MySQL/RabbitMQ）保持一致，避免 Java 写入时间比本地时间早 8 小时
    '-e', 'TZ=Asia/Shanghai',
    # 角色变更推送：连接 dev compose 中的 rabbitmq 服务（与 docker-compose-internal.yml 保持一致）
    '-e', 'SPRING_RABBITMQ_HOST=corp-idm-rabbitmq',
    '-e', 'SPRING_RABBITMQ_PORT=5672',
    '-e', 'SPRING_RABBITMQ_USERNAME=idm',
    '-e', 'SPRING_RABBITMQ_PASSWORD=idm',
    '-e', 'APP_RABBITMQ_MANAGEMENT_BASE_URL=http://corp-idm-rabbitmq:15672',
    '-e', 'APP_RABBITMQ_MANAGEMENT_USERNAME=idm',
    '-e', 'APP_RABBITMQ_MANAGEMENT_PASSWORD=idm'
)

if (Test-Path $feishuImportPath) {
    $dockerArgs += @('-v', "$($feishuImportPath):/docs/feishu-import")
}

$dockerArgs += @(
    'eclipse-temurin:17-jre-jammy',
    'java',
    '-jar',
    '/app/app.jar',
    '--spring.profiles.active=dev',
    '--server.port=8083',
    "--spring.datasource.url=$datasourceUrl",
    "--spring.datasource.username=$DbUsername",
    "--spring.datasource.password=$DbPassword",
    '--app.ldap.mode=spring',
    "--app.ldap.url=$ldapUrl",
    "--app.ldap.bind-dn=$LdapBindDn",
    "--app.ldap.bind-password=$LdapBindPassword",
    '--app.startup-check.enabled=true'
)

docker @dockerArgs | Out-Null

if ($MailNetwork) {
    docker network connect $MailNetwork $ContainerName
}

Write-Host "Backend container started: $ContainerName"
Write-Host "API: http://127.0.0.1:$HostPort"
if ($MailNetwork) {
    Write-Host "Mail network attached: $MailNetwork"
}
