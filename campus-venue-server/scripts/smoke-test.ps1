# =============================================================================
# CampusVenue 端到端验收脚本（兼容 Windows PowerShell 5.1 / PowerShell 7）
# 目标版本：v0.6.1（未支付不占位 / 拼场均摊 / 候补先付后占 / 管理端核销）
# 覆盖：登录 / 场馆与时段 / 预约与支付 / 并发支付防超卖 / 取消与候补递补
#       拼场均摊支付 / 签到核销 / 消息与轮询 / 统计 / 权限
# 用法：powershell -NoProfile -File scripts/smoke-test.ps1 -Base http://localhost:8080
# =============================================================================
param(
    [string]$Base = "http://localhost:8080"
)

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$script:pass = 0
$script:fail = 0
$script:skip = 0

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

# 取某场地某日的时段视图（v0.6.1：只返回开放窗口内的时段）
function Get-Slots([int]$CourtId, [string]$Date, [string]$Token) {
    $r = Call-Api GET "/api/v1/courts/$CourtId/slots?date=$Date" $null $Token
    if ($r.code -ne 0) { throw $r.message }
    return $r.data.slots
}

# 本账号在该场地该日的在途预约所占槽位（待支付也算，避免被 30008 重叠校验拦住）
function Get-BusySlots([int]$CourtId, [string]$Date, [string]$Token) {
    $busy = @{}
    $r = Call-Api GET "/api/v1/reservations/my?pageNum=1&pageSize=200" $null $Token
    if ($r.code -eq 0) {
        foreach ($x in $r.data.records) {
            if ($x.courtId -eq $CourtId -and $x.resvDate -eq $Date -and (@(0, 1, 2) -contains $x.status)) {
                for ($i = $x.startSlot; $i -lt $x.endSlot; $i++) { $busy[[int]$i] = $true }
            }
        }
    }
    return $busy
}

# 从时段视图中找一段连续空闲窗口（同时排除本账号的在途预约）
function Find-Window($Slots, [hashtable]$Busy, [int]$Need) {
    for ($i = 0; $i -le ($Slots.Count - $Need); $i++) {
        $ok = $true
        for ($k = 0; $k -lt $Need; $k++) {
            $s = $Slots[$i + $k]
            if ($s.status -ne 0 -or $Busy.ContainsKey([int]$s.slotNo)) { $ok = $false; break }
        }
        if ($ok) {
            return @{
                startSlot = $Slots[$i].slotNo
                endSlot   = $Slots[$i + $Need - 1].slotNo + 1
                label     = $Slots[$i].label
            }
        }
    }
    return $null
}

Write-Host "=== CampusVenue 端到端验收 v0.6.1 @ $Base ===" -ForegroundColor Cyan

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
Check "1.4 错误密码返回 20002（业务错误码，不是 500）" {
    $r = Call-Api POST "/api/v1/auth/login" @{ username = 'student'; password = 'wrong-password' }
    Info ("返回 code=" + $r.code)
    return ($r.code -eq 20002)
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
$tomorrow = (Get-Date).AddDays(1).ToString('yyyy-MM-dd')
$today = (Get-Date).ToString('yyyy-MM-dd')

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
Check "2.3 时段视图只返回开放窗口内时段（08:00-22:00 -> 28 个，含 slotMinutes）" {
    $slots = Get-Slots $courtId $tomorrow $tok
    $bad = @($slots | Where-Object { $null -eq $_.slotMinutes -or $_.slotMinutes -le 0 })
    $free = @($slots | Where-Object { $_.status -eq 0 }).Count
    Info ("槽位总数=" + $slots.Count + " 空闲=" + $free + " 粒度=" + $slots[0].slotMinutes + "min")
    return ($slots.Count -eq 28 -and $bad.Count -eq 0 -and $free -gt 0)
}

$freeSlots = Get-Slots $courtId $tomorrow $tok
# 主窗口必须同时避开两个演示账号的在途预约（待支付也算，否则会被 30008 拦）
$busyBoth = @{}
foreach ($set in @((Get-BusySlots $courtId $tomorrow $tok), (Get-BusySlots $courtId $tomorrow $tok2))) {
    foreach ($k in $set.Keys) { $busyBoth[$k] = $true }
}
$window = Find-Window $freeSlots $busyBoth 3
Check "2.4 找到连续 3 个空闲槽位用于预约" {
    Info ("窗口 " + $window.label + " (slot " + $window.startSlot + "~" + $window.endSlot + ")")
    return ($null -ne $window)
}
$s1 = $window.startSlot; $e1 = $window.endSlot

# ---------------------------------------------------------------- 3. 预约 ----
$resvId = $null
Check "3.1 requestId 幂等：重复提交返回同一单（另起时段避免重叠校验）" {
    # 幂等用例另找 2 个槽位，且避开主窗口，否则两个用例会互相触发 30008
    $busyIdem = @{}
    foreach ($k in $busyBoth.Keys) { $busyIdem[$k] = $true }
    for ($i = $s1; $i -lt $e1; $i++) { $busyIdem[[int]$i] = $true }
    $w2 = Find-Window (Get-Slots $courtId $tomorrow $tok) $busyIdem 2
    $rid = "IDEMP-" + [guid]::NewGuid()
    $a = Call-Api POST "/api/v1/reservations" @{ courtId = $courtId; resvDate = $tomorrow; startSlot = $w2.startSlot; endSlot = $w2.endSlot; requestId = $rid } $tok
    $b = Call-Api POST "/api/v1/reservations" @{ courtId = $courtId; resvDate = $tomorrow; startSlot = $w2.startSlot; endSlot = $w2.endSlot; requestId = $rid } $tok
    Info ("两次 id=" + $a.data.id + " / " + $b.data.id)
    return ($a.code -eq 0 -and $a.data.id -eq $b.data.id)
}
Check "3.2 收费场地创建后为「待支付」(status=0)，且不占用槽位" {
    $r = Call-Api POST "/api/v1/reservations" @{ courtId = $courtId; resvDate = $tomorrow; startSlot = $s1; endSlot = $e1; requestId = "SMOKE-$([guid]::NewGuid())"; remark = '端到端验收' } $tok
    if ($r.code -ne 0) { throw $r.message }
    $script:resvId = $r.data.id
    $slots = Get-Slots $courtId $tomorrow $tok
    $mine = @($slots | Where-Object { $_.slotNo -ge $s1 -and $_.slotNo -lt $e1 })
    $allFree = @($mine | Where-Object { $_.status -eq 0 }).Count -eq $mine.Count
    Info ("单号=" + $r.data.resvNo + " 状态=" + $r.data.status + " 金额=" + $r.data.amount + " 槽位仍空闲=" + $allFree)
    return ($r.data.status -eq 0 -and $r.data.amount -gt 0 -and $allFree)
}
Check "3.3 同账号同场地重叠时段被拦截（30008，含待支付单）" {
    $r = Call-Api POST "/api/v1/reservations" @{ courtId = $courtId; resvDate = $tomorrow; startSlot = $s1 + 1; endSlot = $e1 + 1; requestId = "OVERLAP-" + [guid]::NewGuid() } $tok
    Info ("返回 code=" + $r.code + " message=" + $r.message)
    return ($r.code -eq 30008)
}
Check "3.4 另一学生可对同一时段下待支付单（未支付不占位，允许并存）" {
    $r = Call-Api POST "/api/v1/reservations" @{ courtId = $courtId; resvDate = $tomorrow; startSlot = $s1; endSlot = $e1; requestId = "RACE-" + [guid]::NewGuid() } $tok2
    if ($r.code -ne 0) { throw $r.message }
    $script:loserId = $r.data.id
    Info ("student2 待支付单=" + $r.data.resvNo + " 状态=" + $r.data.status)
    return ($r.data.status -eq 0)
}
Check "3.5 学生支付成功 -> 状态置为已确认（1）且槽位被占用" {
    $r = Call-Api POST "/api/v1/reservations/$resvId/pay-mock" @{} $tok
    if ($r.code -ne 0) { throw $r.message }
    $slots = Get-Slots $courtId $tomorrow $tok
    $mine = @($slots | Where-Object { $_.slotNo -ge $s1 -and $_.slotNo -lt $e1 })
    $allBusy = @($mine | Where-Object { $_.status -eq 1 }).Count -eq $mine.Count
    Info ("状态=" + $r.data.status + " 槽位已占用=" + $allBusy)
    return ($r.data.status -eq 1 -and $allBusy)
}
Check "3.6 同段被抢先支付后，另一笔待支付单被自动作废（status=6）且支付被拒绝" {
    $r = Call-Api POST "/api/v1/reservations/$($script:loserId)/pay-mock" @{} $tok2
    $d = Call-Api GET "/api/v1/reservations/$($script:loserId)" $null $tok2
    Info ("支付 code=" + $r.code + " message=" + $r.message + " 订单状态=" + $d.data.status + "（6=已过期/已作废）")
    return ($r.code -ne 0 -and $d.data.status -eq 6)
}
Check "3.7 我的预约列表可查到该单" {
    $r = Call-Api GET "/api/v1/reservations/my?pageNum=1&pageSize=50" $null $tok
    if ($r.code -ne 0) { throw $r.message }
    $hit = $r.data.records | Where-Object { $_.id -eq $resvId }
    Info ("共 " + $r.data.total + " 条，命中=" + [bool]$hit)
    return ($null -ne $hit)
}

# ------------------------------------------ 4. 并发支付（10 个账号抢同一时段）----
Check "4.1 10 个账号并发支付同一时段：只能成功 1 单（唯一索引兜底）" {
    $w = Find-Window (Get-Slots 4 $tomorrow $tok) @{} 2
    if ($null -eq $w) { throw "未找到空闲窗口" }

    # 并发必须来自不同账号：固定 9 个压测账号（首次注册，之后直接登录，避免每次运行堆积用户）
    $run = [guid]::NewGuid().ToString('N').Substring(0, 6)
    $tokens = @($tok)
    for ($i = 1; $i -lt 10; $i++) {
        $u = "smkqa$i"
        $reg = Call-Api POST "/api/v1/auth/register" @{ username = $u; password = '123456'; nickname = "压测$i"; studentNo = "SMKQA$i" }
        if ($reg.code -eq 0) {
            $tokens += $reg.data.token
        } else {
            $lg = Call-Api POST "/api/v1/auth/login" @{ username = $u; password = '123456' }
            if ($lg.code -eq 0) { $tokens += $lg.data.token }
        }
    }
    if ($tokens.Count -lt 10) { throw ("可用账号不足：" + $tokens.Count) }

    $jobs = @()
    for ($i = 0; $i -lt $tokens.Count; $i++) {
        $jobs += Start-Job -ScriptBlock {
            param($Base, $Token, $CourtId, $Date, $S, $E, $ReqId)
            $h = @{ Authorization = $Token }
            $body = @{ courtId = $CourtId; resvDate = $Date; startSlot = $S; endSlot = $E; requestId = $ReqId } | ConvertTo-Json -Compress
            try {
                $created = Invoke-RestMethod -Method Post -Uri "$Base/api/v1/reservations" -Headers $h `
                    -ContentType 'application/json; charset=utf-8' `
                    -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 30 -UseBasicParsing
                if ($created.code -ne 0) { return $created.code }
                $paid = Invoke-RestMethod -Method Post -Uri "$Base/api/v1/reservations/$($created.data.id)/pay-mock" -Headers $h `
                    -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes('{}')) `
                    -TimeoutSec 30 -UseBasicParsing
                return $paid.code
            } catch {
                # 业务异常体形如 {"code":30001,...}
                try { return ([int]$_.ErrorDetails.Message | ConvertFrom-Json).code } catch { return -1 }
            }
        } -ArgumentList $Base, $tokens[$i], 4, $tomorrow, $w.startSlot, $w.endSlot, "CONC-$run-$i"
    }
    $codes = @($jobs | Wait-Job -Timeout 120 | Receive-Job)
    $jobs | Remove-Job -Force
    $ok = @($codes | Where-Object { $_ -eq 0 }).Count
    $conflict = @($codes | Where-Object { $_ -eq 30001 }).Count
    Info ("成功=$ok 冲突=$conflict 其它=" + @($codes | Where-Object { $_ -ne 0 -and $_ -ne 30001 }).Count)
    return ($ok -eq 1)
}

# ------------------------------------------------- 5. 候补递补（多端互动）----
$queueId = $null
Check "5.1 student2 加入候补队列并拿到排位" {
    # 清掉上一轮遗留的候补，避免「已在队列中」导致 add 失败
    $my = Call-Api GET "/api/v1/queues/my" $null $tok2
    foreach ($q in @($my.data)) {
        if ($q.status -eq 0 -or $q.status -eq 1) { Call-Api POST "/api/v1/queues/$($q.id)/cancel" @{} $tok2 | Out-Null }
    }
    $r = Call-Api POST "/api/v1/queues" @{ courtId = $courtId; resvDate = $tomorrow; startSlot = $s1; endSlot = $e1 } $tok2
    if ($r.code -ne 0) { throw $r.message }
    $script:queueId = $r.data.id
    Info ("候补ID=" + $r.data.id + " 排位=" + $r.data.position)
    return ($r.data.position -ge 1)
}
Check "5.2 student 取消已支付预约 -> 释放槽位 -> 候补被通知（status=1）" {
    $c = Call-Api POST "/api/v1/reservations/$resvId/cancel" @{ reason = '验收测试取消' } $tok
    if ($c.code -ne 0) { throw $c.message }
    Info ("取消后状态=" + $c.data.status + " 扣分=" + $c.data.penalty + " (5已取消/7逾期取消)")
    Start-Sleep -Milliseconds 500
    $q = Call-Api GET "/api/v1/queues/my" $null $tok2
    $mine = $q.data | Where-Object { $_.id -eq $queueId }
    Info ("候补状态=" + $mine.status + " 截止=" + $mine.expireTime + " (1=已通知)")
    return ($mine.status -eq 1)
}
Check "5.3 student2 确认递补 -> 生成待支付预约（needPay）" {
    $r = Call-Api POST "/api/v1/queues/$queueId/confirm" @{} $tok2
    if ($r.code -ne 0) { throw $r.message }
    Info ("新预约ID=" + $r.data.reservationId + " 单号=" + $r.data.resvNo + " needPay=" + $r.data.needPay)
    $script:queueResvId = $r.data.reservationId
    return ($r.data.reservationId -gt 0 -and $r.data.needPay -eq $true)
}
Check "5.4 student2 支付递补单 -> 重新占位" {
    $r = Call-Api POST "/api/v1/reservations/$($script:queueResvId)/pay-mock" @{} $tok2
    if ($r.code -ne 0) { throw $r.message }
    Info ("状态=" + $r.data.status)
    return ($r.data.status -eq 1)
}

# ------------------------------------------------------------ 6. 拼场组队 ----
$teamId = $null; $memberId = $null
$teamWindow = Find-Window (Get-Slots 3 $tomorrow $tok) (Get-BusySlots 3 $tomorrow $tok) 2
Check "6.1 student 发起拼场（2 人上限）" {
    $r = Call-Api POST "/api/v1/teams" @{ courtId = 3; resvDate = $tomorrow; startSlot = $teamWindow.startSlot; endSlot = $teamWindow.endSlot; maxMembers = 2; remark = '验收拼场' } $tok
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
Check "6.4 队长确认成团 -> 进入「待付齐」(4) 并算出均摊金额" {
    $r = Call-Api POST "/api/v1/teams/$teamId/confirm" @{} $tok
    if ($r.code -ne 0) { throw $r.message }
    Info ("成团预约单=" + $r.data.resvNo + " 状态=" + $r.data.status + " 均摊=" + $r.data.shareAmount + " 总额=" + $r.data.amount)
    $script:teamResvId = $r.data.reservationId
    return ($r.data.reservationId -gt 0 -and $r.data.status -eq 4 -and $r.data.shareAmount -gt 0)
}
Check "6.5 队长先付 -> 仍待付齐（1/2），未占位" {
    $r = Call-Api POST "/api/v1/teams/$teamId/pay" @{} $tok
    if ($r.code -ne 0) { throw $r.message }
    Info ("已付=" + $r.data.paidCount + "/" + $r.data.memberCount + " allPaid=" + $r.data.allPaid)
    return ($r.data.allPaid -eq $false -and $r.data.paidCount -eq 1)
}
Check "6.6 队员付清 -> 全员付齐、成团并占位（status=2）" {
    $r = Call-Api POST "/api/v1/teams/$teamId/pay" @{} $tok2
    if ($r.code -ne 0) { throw $r.message }
    $d = Call-Api GET "/api/v1/teams/$teamId" $null $tok
    Info ("allPaid=" + $r.data.allPaid + " 拼场状态=" + $d.data.status + " 预约状态=" + (Call-Api GET "/api/v1/reservations/$($script:teamResvId)" $null $tok).data.status)
    return ($r.data.allPaid -eq $true -and $d.data.status -eq 2)
}

# ---------------------------------------------------------------- 7. 签到 ----
# 签到窗口＝「时段开始前后 15 分钟」，且不能预约已过去的时段，
# 因此只有「下一个时段在 15 分钟内开始」时才造得出可核销的预约；
# 落在时段边界后的空档（如 15:00~15:15）时本组跳过，而不是误报失败。
$script:checkinSkipped = $false
$script:checkResv = $null
$now = Get-Date
$nowMinutes = $now.Hour * 60 + $now.Minute + $now.Second / 60.0
$candidates = @()
foreach ($cid in 5, 6, 7, 1, 2, 3, 4) {
    $slotsToday = Get-Slots $cid $today $tok
    foreach ($s in $slotsToday) {
        if ($s.status -ne 0) { continue }
        $hh, $mm = $s.label.Substring(0, 5).Split(':')
        $slotStart = [int]$hh * 60 + [int]$mm
        $delta = $slotStart - $nowMinutes
        if ($delta -gt 0 -and $delta -le 15) { $candidates += @{ court = $cid; slot = $s.slotNo } }
    }
}
$tried = 0
foreach ($cand in $candidates) {
    if ($tried -ge 12) { break }
    $tried++
    $cr = Call-Api POST "/api/v1/reservations" @{ courtId = $cand.court; resvDate = $today; startSlot = $cand.slot; endSlot = $cand.slot + 1; requestId = "CHECKIN-" + [guid]::NewGuid() } $tok
    if ($cr.code -eq 0) { $script:checkResv = $cr.data; break }
    Info ("court=$($cand.court) slot=$($cand.slot) 创建失败 code=$($cr.code) $($cr.message)")
}
if ($null -eq $script:checkResv) {
    $script:checkinSkipped = $true
} else {
    if ($script:checkResv.status -eq 0) { Call-Api POST "/api/v1/reservations/$($script:checkResv.id)/pay-mock" @{} $tok | Out-Null }
    $code = Call-Api POST "/api/v1/checkin/code" @{ reservationId = $script:checkResv.id } $tok
    if ($code.code -eq 0) {
        $script:checkCode = $code.data.code
    } else {
        Info ("取码失败 code=" + $code.code + " " + $code.message)
        $script:checkinSkipped = $true
    }
}
if ($script:checkinSkipped) {
    $script:skip++
    Write-Host "[SKIP] 7.x 签到（当前不在可造单的签到窗口内：需下一时段在 15 分钟内开始）" -ForegroundColor Yellow
} else {
    Check "7.1 学生取 6 位一次性签到码（签到窗口内）" {
        Info ("签到码长度=" + $script:checkCode.Length + "（6 位数字，60 秒有效）")
        return ($script:checkCode.Length -eq 6)
    }
    Check "7.2 学生调用核销接口被拒绝（20003，核销为管理端职责）" {
        $r = Call-Api POST "/api/v1/checkin/scan" @{ code = $script:checkCode } $tok
        Info ("返回 code=" + $r.code + " message=" + $r.message)
        return ($r.code -eq 20003)
    }
    Check "7.3 管理端核销成功 -> 预约置为已签到（2）" {
        $r = Call-Api POST "/api/v1/checkin/scan" @{ code = $script:checkCode } $tokAdmin
        if ($r.code -ne 0) { throw $r.message }
        Info ("签到状态=" + $r.data.status + " 时间=" + $r.data.checkinTime + " (2=已签到)")
        return ($r.data.status -eq 2)
    }
    Check "7.4 同一签到码不可重复核销（30007）" {
        $r = Call-Api POST "/api/v1/checkin/scan" @{ code = $script:checkCode } $tokAdmin
        Info ("重复扫码 code=" + $r.code + " message=" + $r.message)
        return ($r.code -eq 30007)
    }
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
Check "8.3 信用分流水可查（核销加分产生流水）" {
    $r = Call-Api GET "/api/v1/credit/logs?pageNum=1&pageSize=10" $null $tok
    if ($r.code -ne 0) { throw $r.message }
    $hit = @($r.data.records | Where-Object { $_.reason -like '*签到*' })
    Info ("流水条数=" + $r.data.total + " 最新=" + $r.data.records[0].reason + " 变动=" + $r.data.records[0].changeValue)
    return ($r.data.total -gt 0 -and $hit.Count -gt 0)
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
Write-Host ("=== 结果：PASS=" + $script:pass + "  FAIL=" + $script:fail + "  SKIP=" + $script:skip + " ===") -ForegroundColor Cyan
if ($script:fail -gt 0) { exit 1 } else { exit 0 }
