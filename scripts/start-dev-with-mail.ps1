$ErrorActionPreference = 'Stop'

$env:SPRING_MAIL_HOST = 'smtp.qq.com'
$env:SPRING_MAIL_PORT = '587'
$env:SPRING_MAIL_USERNAME = '1940579892@qq.com'
$env:SPRING_MAIL_PASSWORD = 'kixmducmmqwrcjhi'
$env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH = 'true'
$env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE = 'true'
$env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED = 'true'
$env:SPRING_MAIL_DEFAULT_ENCODING = 'UTF-8'

$env:APP_PASSWORD_RESET_MAIL_FROM = '1940579892@qq.com'
$env:APP_PASSWORD_RESET_MAIL_SUBJECT = '统一身份管理平台密码重置通知'

$projectRoot = Split-Path $PSScriptRoot -Parent
$jarPath = Join-Path $projectRoot 'target\corp-idm-platform-0.1.0-SNAPSHOT.jar'

if (-not (Test-Path $jarPath)) {
    throw "Jar not found: $jarPath"
}

java -jar $jarPath --spring.profiles.active=dev
