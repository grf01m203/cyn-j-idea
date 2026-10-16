/**
 * CampusVenue 多端并发在线 + 实时消息验证（Node 18+，使用内置 fetch / WebSocket）
 * 用法：node scripts/realtime-multiclient-test.mjs [http://localhost:8080]
 *
 * 覆盖：
 *  A. 同一账号在两台设备同时登录 —— 两个 token 同时有效（Sa-Token is-concurrent）
 *  B. 两个账号同时建立 WebSocket 长连接
 *  C. 拼场申请 -> 队长端秒级收到 TEAM_INVITE 推送
 *  D. 预约取消 -> 候补端秒级收到 QUEUE_PROMOTED 推送
 */
const BASE = process.argv[2] || 'http://localhost:8080'
const WS_BASE = BASE.replace(/^http/, 'ws')

let pass = 0
let fail = 0

function check(name, ok, extra = '') {
  if (ok) {
    console.log(`[PASS] ${name}${extra ? '  ' + extra : ''}`)
    pass++
  } else {
    console.log(`[FAIL] ${name}${extra ? '  ' + extra : ''}`)
    fail++
  }
}

async function api(method, path, body, token) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: token } : {})
    },
    body: body ? JSON.stringify(body) : undefined
  })
  return res.json()
}

const login = async (username) => {
  const r = await api('POST', '/api/v1/auth/login', { username, password: '123456' })
  if (r.code !== 0) throw new Error(`登录失败 ${username}: ${r.message}`)
  return r.data
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function openWs(token, label) {
  const ws = new WebSocket(`${WS_BASE}/ws/notice?token=${token}`)
  const messages = []
  ws.addEventListener('message', (e) => {
    try {
      messages.push(JSON.parse(e.data))
    } catch {
      /* ignore */
    }
  })
  ws.addEventListener('error', (e) => console.log(`   (${label} WebSocket 错误: ${e.message || e.type})`))
  return { ws, messages, label }
}

const readSlotWindow = async (token, courtId, date, count) => {
  const r = await api('GET', `/api/v1/courts/${courtId}/slots?date=${date}`, null, token)
  if (r.code !== 0) throw new Error('时段查询失败: ' + r.message)
  const slots = r.data.slots
  for (let i = 0; i + count <= 40; i++) {
    let ok = true
    for (let k = 0; k < count; k++) if (slots[i + k].status !== 0) ok = false
    if (ok) return { startSlot: slots[i].slotNo, endSlot: slots[i + count - 1].slotNo + 1, label: slots[i].label }
  }
  throw new Error('未找到连续空闲时段')
}

const tomorrow = () => {
  const d = new Date(Date.now() + 86400000)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

async function main() {
  console.log(`=== CampusVenue 多端并发在线验证 @ ${BASE} ===`)
  const date = tomorrow()

  // A. 同一账号两处同时登录
  const dev1 = await login('student')
  const dev2 = await login('student')
  check('A1 同一账号在两台设备同时登录，拿到两个不同 token',
    dev1.token !== dev2.token, `token1=${dev1.token.slice(0, 8)}… token2=${dev2.token.slice(0, 8)}…`)
  const me1 = await api('GET', '/api/v1/auth/me', null, dev1.token)
  const me2 = await api('GET', '/api/v1/auth/me', null, dev2.token)
  check('A2 两个 token 同时有效（并发在线，互不踢下线）',
    me1.code === 0 && me2.code === 0, `设备1=${me1.data?.nickname} 设备2=${me2.data?.nickname}`)

  const other = await login('student2')

  // B. 两个账号同时建立 WebSocket
  const wsA = openWs(dev1.token, '设备A/学生')
  const wsB = openWs(other.token, '设备B/学生2')
  await sleep(900)
  check('B1 两台设备的 WebSocket 长连接同时建立',
    wsA.ws.readyState === 1 && wsB.ws.readyState === 1,
    `A=${['连接中', '已连接', '关闭中', '已关闭'][wsA.ws.readyState]} B=${['连接中', '已连接', '关闭中', '已关闭'][wsB.ws.readyState]}`)

  // C. 拼场申请 -> 队长端实时收到
  const w = await readSlotWindow(dev1.token, 3, date, 2)
  const team = await api('POST', '/api/v1/teams', {
    courtId: 3, resvDate: date, startSlot: w.startSlot, endSlot: w.endSlot, maxMembers: 2, remark: '多端并发验证'
  }, dev1.token)
  if (team.code !== 0) throw new Error('发起拼场失败: ' + team.message)
  wsA.messages.length = 0
  const join = await api('POST', `/api/v1/teams/${team.data.id}/join`, null, other.token)
  if (join.code !== 0) throw new Error('申请加入失败: ' + join.message)
  await sleep(1200)
  const invite = wsA.messages.find((m) => m.type === 'TEAM_INVITE')
  check('C1 学生2 申请加入拼场 -> 队长端实时收到 TEAM_INVITE 推送',
    !!invite, invite ? `收到「${invite.title}」` : `未收到（收到 ${wsA.messages.length} 条）`)

  // D. 取消预约 -> 候补端实时收到递补通知
  const w2 = await readSlotWindow(dev1.token, 4, date, 1)
  const resv = await api('POST', '/api/v1/reservations', {
    courtId: 4, resvDate: date, startSlot: w2.startSlot, endSlot: w2.endSlot, requestId: 'RT-' + Date.now()
  }, dev1.token)
  if (resv.code !== 0) throw new Error('创建预约失败: ' + resv.message)
  const queue = await api('POST', '/api/v1/queues', {
    courtId: 4, resvDate: date, startSlot: w2.startSlot, endSlot: w2.endSlot
  }, other.token)
  if (queue.code !== 0) throw new Error('加入候补失败: ' + queue.message)
  wsB.messages.length = 0
  await api('POST', `/api/v1/reservations/${resv.data.id}/cancel`, { reason: '多端验证取消' }, dev1.token)
  await sleep(1200)
  const promoted = wsB.messages.find((m) => m.type === 'QUEUE_PROMOTED')
  check('D1 设备A 取消预约 -> 设备B 候补端实时收到 QUEUE_PROMOTED 推送',
    !!promoted, promoted ? `收到「${promoted.title}」` : `未收到（收到 ${wsB.messages.length} 条）`)

  // E. 候补端确认递补，生成预约单
  const confirm = await api('POST', `/api/v1/queues/${queue.data.id}/confirm`, null, other.token)
  check('E1 候补端确认递补生成新预约单', confirm.code === 0, confirm.code === 0 ? confirm.data.resvNo : confirm.message)

  wsA.ws.close()
  wsB.ws.close()
  await sleep(200)

  console.log(`\n=== 结果：PASS=${pass}  FAIL=${fail} ===`)
  process.exit(fail > 0 ? 1 : 0)
}

main().catch((e) => {
  console.error('执行失败：' + e.message)
  process.exit(1)
})
