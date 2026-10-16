# =============================================================================
# 一键启动 CampusVenue Spring Boot 后端（本项目唯一的后端实现，无任何 mock 后端）
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-backend.ps1
#   powershell -File scripts/start-backend.ps1 -Port 8081        # 换端口
#   powershell -File scripts/start-backend.ps1 -SkipBuild        # 跳过打包
# =============================================================================
param(
    [int]$Port = 8080,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$serverDir = Join-Path $root 'campus-venue-server'
$jar = Join-Path $serverDir 'target\campus-venue-server.jar'

Write-Host "=== CampusVenue 后端启动（Spring Boot 3.2 / JDK 17）===" -ForegroundColor Cyan

# 1) 检查端口占用者是不是本项目后端
$listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) {
    $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    if ($proc.CommandLine -match 'campus-venue-server\.jar') {
        Write-Host "端口 $Port 上已经在运行本项目 Spring Boot 后端（pid=$($listener.OwningProcess)），无需重复启动。" -ForegroundColor Yellow
        Write-Host "健康检查： http://localhost:$Port/actuator/health"
        exit 0
    }
    Write-Host "端口 $Port 被其它进程占用：$($proc.Name) :: $($proc.CommandLine)" -ForegroundColor Red
    Write-Host "请先停止该进程，或用 -Port 8081 指定其它端口（同时把前端 VITE_API_TARGET 指向新端口）。"
    exit 1
}

# 2) 打包（缺 jar 或未指定 -SkipBuild 时）
if (-not $SkipBuild -or -not (Test-Path $jar)) {
    Write-Host "[1/2] Maven 打包（跳过测试）..." -ForegroundColor Cyan
    Push-Location $serverDir
    try { & mvn -B -ntp -DskipTests package | Select-Object -Last 5 }
    finally { Pop-Location }
}

if (-not (Test-Path $jar)) { throw "未找到 $jar，打包失败" }

# 3) 启动 Spring Boot
Write-Host "[2/2] 启动 Spring Boot：java -jar $jar --spring.profiles.active=dev --server.port=$Port" -ForegroundColor Cyan
Push-Location $serverDir
try {
    & java -jar $jar --spring.profiles.active=dev --server.port=$Port
}
finally { Pop-Location }
