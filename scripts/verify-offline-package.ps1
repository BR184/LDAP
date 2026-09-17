[CmdletBinding()]
param(
    [string]$PackageRoot = (Join-Path $PSScriptRoot '..\offline-images')
)

$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath((Resolve-Path -LiteralPath $PackageRoot).Path)
$errors = [System.Collections.Generic.List[string]]::new()

function Add-Failure {
    param([string]$Message)
    $errors.Add($Message)
}

function Require-File {
    param([string]$RelativePath)
    if (-not (Test-Path -LiteralPath (Join-Path $root $RelativePath) -PathType Leaf)) {
        Add-Failure "Missing file: $RelativePath"
    }
}

function Read-EnvFile {
    $values = @{}
    $path = Join-Path $root '.env'
    foreach ($line in Get-Content -LiteralPath $path) {
        if ($line -match '^\s*#' -or $line -notmatch '^\s*([^=\s]+)=(.*)$') {
            continue
        }
        $values[$Matches[1]] = $Matches[2].Trim()
    }
    return $values
}

function Assert-EnvValue {
    param(
        [hashtable]$Values,
        [string]$Name,
        [string]$Expected
    )
    if (-not $Values.ContainsKey($Name)) {
        Add-Failure "Missing .env value: $Name"
        return
    }
    if ($Values[$Name] -ne $Expected) {
        Add-Failure "$Name does not match the offline package contract"
    }
}

Write-Host "Validating offline package: $root"

@(
    '.env',
    'docker-compose.yml',
    'DEPLOY-OFFLINE.md',
    'CHECKSUMS.txt',
    'openldap/bootstrap/01-base-ou.ldif',
    'data/mysql/.gitkeep',
    'data/openldap/config/.gitkeep',
    'data/openldap/database/.gitkeep',
    'data/rabbitmq/.gitkeep',
    'imports/.gitkeep',
    'images/corp-idm-platform-internal.tar',
    'images/corp-idm-web-internal.tar',
    'images/mysql-8.0.tar',
    'images/openldap-1.5.0.tar',
    'images/rabbitmq-4.2-management.tar'
) | ForEach-Object { Require-File $_ }

$envFile = Join-Path $root '.env'
if (Test-Path -LiteralPath $envFile -PathType Leaf) {
    $envValues = Read-EnvFile
    Assert-EnvValue $envValues 'WEB_EXPOSE_PORT' '80'
    Assert-EnvValue $envValues 'APP_EXPOSE_PORT' '8081'
    Assert-EnvValue $envValues 'MYSQL_EXPOSE_PORT' '3307'
    Assert-EnvValue $envValues 'LDAP_EXPOSE_PORT' '389'
    Assert-EnvValue $envValues 'APP_LDAP_URL' 'ldap://openldap:389'
    Assert-EnvValue $envValues 'APP_LDAP_BIND_DN' 'cn=admin,dc=corp,dc=local'
    Assert-EnvValue $envValues 'LDAP_DOMAIN' 'corp.local'
    Assert-EnvValue $envValues 'RABBITMQ_IMAGE' 'rabbitmq:4.2-management'
    Assert-EnvValue $envValues 'RABBITMQ_EXPOSE_PORT' '5672'
    Assert-EnvValue $envValues 'RABBITMQ_MGMT_EXPOSE_PORT' '15672'

    if (-not $envValues.ContainsKey('RABBITMQ_USER') -or [string]::IsNullOrWhiteSpace($envValues['RABBITMQ_USER'])) {
        Add-Failure 'RABBITMQ_USER is empty'
    }
    if (-not $envValues.ContainsKey('RABBITMQ_PASSWORD') -or [string]::IsNullOrWhiteSpace($envValues['RABBITMQ_PASSWORD'])) {
        Add-Failure 'RABBITMQ_PASSWORD is empty'
    }

    if (-not $envValues.ContainsKey('LDAP_ADMIN_PASSWORD') -or [string]::IsNullOrWhiteSpace($envValues['LDAP_ADMIN_PASSWORD'])) {
        Add-Failure 'LDAP_ADMIN_PASSWORD is empty'
    }
    if (-not $envValues.ContainsKey('APP_LDAP_BIND_PASSWORD') -or [string]::IsNullOrWhiteSpace($envValues['APP_LDAP_BIND_PASSWORD'])) {
        Add-Failure 'APP_LDAP_BIND_PASSWORD is empty'
    }
    if ($envValues.ContainsKey('LDAP_ADMIN_PASSWORD') -and $envValues.ContainsKey('APP_LDAP_BIND_PASSWORD') -and
        $envValues['LDAP_ADMIN_PASSWORD'] -ne $envValues['APP_LDAP_BIND_PASSWORD']) {
        Add-Failure 'LDAP_ADMIN_PASSWORD and APP_LDAP_BIND_PASSWORD are different'
    }
}

$ldifPath = Join-Path $root 'openldap/bootstrap/01-base-ou.ldif'
if (Test-Path -LiteralPath $ldifPath -PathType Leaf) {
    $ldif = Get-Content -Raw -LiteralPath $ldifPath
    if ($ldif -notmatch '(?m)^dn:\s*uid=admin,ou=people,dc=corp,dc=local\s*$') {
        Add-Failure 'Bootstrap LDIF does not contain the admin DN'
    }
    if ($ldif -notmatch '(?m)^userPassword:\s*123456\s*$') {
        Add-Failure 'Bootstrap LDIF does not contain the required initial admin password'
    }
    if ($ldif -notmatch '(?m)^employeeType:\s*ENABLED\s*$') {
        Add-Failure 'Bootstrap LDIF admin entry is not enabled'
    }
}

foreach ($relativeDirectory in @('data/mysql', 'data/openldap/config', 'data/openldap/database', 'data/rabbitmq')) {
    $directory = Join-Path $root $relativeDirectory
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
        Add-Failure "Missing data directory: $relativeDirectory"
        continue
    }
    $runtimeFiles = Get-ChildItem -LiteralPath $directory -Recurse -Force -File |
        Where-Object { $_.Name -ne '.gitkeep' }
    if ($runtimeFiles) {
        Add-Failure "Runtime data exists in clean package directory: $relativeDirectory"
    }
}

$checksumPath = Join-Path $root 'CHECKSUMS.txt'
if (Test-Path -LiteralPath $checksumPath -PathType Leaf) {
    $checksumEntries = 0
    foreach ($line in Get-Content -LiteralPath $checksumPath) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        if ($line -notmatch '^([0-9a-fA-F]{64})\s+(.+)$') {
            Add-Failure 'CHECKSUMS.txt contains an invalid line'
            continue
        }
        $checksumEntries++
        $expectedHash = $Matches[1].ToLowerInvariant()
        $relativePath = $Matches[2].Trim()
        $filePath = Join-Path $root $relativePath
        if (-not (Test-Path -LiteralPath $filePath -PathType Leaf)) {
            Add-Failure "Checksum target is missing: $relativePath"
            continue
        }
        $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $filePath).Hash.ToLowerInvariant()
        if ($actualHash -ne $expectedHash) {
            Add-Failure "SHA256 mismatch: $relativePath"
        }
    }
    if ($checksumEntries -eq 0) {
        Add-Failure 'CHECKSUMS.txt contains no checksum entries'
    }
}

$composePath = Join-Path $root 'docker-compose.yml'
$composeText = Get-Content -Raw -LiteralPath $composePath
if ($composeText -match '(?m)\b(?:10|172\.(?:1[6-9]|2\d|3[01])|192\.168)\.') {
    Add-Failure 'docker-compose.yml contains a private server IP; use Compose service names'
}

if (Get-Command docker -ErrorAction SilentlyContinue) {
    & docker compose --project-directory $root --env-file $envFile -f $composePath config --quiet
    if ($LASTEXITCODE -ne 0) {
        Add-Failure 'docker compose config failed'
    }
    else {
        $services = @(& docker compose --project-directory $root --env-file $envFile -f $composePath config --services)
        foreach ($service in @('mysql', 'openldap', 'rabbitmq', 'idm-app', 'idm-web')) {
            if ($services -notcontains $service) {
                Add-Failure "Missing Compose service: $service"
            }
        }
    }

    foreach ($image in @(
        'corp-idm-platform:internal',
        'corp-idm-web:internal',
        'mysql:8.0',
        'osixia/openldap:1.5.0',
        'rabbitmq:4.2-management'
    )) {
        & docker image inspect $image *> $null
        if ($LASTEXITCODE -ne 0) {
            Add-Failure "Docker image is not loaded: $image"
        }
    }
}
else {
    Add-Failure 'docker command is not available'
}

if ($errors.Count -eq 0) {
    Write-Host 'OFFLINE PACKAGE VALIDATION: PASS' -ForegroundColor Green
    exit 0
}

Write-Host 'OFFLINE PACKAGE VALIDATION: FAIL' -ForegroundColor Red
$errors | ForEach-Object { Write-Host "- $_" -ForegroundColor Red }
exit 1
