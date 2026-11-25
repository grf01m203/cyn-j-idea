/**
 * CampusVenue v0.6.1 新功能验收（Node 18+，使用内置 fetch）
 * 用法: node deploy/feature-test.mjs [http://127.0.0.1:8080]
 *
 * 覆盖 v0.6.0/0.6.1 的变更点：
 *  0. 时段视图改为「仅返回开放窗口内的时段」，带 slotMinutes 与四态
 *  1. 学生自助注册（含重复用户名拦截）
 *  2. 收费场地「未支付不占位」，支付成功才占槽位
 *  3. 同一账号同场地重叠时段被拦截（30008，含待支付）
 *  4. 他人已支付后再次预约被拦截（30001）
 *  5. 待支付订单取消不扣信用分（penalty=0）
 *  6. 拼场均摊支付：成团转「待付齐」，全员付齐才占位
 *  7. 签到核销改为管理端权限（学生调用 -> 20003）
 *  8. 管理员删除用户（逻辑删除 + 脱敏），删除后无法登录
 *  9. 并发支付防超卖：同槽位两笔待支付，仅一笔支付成功，另一笔 30001 并置为过期
 * 10. 管理端核销签到码成功（status -> 2）+ 加分流水
 */
const BASE = process.argv[2] || 'http://127.0.0.1:8080'
let pass = 0, fail = 0, skipped = 0
const check = (name, ok, extra = '') => {
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${extra ? '  ' + extra : ''}`)
  ok ? pass++ : fail++
}
const skip = (name, why) => { console.log(`[SKIP] ${name}  ${why}`); skipped++ }

async function api(method, path, body, token) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: token } : {}) },
    body: body ? JSON.stringify(body) : undefined
  })
  const text = await res.text()
  try { return JSON.parse(text) } catch { return { code: res.status, raw: text } }
}

const login = async (u, p = '123456') => {
  const r = await api('POST', '/api/v1/auth/login', { username: u, password: p })
  if (r.code !== 0) throw new Error(`登录失败 ${u}: ${r.code} ${r.message}`)
  return r.data
}

const tomorrow = new Date(Date.now() + 86400000).toISOString().slice(0, 10)
const today = new Date().toISOString().slice(0, 10)

async function freeWindow(courtId, date, need, token, skipSet = new Set()) {
  const r = await api('GET', `/api/v1/courts/${courtId}/slots?date=${date}`, null, token)
  // 「未支付不占位」意味着时段视图里的空闲不代表本账号没有在途订单，
  // 因此还要排除自己已存在的待支付/已确认预约（否则会被 30008 重叠校验拦下）。
  const mine = await api('GET', '/api/v1/reservations/my?pageNum=1&pageSize=200', null, token)
  const busy = new Set()
  for (const x of mine.data?.records || []) {
    if (x.courtId === courtId && x.resvDate === date && [0, 1, 2].includes(x.status)) {
      for (let i = x.startSlot; i < x.endSlot; i++) busy.add(i)
    }
  }
  const slots = r.data?.slots || []
  for (let i = 0; i + need <= slots.length; i++) {
    const win = slots.slice(i, i + need)
    if (win.every(s => s.status === 0 && !skipSet.has(s.slotNo) && !busy.has(s.slotNo))) {
      win.forEach(s => skipSet.add(s.slotNo))
      return { startSlot: win[0].slotNo, endSlot: win[need - 1].slotNo + 1 }
    }
  }
  throw new Error('找不到连续空闲时段')
}

const creditOf = async (token) => (await api('GET', '/api/v1/auth/me', null, token)).data.creditScore
const reserve = (token, courtId, date, w, tag) => api('POST', '/api/v1/reservations',
  { courtId, resvDate: date, startSlot: w.startSlot, endSlot: w.endSlot, requestId: `${tag}-${Date.now()}-${Math.random()}`, remark: 'v0.6.1 验收' }, token)

console.log(`=== CampusVenue v0.6.1 新功能验收 @ ${BASE} ===`)

const stu = await login('student')
const stu2 = await login('student2')
const adm = await login('admin')
const ct = stu.token, ct2 = stu2.token, at = adm.token

// ---------------------------------------------------------------- 0. 时段视图
const courtId = 4 // 羽毛球 2 号场，25 元/小时，08:00-22:00（避开冒烟脚本使用的 3 号场）
const view = await api('GET', `/api/v1/courts/${courtId}/slots?date=${tomorrow}`, null, ct)
const slots = view.data?.slots || []
check('0.1 时段视图仅返回开放窗口内时段（08:00-22:00 -> 28 个）',
  view.code === 0 && slots.length === 28, `count=${slots.length} court=${view.data?.courtName}`)
check('0.2 每个时段带 slotMinutes 与状态（0空闲/1占用/2不可用/3已过期）',
  slots.every(s => s.slotMinutes > 0 && typeof s.status === 'number'))

const todayView = await api('GET', `/api/v1/courts/${courtId}/slots?date=${today}`, null, ct)
const pastSlots = (todayView.data?.slots || []).filter(s => s.status === 3)
check('0.3 当天已过去的时段标记为「已过期」(status=3)', pastSlots.length > 0, `past=${pastSlots.length}`)

// ---------------------------------------------------------------- 1. 自助注册
const uname = 'qa' + Date.now().toString().slice(-8)
const reg = await api('POST', '/api/v1/auth/register',
  { username: uname, password: '123456', nickname: 'QA新同学', studentNo: 'Q' + uname.slice(-6), phone: '13800009999' })
check('1.1 学生自助注册成功并直接返回 token', reg.code === 0 && !!reg.data?.token, `user=${reg.data?.user?.username}`)
const dup = await api('POST', '/api/v1/auth/register',
  { username: uname, password: '123456', nickname: '重复', studentNo: 'X' + uname.slice(-6) })
check('1.2 重复用户名注册被拒绝', dup.code === 10001, `code=${dup.code} ${dup.message}`)
const newUserId = reg.data?.user?.id

// ---------------------------------------------------------------- 2. 未支付不占位
const skipSet = new Set()
const wA = await freeWindow(courtId, tomorrow, 2, ct, skipSet)
const ra = await reserve(ct, courtId, tomorrow, wA, 'QA-A')
if (ra.code !== 0) {
  console.error(`\n[SETUP] 创建预约失败（可能是该时段已有待支付/已支付预约）：code=${ra.code} ${ra.message}`)
  console.error('请稍后重跑，或先执行 start-all.ps1 -ResetDb 清理演示数据。')
  process.exit(2)
}
check('2.1 收费场地创建后为「待支付」(status=0) 且金额>0',
  ra.data.status === 0 && Number(ra.data.amount) > 0, `status=${ra.data?.status} amount=${ra.data?.amount}`)

const slotsBefore = (await api('GET', `/api/v1/courts/${courtId}/slots?date=${tomorrow}`, null, ct)).data.slots
const stillFree = slotsBefore.filter(s => s.slotNo >= wA.startSlot && s.slotNo < wA.endSlot).every(s => s.status === 0)
check('2.2 未支付时槽位仍为空闲（不占位）', stillFree)

// ---------------------------------------------------------------- 3. 同账号重叠拦截
const overlap = await api('POST', '/api/v1/reservations',
  { courtId, resvDate: tomorrow, startSlot: wA.startSlot + 1, endSlot: wA.endSlot + 1, requestId: `QA-OV-${Date.now()}`, remark: '重叠' }, ct)
check('3.1 同账号同场地重叠时段被拦截(30008)', overlap.code === 30008, `code=${overlap.code} ${overlap.message}`)

// ---------------------------------------------------------------- 4. 支付后才占位
const paid = await api('POST', `/api/v1/reservations/${ra.data.id}/pay-mock`, null, ct)
check('4.1 模拟支付成功并置为已确认', paid.code === 0 && paid.data.status === 1, `status=${paid.data?.status}`)

const slotsAfter = (await api('GET', `/api/v1/courts/${courtId}/slots?date=${tomorrow}`, null, ct)).data.slots
const nowOccupied = slotsAfter.filter(s => s.slotNo >= wA.startSlot && s.slotNo < wA.endSlot).every(s => s.status === 1)
check('4.2 支付后槽位变为已占用', nowOccupied)

const other = await api('POST', '/api/v1/reservations',
  { courtId, resvDate: tomorrow, startSlot: wA.startSlot, endSlot: wA.endSlot, requestId: `QA-OTH-${Date.now()}`, remark: '他人抢占' }, ct2)
check('4.3 他人预约已支付时段被拦截(30001)', other.code === 30001, `code=${other.code} ${other.message}`)

// ---------------------------------------------------------------- 5. 待支付取消不扣分
const wC = await freeWindow(courtId, tomorrow, 2, ct, skipSet)
const rc = await reserve(ct, courtId, tomorrow, wC, 'QA-C')
const creditBefore = await creditOf(ct)
const cancel = await api('POST', `/api/v1/reservations/${rc.data.id}/cancel`, { reason: '待支付取消测试' }, ct)
const creditAfter = await creditOf(ct)
check('5.1 待支付订单取消不扣信用分(penalty=0)',
  cancel.code === 0 && Number(cancel.data.penalty ?? 0) === 0 && creditBefore === creditAfter,
  `penalty=${cancel.data?.penalty} credit ${creditBefore}->${creditAfter}`)

// ---------------------------------------------------------------- 6. 拼场均摊支付
const wT = await freeWindow(courtId, tomorrow, 2, ct, skipSet)
const team = await api('POST', '/api/v1/teams',
  { courtId, resvDate: tomorrow, startSlot: wT.startSlot, endSlot: wT.endSlot, maxMembers: 2, remark: '均摊支付验收' }, ct)
check('6.1 发起拼场成功', team.code === 0 && !!team.data?.id, `teamId=${team.data?.id}`)
const teamId = team.data.id

const join = await api('POST', `/api/v1/teams/${teamId}/join`, null, ct2)
check('6.2 学生2 申请加入', join.code === 0, `memberId=${join.data?.memberId}`)
const review = await api('POST', `/api/v1/teams/${teamId}/members/${join.data.memberId}/review?accept=true`, null, ct)
check('6.3 队长审核通过', review.code === 0)

const conf = await api('POST', `/api/v1/teams/${teamId}/confirm`, null, ct)
check('6.4 成团后进入「待付齐」且算出均摊金额',
  conf.code === 0 && conf.data.status === 4 && Number(conf.data.shareAmount) > 0,
  `status=${conf.data?.status} share=${conf.data?.shareAmount} amount=${conf.data?.amount}`)
const teamResv = conf.data.reservationId

const pay1 = await api('POST', `/api/v1/teams/${teamId}/pay`, null, ct)
check('6.5 队长支付后仍未占位（1/2 已付）',
  pay1.code === 0 && pay1.data.allPaid === false && pay1.data.paidCount === 1, `paid=${pay1.data?.paidCount}/${pay1.data?.memberCount}`)

const pay2 = await api('POST', `/api/v1/teams/${teamId}/pay`, null, ct2)
check('6.6 全员付齐后成团并占用场地',
  pay2.code === 0 && pay2.data.allPaid === true && pay2.data.status === 2, `allPaid=${pay2.data?.allPaid} status=${pay2.data?.status}`)

const teamResvStatus = await api('GET', `/api/v1/reservations/${teamResv}`, null, ct)
check('6.7 成团预约单已确认(status=1)', teamResvStatus.code === 0 && teamResvStatus.data.status === 1,
  `resvStatus=${teamResvStatus.data?.status}`)

// ---------------------------------------------------------------- 7. 核销权限
const scanByStudent = await api('POST', '/api/v1/checkin/scan', { code: '123456' }, ct)
check('7.1 学生调用核销接口被拒绝(20003)', scanByStudent.code === 20003, `code=${scanByStudent.code} ${scanByStudent.message}`)

// ---------------------------------------------------------------- 8. 管理员删除用户
const del = await api('DELETE', `/api/v1/admin/users/${newUserId}`, null, at)
check('8.1 管理员删除（无在途业务的）用户成功', del.code === 0, `code=${del.code} ${del.message}`)
const reLogin = await api('POST', '/api/v1/auth/login', { username: uname, password: '123456' })
check('8.2 被删除用户无法再登录', reLogin.code !== 0, `code=${reLogin.code} ${reLogin.message}`)

// ---------------------------------------------------------------- 9. 并发支付防超卖
const wP = await freeWindow(courtId, tomorrow, 2, ct, skipSet)
const pa = await reserve(ct, courtId, tomorrow, wP, 'QA-PA')
const pb = await reserve(ct2, courtId, tomorrow, wP, 'QA-PB')
check('9.1 同一时段可同时存在两笔待支付订单',
  pa.code === 0 && pb.code === 0 && pa.data.status === 0 && pb.data.status === 0)

const [payA, payB] = await Promise.all([
  api('POST', `/api/v1/reservations/${pa.data.id}/pay-mock`, null, ct),
  api('POST', `/api/v1/reservations/${pb.data.id}/pay-mock`, null, ct2)
])
const wins = [payA, payB].filter(x => x.code === 0).length
check('9.2 并发支付仅一笔成功（唯一索引兜底）', wins === 1, `A=${payA.code} B=${payB.code}`)
const loser = payA.code === 0 ? pb : pa
const loserPay = payA.code === 0 ? payB : payA
check('9.3 失败方返回 30001 且订单置为过期(6)',
  loserPay.code === 30001 && loserPay.message?.includes('') !== false,
  `code=${loserPay.code} ${loserPay.message}`)
const loserDetail = await api('GET', `/api/v1/reservations/${loser.data.id}`, null, loser === pa ? ct : ct2)
check('9.4 失败方订单状态为 6（已过期）', loserDetail.code === 0 && loserDetail.data.status === 6,
  `status=${loserDetail.data?.status}`)

// ---------------------------------------------------------------- 10. 管理端核销
// 免费场地当天「签到窗口内」的最近时段：创建即确认，可直接取码。
// 依次尝试 5/6/7 号免费场地，跳过与既有待支付单重叠的时段。
const now = new Date()
let ckDone = false
for (const freeCourt of [5, 6, 7]) {
  const tv = await api('GET', `/api/v1/courts/${freeCourt}/slots?date=${today}`, null, ct)
  const nextSlot = (tv.data?.slots || []).find(s => {
    const [h, m] = s.label.slice(0, 5).split(':').map(Number)
    const start = new Date(now); start.setHours(h, m, 0, 0)
    return start > now && (start - now) / 60000 <= 15
  })
  if (!nextSlot) continue

  const rck = await reserve(ct, freeCourt, today, { startSlot: nextSlot.slotNo, endSlot: nextSlot.slotNo + 1 }, 'QA-CK')
  if (rck.code !== 0) continue // 该场地时段与既有预约重叠，换下一个
  ckDone = true

  check('10.1 免费场地创建即确认(status=1)', rck.data.status === 1, `status=${rck.data?.status}`)
  const code = await api('POST', '/api/v1/checkin/code', { reservationId: rck.data.id }, ct)
  check('10.2 学生获取 6 位一次性签到码', code.code === 0 && /^\d{6}$/.test(String(code.data?.code)),
    `code=${code.data?.code} ttl=${code.data?.expireSeconds}s`)
  const scanned = await api('POST', '/api/v1/checkin/scan', { code: String(code.data.code) }, at)
  check('10.3 管理端核销成功且预约置为已签到(2)', scanned.code === 0 && scanned.data.status === 2,
    `code=${scanned.code} status=${scanned.data?.status}`)
  const again = await api('POST', '/api/v1/checkin/scan', { code: String(code.data.code) }, at)
  check('10.4 同一签到码不可重复核销(30007)', again.code === 30007, `code=${again.code} ${again.message}`)
  const logs = await api('GET', '/api/v1/credit/logs', null, ct)
  check('10.5 核销后产生信用加分流水', logs.code === 0 && (logs.data?.records?.length ?? 0) > 0,
    `logs=${logs.data?.records?.length}`)
  break
}
if (!ckDone) skip('10.x 管理端核销签到码', '当天没有处于签到窗口内的免费时段可用')

console.log(`\n=== 结果：PASS=${pass}  FAIL=${fail}  SKIP=${skipped} ===`)
process.exit(fail === 0 ? 0 : 1)
