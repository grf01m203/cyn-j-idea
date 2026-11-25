<#
.SYNOPSIS
    停止 start-local.ps1 启动的全部组件（按端口精确结束进程）
#>
[CmdletBinding()]
param(
    [int]$DbPort = 3307,
    [int]$ApiPort = 8080,
    [int[]]$FrontendPorts = @(5173, 5273)
)

$RepoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$RunDir = Join-Path $RepoRoot '.local\run'

function Stop-Port([int]$Port, [string]$Name) {
    $conns = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
    if (-not $conns) { Write-Host "  [--]   $Name（$Port）未在运行"; return }
    foreach ($procId in ($conns.OwningProcess | Select-Object -Unique)) {
        cmd /c "taskkill /PID $procId /T /F" | Out-Null
        Write-Host ("  [OK]   已停止 {0}（PID {1}）" -f $Name, $procId) -ForegroundColor Green
    }
}

Write-Host "`n=== 停止 CampusVenue 本地环境 ===`n" -ForegroundColor Cyan
foreach ($p in $FrontendPorts) { Stop-Port $p "前端" }
Stop-Port $ApiPort 'Spring Boot'
Stop-Port $DbPort 'MySQL（独立实例）'
Get-ChildItem $RunDir -Filter *.pid -ErrorAction SilentlyContinue | Remove-Item -Force
Write-Host "`n  （系统原有的 MySQL 3306 未受影响）`n"
