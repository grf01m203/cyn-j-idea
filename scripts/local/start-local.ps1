<#
.SYNOPSIS
    CampusVenue 本地一键启动（可移植版）：MySQL(独立实例) -> Spring Boot -> 管理端 -> 学生端 H5

.DESCRIPTION
    不依赖任何绝对路径：数据、日志、PID 全部放在仓库根的 .local/ 目录（已 gitignore）。
    需要本机已安装：JDK 17+、Maven（缺 jar 时用）、Node 18+、MySQL 8（mysqld.exe）。

.PARAMETER DbPort
    独立 MySQL 实例端口，默认 3307（避免与系统已有的 3306 冲突）。

.PARAMETER ResetDb
    重建库：执行 docs/sql/V1、V2、V4（全新库无需 V3，它是旧库 ALTER 迁移）。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\local\start-local.ps1 -ResetDb
    powershell -ExecutionPolicy Bypass -File scripts\local\start-local.ps1 -SkipFrontend
#>
[CmdletBinding()]
param(
    [int]$DbPort = 3307,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$MySqlBase = '',
    [switch]$ResetDb,
    [switch]$SkipMysql,
    [switch]$SkipBackend,
    [switch]$SkipFrontend
)

$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$ServerDir = Join-Path $RepoRoot 'campus-venue-server'
$AdminDir  = Join-Path $RepoRoot 'campus-venue-admin'
$UniDir    = Join-Path $RepoRoot 'campus-venue-uniapp'
$SqlDir    = Join-Path $RepoRoot 'docs\sql'
$Jar       = Join-Path $ServerDir 'target\campus-venue-server.jar'

$LocalDir = Join-Path $RepoRoot '.local'
$RunDir   = Join-Path $LocalDir 'run'
$LogDir   = Join-Path $LocalDir 'logs'
$DataDir  = Join-Path $LocalDir 'mysql\data'
$IniPath  = Join-Path $LocalDir 'mysql\my.ini'
$BootSql  = Join-Path $LocalDir 'mysql\bootstrap.sql'

New-Item -ItemType Directory -Force -Path $RunDir, $LogDir, (Split-Path $DataDir -Parent) | Out-Null

function Test-Port([int]$Port) {
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
}

function Wait-Port([int]$Port, [int]$Seconds, [string]$Name) {
    for ($i = 0; $i -lt $Seconds; $i++) {
        if (Test-Port $Port) { Write-Host ("  [OK]   {0} 已监听 {1}" -f $Name, $Port) -ForegroundColor Green; return $true }
        Start-Sleep -Seconds 1
    }
    Write-Host ("  [FAIL] {0} 在 {1}s 内未监听 {2}，日志见 {3}" -f $Name, $Seconds, $Port, $LogDir) -ForegroundColor Red
    return $false
}

function Start-Detached([string]$FilePath, [string]$Arguments, [string]$WorkDir) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $FilePath
    $psi.Arguments = $Arguments
    $psi.WorkingDirectory = $WorkDir
    $psi.UseShellExecute = $true
    $psi.WindowStyle = 'Hidden'
    return [System.Diagnostics.Process]::Start($psi)
}

function Save-Pid([string]$Name, $Process) {
    if ($Process) { Set-Content -Path (Join-Path $RunDir "$Name.pid") -Value $Process.Id -Encoding ASCII }
}

function Start-NodeApp([string]$Name, [string]$WorkDir, [string]$Script, [int]$Port, [int]$WaitSeconds) {
    $log = Join-Path $LogDir "$Name.log"
    $wrap = Join-Path $RunDir "$Name.cmd"
    $body = "@echo off`r`ncd /d `"$WorkDir`"`r`nset VITE_API_TARGET=http://127.0.0.1:8080`r`ncall npm $Script > `"$log`" 2>&1`r`n"
    Set-Content -Path $wrap -Value $body -Encoding ASCII
    $p = Start-Detached -FilePath 'cmd.exe' -Arguments "/c `"$wrap`"" -WorkDir $WorkDir
    Save-Pid $Name $p
    return Wait-Port $Port $WaitSeconds $Name
}

Write-Host "`n=== CampusVenue 本地启动（可移植版）===`n" -ForegroundColor Cyan
Write-Host "  仓库根：$RepoRoot"
Write-Host "  运行目录：$LocalDir`n"

# ---------------------------------------------------------------- 0. 依赖探测
$JavaExe = $null
if (-not $SkipBackend) {
    if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin\java.exe'))) {
        $cand = @()
        if ($JavaHome) { $cand += $JavaHome }
        $cand += (Get-ChildItem 'C:\Program Files\Java' -Directory -Filter 'jdk*' -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | ForEach-Object { $_.FullName })
        $JavaHome = $cand | Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin\java.exe')) } | Select-Object -First 1
    }
    if (-not $JavaHome) { throw "未找到 JDK 17+，请安装后用 -JavaHome 指定（或设置 JAVA_HOME）" }
    $JavaExe = Join-Path $JavaHome 'bin\java.exe'
}

if (-not $SkipMysql -and -not $MySqlBase) {
    $cand = @(
        (Get-ChildItem 'C:\Program Files\MySQL' -Directory -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
        (Get-ChildItem 'D:\Program Files\MySQL', 'D:\mysql' -Directory -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
    )
    $MySqlBase = $cand | Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin\mysqld.exe')) } | Select-Object -First 1
    if (-not $MySqlBase) { throw "未找到 MySQL（mysqld.exe），请用 -MySqlBase 指定安装目录，例如 'C:\Program Files\MySQL\MySQL Server 8.0'" }
}
$MysqldExe = if ($MySqlBase) { Join-Path $MySqlBase 'bin\mysqld.exe' } else { $null }
$MysqlExe  = if ($MySqlBase) { Join-Path $MySqlBase 'bin\mysql.exe' } else { $null }

# ---------------------------------------------------------------- 1. MySQL
if (-not $SkipMysql) {
    if (Test-Port $DbPort) {
        Write-Host "  [SKIP] MySQL $DbPort 已在运行"
    } else {
        Write-Host "  [..]   准备独立 MySQL 实例（端口 $DbPort，数据目录 .local\mysql\data）"
        $ini = @"
[mysqld]
basedir=$($MySqlBase -replace '\\','/')
datadir=$($DataDir -replace '\\','/')
port=$DbPort
bind-address=127.0.0.1
mysqlx=0
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
max_connections=200
log-error=$(($LogDir -replace '\\','/'))/mysql.err.log
pid-file=$(($LocalDir -replace '\\','/'))/mysql.pid

[client]
port=$DbPort
host=127.0.0.1
default-character-set=utf8mb4
"@
        Set-Content -Path $IniPath -Value $ini -Encoding ASCII

        $boot = @"
CREATE USER IF NOT EXISTS 'campus'@'%' IDENTIFIED BY 'Campus@2026';
CREATE USER IF NOT EXISTS 'campus'@'localhost' IDENTIFIED BY 'Campus@2026';
ALTER USER 'campus'@'%' IDENTIFIED BY 'Campus@2026';
ALTER USER 'campus'@'localhost' IDENTIFIED BY 'Campus@2026';
GRANT ALL PRIVILEGES ON *.* TO 'campus'@'%' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON *.* TO 'campus'@'localhost' WITH GRANT OPTION;
ALTER USER 'root'@'localhost' IDENTIFIED BY 'CampusRoot2026';
FLUSH PRIVILEGES;
"@
        Set-Content -Path $BootSql -Value $boot -Encoding ASCII

        if (-not (Test-Path (Join-Path $DataDir 'mysql'))) {
            Write-Host "  [..]   初始化数据目录（首次运行）"
            $initLog = Join-Path $LogDir 'mysql-init.log'
            $prev = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'   # mysqld 把进度写到 stderr，Stop 会误判为失败
            & $MysqldExe "--defaults-file=$($IniPath -replace '\\','/')" --initialize-insecure --console *> $initLog
            $initCode = $LASTEXITCODE
            $ErrorActionPreference = $prev
            if ($initCode -ne 0 -or -not (Test-Path (Join-Path $DataDir 'mysql'))) {
                throw "MySQL 初始化失败（exit=$initCode），详见 $initLog"
            }
        }

        $p = Start-Detached -FilePath $MysqldExe `
            -Arguments "--defaults-file=$($IniPath -replace '\\','/') --init-file=$($BootSql -replace '\\','/')" `
            -WorkDir $MySqlBase
        Save-Pid 'mysql' $p
        if (-not (Wait-Port $DbPort 60 'MySQL')) { exit 1 }
    }
}

# ---------------------------------------------------------------- 2. 重置库（可选）
if ($ResetDb) {
    Write-Host "  [..]   重置数据库（V1 建表 + V2 种子 + V4 参数默认值）"
    $env:MYSQL_PWD = 'Campus@2026'
    $scripts = @('V1__init.sql', 'V2__seed.sql')
    if (Test-Path (Join-Path $SqlDir 'V4__config_defaults.sql')) { $scripts += 'V4__config_defaults.sql' }
    foreach ($f in $scripts) {
        $path = Join-Path $SqlDir $f
        # V1 会自己 CREATE DATABASE，连接时不能预选库；V2/V4 才需要指定 campus_venue
        $dbArg = if ($f -eq 'V1__init.sql') { '' } else { 'campus_venue' }
        $prev = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        cmd /c "`"$MysqlExe`" --host=127.0.0.1 --port=$DbPort --user=campus --protocol=TCP --default-character-set=utf8mb4 $dbArg < `"$path`"" 2>&1 | Out-Null
        $code = $LASTEXITCODE
        $ErrorActionPreference = $prev
        if ($code -ne 0) { throw "$f 执行失败（exit=$code）" }
    }
    $env:MYSQL_PWD = $null
    Write-Host "  [OK]   数据库已重置（3 场馆 / 7 场地 / 49 规则 / 4 演示账号）" -ForegroundColor Green
}

# ---------------------------------------------------------------- 3. 后端
if (-not $SkipBackend) {
    if (Test-Port 8080) {
        Write-Host "  [SKIP] 后端 8080 已在运行"
    } else {
        if (-not (Test-Path $Jar)) {
            Write-Host "  [..]   未找到 jar，使用 Maven 打包"
            Push-Location $ServerDir
            & mvn -B -DskipTests package
            Pop-Location
            if (-not (Test-Path $Jar)) { throw "打包失败" }
        }
        Write-Host "  [..]   启动 Spring Boot（8080，连 $DbPort）"
        $dbUrl = "jdbc:mysql://127.0.0.1:$DbPort/campus_venue?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true"
        $args = "-jar `"$Jar`" --spring.profiles.active=dev --spring.datasource.url=$dbUrl --logging.file.name=`"$LogDir\backend.log`""
        $p = Start-Detached -FilePath $JavaExe -Arguments $args -WorkDir $ServerDir
        Save-Pid 'backend' $p

        $healthy = $false
        for ($i = 0; $i -lt 120; $i++) {
            try {
                $h = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 2
                if ($h.status -eq 'UP') { $healthy = $true; break }
            } catch { }
            Start-Sleep -Seconds 1
        }
        if ($healthy) { Write-Host "  [OK]   后端健康检查 UP" -ForegroundColor Green }
        else { Write-Host "  [FAIL] 后端未就绪，见 $LogDir\backend.log" -ForegroundColor Red }
    }
}

# ---------------------------------------------------------------- 4. 前端
if (-not $SkipFrontend) {
    if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) { throw "未找到 npm，请安装 Node 18+ 并加入 PATH" }
    if ((Test-Port 5173)) { Write-Host "  [SKIP] 管理端 5173 已在运行" }
    else {
        Write-Host "  [..]   启动管理端（5173）"
        Start-NodeApp -Name 'admin' -WorkDir $AdminDir -Script 'run dev' -Port 5173 -WaitSeconds 180 | Out-Null
    }
    if ((Test-Port 5273)) { Write-Host "  [SKIP] 学生端 5273 已在运行" }
    else {
        Write-Host "  [..]   启动学生端 H5（5273）"
        Start-NodeApp -Name 'uniapp' -WorkDir $UniDir -Script 'run dev:h5' -Port 5273 -WaitSeconds 240 | Out-Null
    }
}

# ---------------------------------------------------------------- 汇总
$lan = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254*' } |
    Select-Object -First 1).IPAddress
Write-Host "`n=== 访问地址 ===" -ForegroundColor Cyan
Write-Host "  学生端 H5      http://127.0.0.1:5273"
Write-Host "  管理端控制台    http://127.0.0.1:5173"
Write-Host "  后端 API        http://127.0.0.1:8080   （/doc.html 接口文档，/actuator/health 健康检查）"
if ($lan) { Write-Host "  局域网          http://${lan}:5273  /  http://${lan}:5173" -ForegroundColor Yellow }
Write-Host "`n  演示账号（密码 123456）：student / student2 / admin / sysadmin"
Write-Host "  日志：.local\logs    停止：scripts\local\stop-local.ps1`n"
