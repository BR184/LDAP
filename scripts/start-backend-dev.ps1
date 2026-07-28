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
    + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai" `
    + "&allowPublicKeyRetrieval=true&useSSL=false"
$ldapUrl = "ldap://$($LdapHost):$LdapPort"

$dockerArgs = @(
    'run',
    '-d',
    '--name', $ContainerName,
    '--restart', 'unless-stopped',
    '--network', $PrimaryNetwork,
    '-p', "$($HostPort):8083",
    '-v', "$($jarPath):/app/app.jar:ro"
)

if (Test-Path $feishuImportPath) {
    $dockerArgs += @('-v', "$($feishuImportPath):/app/docs/feishu-import")
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
