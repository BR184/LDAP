# 带版本追踪的打包脚本
# 用途：编译后端并自动记录版本信息到 build-versions.txt

param(
    [switch]$SkipTests = $true
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir

Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "  Corp IDM Platform - Build Script" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""

# 1. 检查 Git 仓库状态
Write-Host "[1/5] Checking Git repository..." -ForegroundColor Yellow
Set-Location $projectRoot

if (-not (Test-Path ".git")) {
    Write-Host "ERROR: Not a git repository!" -ForegroundColor Red
    exit 1
}

$gitStatus = git status --porcelain
if ($gitStatus) {
    Write-Host "WARNING: You have uncommitted changes:" -ForegroundColor Yellow
    git status --short
    Write-Host ""
}

# 2. 执行 Maven 打包
Write-Host "[2/5] Building with Maven..." -ForegroundColor Yellow
$mvnCmd = ".tools\apache-maven-3.9.6\bin\mvn.cmd"

if ($SkipTests) {
    & $mvnCmd clean package -DskipTests
} else {
    & $mvnCmd clean package
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Maven build failed!" -ForegroundColor Red
    exit 1
}

Write-Host "Build successful!" -ForegroundColor Green
Write-Host ""

# 3. 收集版本信息
Write-Host "[3/5] Collecting version information..." -ForegroundColor Yellow

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$commitHash = git rev-parse HEAD
$commitShort = git rev-parse --short=7 HEAD

# 获取 JAR 文件名（从 pom.xml 读取）
$pomXml = [xml](Get-Content "pom.xml")
$artifactId = $pomXml.project.artifactId
$version = $pomXml.project.version
$jarFile = "$artifactId-$version.jar"

# 获取最新的 Flyway 版本
$migrationFiles = Get-ChildItem "src/main/resources/db/migration/V*.sql" | Sort-Object Name -Descending
if ($migrationFiles) {
    $latestMigration = $migrationFiles[0].BaseName
    if ($latestMigration -match '^(V\d+)__') {
        $dbVersion = $matches[1]
    } else {
        $dbVersion = "unknown"
    }
} else {
    $dbVersion = "none"
}

Write-Host "  Timestamp:    $timestamp" -ForegroundColor Gray
Write-Host "  Commit:       $commitHash ($commitShort)" -ForegroundColor Gray
Write-Host "  JAR File:     $jarFile" -ForegroundColor Gray
Write-Host "  DB Version:   $dbVersion" -ForegroundColor Gray
Write-Host ""

# 4. 记录到 build-versions.txt
Write-Host "[4/5] Recording to build-versions.txt..." -ForegroundColor Yellow

$record = "[$timestamp] commit: $commitHash | jar: $jarFile | db: $dbVersion"
Add-Content -Path "build-versions.txt" -Value $record

Write-Host "Version record added successfully!" -ForegroundColor Green
Write-Host ""

# 5. 显示构建信息摘要
Write-Host "[5/5] Build Summary" -ForegroundColor Yellow
Write-Host "=====================================" -ForegroundColor Cyan

$jarPath = "target\$jarFile"
if (Test-Path $jarPath) {
    $jarSize = (Get-Item $jarPath).Length / 1MB
    Write-Host "  JAR Location: $jarPath" -ForegroundColor White
    Write-Host "  JAR Size:     $([math]::Round($jarSize, 2)) MB" -ForegroundColor White
} else {
    Write-Host "  WARNING: JAR file not found at $jarPath" -ForegroundColor Yellow
}

Write-Host "  Git Commit:   $commitShort" -ForegroundColor White
Write-Host "  DB Version:   $dbVersion" -ForegroundColor White
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Build completed successfully! ✓" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  - Run locally:  java -jar $jarPath --spring.profiles.active=dev" -ForegroundColor Gray
Write-Host "  - Deploy:       Copy $jarPath to target server" -ForegroundColor Gray
Write-Host ""
