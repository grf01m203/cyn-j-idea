# =============================================================================
# CampusVenue 端到端验收脚本（兼容 Windows PowerShell 5.1 / PowerShell 7）
# 覆盖：登录 / 场馆 / 时段 / 预约防超卖(含并发) / 取消 / 候补递补 / 拼场 / 签到 / 消息 / 统计 / 权限
# 用法：powershell -NoProfile -File scripts/smoke-test.ps1 -Base http://localhost:8081
# =============================================================================
param(
    [string]$Base = "http://localhost:8081"
)

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$script:pass = 0
$script:fail = 0

function Call-Api {
    param([string]$Method, [string]$Path, $Body, [string]$Token)
    $headers = @{}
    if ($Token) { $headers['Authorization'] = $Token }
    $params = @{ Method = $Method; Uri = "$Base$Path"; Headers = $headers; TimeoutSec = 20; UseBasicParsing = $true }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 6 -Compress
        $params['ContentType'] = 'application/json; charset=utf-8'
        $params['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    try {
        return Invoke-RestMethod @params
    } catch {
        return [pscustomobject]@{ code = -1; message = $_.Exception.Message; data = $null }
    }
}

function Check {
    param([string]$Name, [scriptblock]$Body)
    try {
        $result = & $Body
        if ($result -eq $false) { throw "断言失败" }
        Write-Host ("[PASS] " + $Name) -ForegroundColor Green
        $script:pass++
    } catch {
        Write-Host ("[FAIL] " + $Name + " -> " + $_.Exception.Message) -ForegroundColor Red
        $script:fail++
    }
}

function Info($text) { Write-Host ("       " + $text) -ForegroundColor DarkGray }

Write-Host "=== CampusVenue 端到端验收 @ $Base ===" -ForegroundColor Cyan

# ---------------------------------------------------------------- 1. 登录 ----
$student = $null; $student2 = $null; $admin = $null
Check "1.1 学生 student 登录（BCrypt 校验 + Sa-Token 发放）" {
    $r = Call-Api POST "/api/v1/auth/login" @{ username = 'student'; password = '123456' }
    if ($r.code -ne 0) { throw $r.message }
    $script:student = $r.data
    Info ("token=" + $r.data.token.Substring(0, 8) + "...  role=" + $r.data.user.role + "  credit=" + $r.data.user.creditScore)
    return ($r.data.user.role -eq 'STUDENT')
}
Check "1.2 学生 student2 登录" {
    $r = Call-Api POST "/api/v1/auth/login" @{ username = 'student2'; password = '123456' }
    if ($r.code -ne 0) { throw $r.message }
    $script:student2 = $r.data
    return $true
}
Check "1.3 管理员 admin 登录且角色为 VENUE_ADMIN" {
    $r = Call-Api POST "/api/v1/auth/login" @{ username = 'admin'; password = '123456' }
    if ($r.code -ne 0) { throw $r.message }
    $script:admin = $r.data
    return ($r.data.user.role -eq 'VENUE_ADMIN')
}
Check "1.4 错误密码返回 20001 而不是 500" {
    $r = Call-Api POST "/api/v1/auth/login" @{ username = 'student'; password = 'wrong-password' }
    return ($r.code -eq 20001)
}
Check "1.5 未携带 token 访问受保护接口返回 20001" {
    $r = Call-Api GET "/api/v1/auth/me" $null ""
    return ($r.code -eq 20001)
}

$tok = $student.token
$tok2 = $student2.token
$tokAdmin = $admin.token

# ------------------------------------------------------------ 2. 场馆/时段 ----
$courtId = 3
Check "2.1 场馆分页查询返回数据" {
    $r = Call-Api GET "/api/v1/venues?pageNum=1&pageSize=10" $null $tok
    if ($r.code -ne 0) { throw $r.message }
    Info ("场馆数=" + $r.data.total + " 首个=" + $r.data.records[0].name + " 场地数=" + $r.data.records[0].courtCount)
    return ($r.data.total -ge 3)
}
Check "2.2 场馆下场地列表" {
    $r = Call-Api GET "/api/v1/venues/1/courts" $null $tok
    if ($r.code -ne 0) { throw $r.message }
    Info ("场地=" + (($r.data | ForEach-Object { $_.name }) -join ', '))
    return ($r.data.Count -ge 4)
}

$tomorrow = (Get-Date).AddDays(1).ToString('yyyy-MM-dd')
$today = (Get-Date).ToString('yyyy-MM-dd')
$freeSlots = $null
Check "2.3 当日时段视图返回 48 个 30 分钟槽位（三态）" {
    $r = Call-Api GET "/api/v1/courts/$courtId/slots?date=$tomorrow" $null $tok
    if ($r.code -ne 0) { throw $r.message }
    $script:freeSlots = $r.data.slots
    $free = ($r.data.slots | Where-Object { $_.status -eq 0 }).Count
    $unavailable = ($r.data.slots | Where-Object { $_.status -eq 2 }).Count
    Info ("槽位总数=" + $r.data.slots.Count + " 空闲=" + $free + " 不可用=" + $unavailable)
    return ($r.data.slots.Count -eq 48 -and $free -gt 0)
}

# 选一个连续 3 槽（1.5 小时）的空闲窗口
$window = $null
for ($i = 0; $i -lt 40; $i++) {
    $a = $freeSlots[$i]; $b = $freeSlots[$i + 1]; $c = $freeSlots[$i + 2]
    if ($a.status -eq 0 -and $b.status -eq 0 -and $c.status -eq 0) {
        $window = @{ startSlot = $a.slotNo; endSlot = $c.slotNo + 1; label = $a.label }
        break
    }
}
Check "2.4 找到连续 3 个空闲槽位用于预约" {
    Info ("窗口 " + $window.label + " (slot " + $window.startSlot + "~" + $window.endSlot + ")")
    return ($null -ne $window)
}
$s1 = $window.startSlot; $e1 = $window.endSlot

# ---------------------------------------------------------------- 3. 预约 ----
$resvId = $null
Check "3.1 创建预约：前端 -> 后端 -> 数据库（收费场地为待支付）" {
    $r = Call-Api POST "/api/v1/reservations" @{ courtId = $courtId; resvDate = $tomorrow; startSlot = $s1; endSlot = $e1; requestId = "SMOKE-$([guid]::NewGuid())"; remark = '端到端验收' } $tok
    if ($r.code -ne 0) { throw $r.message }
    $script:resvId = $r.data.id
    Info ("单号=" + $r.data.resvNo + " 状态=" + $r.data.status + " 金额=" + $r.data.amount + " (0待支付/1已确认)")
    return ($r.data.id -gt 0)
}
Check "3.2 同一 requestId 重复提交幂等（返回同一单）" {
    $rid = "IDEMP-" + [guid]::NewGuid()
    $a = Call-Api POST "/api/v1/reservations" @{ courtId = $courtId; resvDate = $tomorrow; startSlot = $s1; endSlot = $e1; requestId = $rid } $tok
    $b = Call-Api POST "/api/v1/reservations" @{ courtId = $courtId; resvDate = $tomorrow; startSlot = $s1; endSlot = $e1; requestId = $rid } $tok
    return ($a.data.id -eq $b.data.id)
}
Check "3.3 时段冲突被唯一索引拦截（返回 30001）" {
    $r = Call-Api POST "/api/v1/reservations" @{ courtId = $courtId; resvDate = $tomorrow; startSlot = $s1; endSlot = $e1; requestId = "CONFLICT-" + [guid]::NewGuid() } $tok2
    Info ("返回 code=" + $r.code + " message=" + $r.message)
    return ($r.code -eq 30001)
}
Check "3.4 模拟支付后预约状态变为已确认（1）" {
    $r = Call-Api POST "/api/v1/reservations/$resvId/pay-mock" @{} $tok
    if ($r.code -ne 0) { throw $r.message }
    Info ("状态=" + $r.data.status)
    return ($r.data.status -eq 1)
}
Check "3.5 我的预约列表可查到该单" {
    $r = Call-Api GET "/api/v1/reservations/my?pageNum=1&pageSize=10" $null $tok
    if ($r.code -ne 0) { throw $r.message }
    $hit = $r.data.records | Where-Object { $_.id -eq $resvId }
    Info ("共 " + $r.data.total + " 条，命中=" + [bool]$hit)
    return ($null -ne $hit)
}

# ---------------------------------------------------- 4. 并发抢约（防超卖）----
Check "4.1 10 并发抢同一时段：只能成功 1 单（数据库唯一索引兜底）" {
    $freeSlots2 = (Call-Api GET "/api/v1/courts/4/slots?date=$tomorrow" $null $tok).data.slots
    $w = $null
    for ($i = 0; $i -lt 40; $i++) {
        if ($freeSlots2[$i].status -eq 0 -and $freeSlots2[$i + 1].status -eq 0) {
            $w = @{ s = $freeSlots2[$i].slotNo; e = $freeSlots2[$i + 1].slotNo + 1 }
            break
        }
    }
    if ($null -eq $w) { throw "未找到空闲窗口" }
    # requestId 必须每次运行唯一，否则会命中"幂等返回同一单"分支而干扰并发结论
    $run = [guid]::NewGuid().ToString('N').Substring(0, 6)
    $jobs = 1..10 | ForEach-Object {
        Start-Job -ScriptBlock {
            param($Base, $Token, $CourtId, $Date, $S, $E, $ReqId)
            $h = @{ Authorization = $Token }
            $body = @{ courtId = $CourtId; resvDate = $Date; startSlot = $S; endSlot = $E; requestId = $ReqId } | ConvertTo-Json -Compress
            try {
                (Invoke-RestMethod -Method Post -Uri "$Base/api/v1/reservations" -Headers $h `
                        -ContentType 'application/json; charset=utf-8' `
                        -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 30 -UseBasicParsing).code
            } catch { -1 }
        } -ArgumentList $Base, $tok, 4, $tomorrow, $w.s, $w.e, "CONC-$run-$_"
    }
    $codes = $jobs | Wait-Job -Timeout 90 | Receive-Job
    $jobs | Remove-Job -Force
    $ok = @($codes | Where-Object { $_ -eq 0 }).Count
    $conflict = @($codes | Where-Object { $_ -eq 30001 }).Count
    Info ("成功=$ok 冲突=$conflict 其它=" + @($codes | Where-Object { $_ -ne 0 -and $_ -ne 30001 }).Count)
    return ($ok -eq 1)
}

# ------------------------------------------------- 5. 候补递补（多端互动）----
$queueId = $null
Check "5.1 student2 加入候补队列并拿到排位" {
    $r = Call-Api POST "/api/v1/queues" @{ courtId = $courtId; resvDate = $tomorrow; startSlot = $s1; endSlot = $e1 } $tok2
    if ($r.code -ne 0) { throw $r.message }
    $script:queueId = $r.data.id
    Info ("候补ID=" + $r.data.id + " 排位=" + $r.data.position)
    return ($r.data.position -ge 1)
}
Check "5.2 student 取消预约 -> 槽位释放 -> 候补被通知（status=1）" {
    $c = Call-Api POST "/api/v1/reservations/$resvId/cancel" @{ reason = '验收测试取消' } $tok
    if ($c.code -ne 0) { throw $c.message }
    Info ("取消后状态=" + $c.data.status + " (5已取消/7逾期取消)")
    Start-Sleep -Milliseconds 500
    $q = Call-Api GET "/api/v1/queues/my" $null $tok2
    $mine = $q.data | Where-Object { $_.id -eq $queueId }
    Info ("候补状态=" + $mine.status + " 截止=" + $mine.expireTime + " (1=已通知)")
    return ($mine.status -eq 1)
}
Check "5.3 student2 确认递补 -> 生成新预约单" {
    $r = Call-Api POST "/api/v1/queues/$queueId/confirm" @{} $tok2
    if ($r.code -ne 0) { throw $r.message }
    Info ("新预约ID=" + $r.data.reservationId + " 单号=" + $r.data.resvNo)
    return ($r.data.reservationId -gt 0)
}

# ------------------------------------------------------------ 6. 拼场组队 ----
$teamId = $null; $memberId = $null
$freeSlots3 = (Call-Api GET "/api/v1/courts/3/slots?date=$tomorrow" $null $tok).data.slots
$tw = $null
for ($i = 0; $i -lt 40; $i++) {
    if ($freeSlots3[$i].status -eq 0 -and $freeSlots3[$i + 1].status -eq 0) {
        $tw = @{ s = $freeSlots3[$i].slotNo; e = $freeSlots3[$i + 1].slotNo + 1 }
        break
    }
}
Check "6.1 student 发起拼场（2 人上限）" {
    $r = Call-Api POST "/api/v1/teams" @{ courtId = 3; resvDate = $tomorrow; startSlot = $tw.s; endSlot = $tw.e; maxMembers = 2; remark = '验收拼场' } $tok
    if ($r.code -ne 0) { throw $r.message }
    $script:teamId = $r.data.id
    Info ("拼场ID=" + $r.data.id)
    return ($r.data.id -gt 0)
}
Check "6.2 student2 申请加入 -> 队长收到申请（消息中心）" {
    $r = Call-Api POST "/api/v1/teams/$teamId/join" @{} $tok2
    if ($r.code -ne 0) { throw $r.message }
    $d = Call-Api GET "/api/v1/teams/$teamId" $null $tok
    $pending = @($d.data.members | Where-Object { $_.status -eq 0 })
    $script:memberId = $pending[0].id
    Info ("待审核成员数=" + $pending.Count + " memberId=" + $script:memberId)
    return ($pending.Count -eq 1)
}
Check "6.3 队长审核通过 -> 满员置为 FULL 并通知" {
    $r = Call-Api POST "/api/v1/teams/$teamId/members/$memberId/review?accept=true" $null $tok
    if ($r.code -ne 0) { throw $r.message }
    $d = Call-Api GET "/api/v1/teams/$teamId" $null $tok
    Info ("拼场状态=" + $d.data.status + " 当前人数=" + $d.data.currentMembers + "/" + $d.data.maxMembers + " (1=已满)")
    return ($d.data.status -eq 1)
}
Check "6.4 队长确认成团 -> 生成预约单（拼场成员共享）" {
    $r = Call-Api POST "/api/v1/teams/$teamId/confirm" @{} $tok
    if ($r.code -ne 0) { throw $r.message }
    Info ("成团预约单=" + $r.data.resvNo)
    return ($r.data.reservationId -gt 0)
}

# ---------------------------------------------------------------- 7. 签到 ----
Check "7.1 当前时段创建预约 -> 获取一次性动态签到码 -> 扫码核销" {
    $now = Get-Date
    $startCandidate = [int][math]::Floor(($now.Hour * 60 + $now.Minute) / 30)
    # 若当前槽位开始已超过 15 分钟，则退到上一个槽位，保证仍在签到窗口内
    if ((($now.Hour * 60 + $now.Minute) % 30) -gt 14) { $startCandidate = $startCandidate - 1 }
    if ($startCandidate -lt 16) { throw "当前时间不在开放时段内（$($now.ToString('HH:mm')))" }
    # 依次在所有场地中寻找仍空闲的当前槽位（注意：数组字面量里每个元素都要单独括号，避免逗号优先级陷阱）
    $checkResv = $null
    foreach ($cid in 1..7) {
        foreach ($sn in @(($startCandidate), ($startCandidate + 1))) {
            if ($sn -lt 16 -or $sn -gt 43) { continue }
            $slotsToday = (Call-Api GET "/api/v1/courts/$cid/slots?date=$today" $null $tok).data.slots
            $slotNow = $slotsToday | Where-Object { $_.slotNo -eq $sn }
            if ($null -eq $slotNow -or $slotNow.status -ne 0) { continue }
            $cr = Call-Api POST "/api/v1/reservations" @{ courtId = $cid; resvDate = $today; startSlot = $sn; endSlot = $sn + 1; requestId = "CHECKIN-" + [guid]::NewGuid() } $tok
            if ($cr.code -eq 0) { $checkResv = $cr.data; break }
        }
        if ($null -ne $checkResv) { break }
    }
    if ($null -eq $checkResv) { throw "当前时段在所有场地均已被占用，无法创建签到用预约" }
    if ($checkResv.status -eq 0) { Call-Api POST "/api/v1/reservations/$($checkResv.id)/pay-mock" @{} $tok | Out-Null }
    $code = Call-Api POST "/api/v1/checkin/code" @{ reservationId = $checkResv.id } $tok
    if ($code.code -ne 0) { throw $code.message }
    Info ("签到码长度=" + $code.data.code.Length + " 有效期=" + $code.data.expireSeconds + "s")
    $scan = Call-Api POST "/api/v1/checkin/scan" @{ code = $code.data.code } $tok
    if ($scan.code -ne 0) { throw $scan.message }
    Info ("签到状态=" + $scan.data.status + " 时间=" + $scan.data.checkinTime + " (2=已签到)")
    $again = Call-Api POST "/api/v1/checkin/scan" @{ code = $code.data.code } $tok
    Info ("重复扫码 code=" + $again.code + "（应被拒绝）")
    return ($scan.data.status -eq 2 -and $again.code -ne 0)
}

# ---------------------------------------------------------------- 8. 消息 ----
Check "8.1 student2 消息中心有未读消息（拼场/候补/信用分）" {
    $r = Call-Api GET "/api/v1/notifications?pageNum=1&pageSize=20" $null $tok2
    if ($r.code -ne 0) { throw $r.message }
    $types = ($r.data.records | ForEach-Object { $_.type }) -join ','
    Info ("未读总数=" + $r.data.total + " 类型=" + $types)
    return ($r.data.total -gt 0)
}
Check "8.2 未读数接口 + 轮询兜底接口 + 标记已读" {
    $c = Call-Api GET "/api/v1/notifications/unread-count" $null $tok2
    $p = Call-Api GET "/api/v1/notifications/poll?lastId=0" $null $tok2
    if ($p.code -ne 0) { throw $p.message }
    $first = $p.data[0]
    $read = Call-Api POST "/api/v1/notifications/$($first.id)/read" @{} $tok2
    Info ("未读数=" + $c.data.count + " 轮询=" + $p.data.Count + " 条 标记已读 code=" + $read.code)
    return ($c.data.count -ge 0 -and $p.data.Count -gt 0 -and $read.code -eq 0)
}
Check "8.3 信用分流水可查（签到加分产生流水）" {
    $r = Call-Api GET "/api/v1/credit/logs?pageNum=1&pageSize=10" $null $tok
    if ($r.code -ne 0) { throw $r.message }
    Info ("流水条数=" + $r.data.total + " 最新=" + $r.data.records[0].reason + " 变动=" + $r.data.records[0].changeValue)
    return ($r.data.total -gt 0)
}

# ------------------------------------------------------------ 9. 管理端 ----
Check "9.1 管理端预约列表（含用户与场地名称）" {
    $r = Call-Api GET "/api/v1/admin/reservations?pageNum=1&pageSize=10" $null $tokAdmin
    if ($r.code -ne 0) { throw $r.message }
    $f = $r.data.records[0]
    Info ("总数=" + $r.data.total + " 首条: 单号=" + $f.resvNo + " 用户=" + $f.userName + " 场地=" + $f.courtName)
    return ($r.data.total -gt 0 -and $null -ne $f.userName)
}
Check "9.2 统计总览" {
    $r = Call-Api GET "/api/v1/admin/stat/overview" $null $tokAdmin
    if ($r.code -ne 0) { throw $r.message }
    Info ("用户=" + $r.data.userCount + " 场馆=" + $r.data.venueCount + " 场地=" + $r.data.courtCount + " 今日预约=" + $r.data.resvToday + " 取消率=" + $r.data.cancelRate + " 签到率=" + $r.data.signRate)
    return ($r.data.venueCount -ge 3 -and $r.data.resvTotal -gt 0)
}
Check "9.3 时段热力图（星期 × 槽位）" {
    $r = Call-Api GET "/api/v1/admin/stat/heatmap" $null $tokAdmin
    if ($r.code -ne 0) { throw $r.message }
    Info ("热力点=" + $r.data.Count + " 首个=" + ($r.data | Select-Object -First 1 | ConvertTo-Json -Compress))
    return ($r.data.Count -gt 0)
}
Check "9.4 预约趋势（近 7 天，空日补 0）" {
    $r = Call-Api GET "/api/v1/admin/stat/trend?days=7" $null $tokAdmin
    if ($r.code -ne 0) { throw $r.message }
    Info ("天数=" + $r.data.Count + " 今日=" + ($r.data | Select-Object -Last 1 | ConvertTo-Json -Compress))
    return ($r.data.Count -eq 7)
}
Check "9.5 用户管理列表 + 调整信用分" {
    $r = Call-Api GET "/api/v1/admin/users?pageNum=1&pageSize=10" $null $tokAdmin
    if ($r.code -ne 0) { throw $r.message }
    $u = $r.data.records | Where-Object { $_.username -eq 'student2' }
    Info ("用户数=" + $r.data.total + " student2 信用分=" + $u.creditScore)
    $adj = Call-Api PUT "/api/v1/admin/users/$($u.id)/credit" @{ score = 88; reason = '验收测试调整' } $tokAdmin
    Start-Sleep -Milliseconds 300
    $after = Call-Api GET "/api/v1/auth/me" $null $tok2
    Info ("调整后 student2 信用分=" + $after.data.creditScore + " code=" + $adj.code)
    return ($after.data.creditScore -eq 88)
}
Check "9.6 开放规则维护：查询 + 覆盖保存" {
    $r = Call-Api GET "/api/v1/admin/open-rules?courtId=3" $null $tokAdmin
    if ($r.code -ne 0) { throw $r.message }
    Info ("规则条数=" + $r.data.Count + " 第1条=" + $r.data[0].weekday + " " + $r.data[0].startTime + "-" + $r.data[0].endTime)
    $rules = 1..7 | ForEach-Object { @{ weekday = $_; startTime = '08:00:00'; endTime = '22:00:00'; slotMinutes = 30; maxDuration = 240; price = 25 } }
    $save = Call-Api PUT "/api/v1/admin/courts/3/rules" @{ rules = $rules } $tokAdmin
    return ($r.data.Count -eq 7 -and $save.code -eq 0)
}
Check "9.7 越权访问：学生调用管理员接口被拒绝（20003）" {
    $r = Call-Api GET "/api/v1/admin/stat/overview" $null $tok
    Info ("返回 code=" + $r.code)
    return ($r.code -eq 20003)
}
Check "9.8 越权访问他人预约被拒绝（30005）" {
    $r = Call-Api GET "/api/v1/reservations/my?pageNum=1&pageSize=1" $null $tok
    $otherId = $r.data.records[0].id
    $d = Call-Api GET "/api/v1/reservations/$otherId" $null $tok2
    Info ("student2 读取 student 的预约 code=" + $d.code)
    return ($d.code -eq 30005 -or $d.code -eq 40001)
}

Write-Host ""
Write-Host ("=== 结果：PASS=" + $script:pass + "  FAIL=" + $script:fail + " ===") -ForegroundColor Cyan
if ($script:fail -gt 0) { exit 1 } else { exit 0 }
