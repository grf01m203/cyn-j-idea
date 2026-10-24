# =============================================================================
# 用 Newman 运行 CampusVenue 的 Postman 接口测试集合
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-postman.ps1
#   powershell -File scripts/run-postman.ps1 -BaseUrl http://127.0.0.1:8081
#   powershell -File scripts/run-postman.ps1 -ReportPath D:\tmp\report.json
# 说明：
#   - 首次运行会把 newman 安装到 %LOCALAPPDATA%\CampusVenue\postman-tools（不污染仓库）
#   - 集合会自动登录、写入 token/ID 环境变量，按顺序执行，不要只跑单个文件夹
# =============================================================================
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$ReportPath = '',
    [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$collection = Join-Path $root 'docs\postman\CampusVenue.postman_collection.json'
$environment = Join-Path $root 'docs\postman\CampusVenue.local.postman_environment.json'

if (-not (Test-Path $collection)) {
    throw "未找到 Postman 集合：$collection"
}
if (-not (Test-Path $environment)) {
    throw "未找到 Postman 环境文件：$environment"
}

# 1) 后端健康检查（失败只提示，不阻断，便于排查）
$healthy = $false
try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 5 -UseBasicParsing
    $healthy = ($health.status -eq 'UP')
} catch {
    $healthy = $false
}
if ($healthy) {
    Write-Host "[准备] 后端健康检查通过：$BaseUrl" -ForegroundColor Green
} else {
    Write-Host "[警告] 后端健康检查失败：$BaseUrl（请先启动后端，否则集合会大量失败）" -ForegroundColor Yellow
}

# 2) 安装 / 定位 Newman
$toolsDir = Join-Path $env:LOCALAPPDATA 'CampusVenue\postman-tools'
$newman = Join-Path $toolsDir 'node_modules\newman\bin\newman.js'
if (-not (Test-Path $newman)) {
    if ($SkipInstall) {
        throw "Newman 未安装：$newman（去掉 -SkipInstall 让它自动安装）"
    }
    if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
        throw '未找到 npm，请先安装 Node.js。'
    }
    Write-Host '[1/2] 安装 Newman（首次运行，安装到 LOCALAPPDATA）...' -ForegroundColor Cyan
    New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
    & npm install --prefix $toolsDir newman@6 --no-audit --no-fund --registry=https://registry.npmmirror.com
    if ($LASTEXITCODE -ne 0) {
        throw 'Newman 安装失败，请检查网络或 npm 镜像配置。'
    }
}

# 3) 报告路径
if ([string]::IsNullOrWhiteSpace($ReportPath)) {
    $ReportPath = Join-Path $env:TEMP ('campus-venue-postman-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.json')
}
$reportDir = Split-Path -Parent $ReportPath
if ($reportDir -and -not (Test-Path $reportDir)) {
    New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
}

Write-Host '[2/2] 运行 Postman 集合（Newman）...' -ForegroundColor Cyan
Write-Host ("      集合：{0}" -f $collection)
Write-Host ("      环境：{0}" -f $environment)
Write-Host ("      地址：{0}" -f $BaseUrl)

& node $newman run $collection `
    -e $environment `
    --env-var ("baseUrl=" + $BaseUrl) `
    --reporters cli,json `
    --reporter-json-export $ReportPath
$exitCode = $LASTEXITCODE

Write-Host ''
if ($exitCode -eq 0) {
    Write-Host '[结果] 全部断言通过' -ForegroundColor Green
} else {
    Write-Host '[结果] 存在失败请求，请查看 JSON 报告' -ForegroundColor Red
}
Write-Host ("[报告] {0}" -f $ReportPath)
exit $exitCode
