/**
 * 候补递补 + 实时推送验证（适配 v0.6.1「未支付不占位」模型）
 * 用法: node deploy/queue-push-test.mjs [http://127.0.0.1:8080]
 *
 * 流程：
 *  1. 学生A 创建预约 -> 支付（此时才真正占用场地）
 *  2. 学生B 加入该时段候补队列
 *  3. 学生A 取消已支付预约 -> 释放槽位 -> 学生B 应实时收到 QUEUE_PROMOTED
 *  4. 学生B 确认递补 -> 生成「待支付」预约（needPay=true）
 *  5. 学生B 支付 -> 场地被占用
 */
const BASE = process.argv[2] || 'http://127.0.0.1:8080'
const WS = BASE.replace(/^http/, 'ws')
let pass = 0, fail = 0
const check = (n, ok, extra = '') => { console.log(`${ok ? '[PASS]' : '[FAIL]'} ${n}${extra ? '  ' + extra : ''}`); ok ? pass++ : fail++ }
const sleep = (ms) => new Promise(r => setTimeout(r, ms))

async function api(method, path, body, token) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: token } : {}) },
    body: body ? JSON.stringify(body) : undefined
  })
  const t = await res.text()
  try { return JSON.parse(t) } catch { return { code: res.status, raw: t } }
}
const login = async (u) => (await api('POST', '/api/v1/auth/login', { username: u, password: '123456' })).data

const tomorrow = new Date(Date.now() + 86400000).toISOString().slice(0, 10)
const a = await login('student')
const b = await login('student2')

// 学生B 建立 WebSocket，收集推送
const messages = []
const ws = new WebSocket(`${WS}/ws/notice?token=${b.token}`)
await new Promise((res, rej) => { ws.addEventListener('open', res); ws.addEventListener('error', rej); setTimeout(res, 3000) })
ws.addEventListener('message', (e) => { try { messages.push(JSON.parse(e.data)) } catch { } })

// 找一个 A 没有在途订单的空闲时段（court 4）
const courtId = 4
const view = await api('GET', `/api/v1/courts/${courtId}/slots?date=${tomorrow}`, null, a.token)
const mine = await api('GET', '/api/v1/reservations/my?pageNum=1&pageSize=200', null, a.token)
const busy = new Set()
for (const x of mine.data?.records || []) {
  if (x.courtId === courtId && x.resvDate === tomorrow && [0, 1, 2].includes(x.status))
    for (let i = x.startSlot; i < x.endSlot; i++) busy.add(i)
}
const slots = view.data?.slots || []
let w = null
for (let i = 0; i < slots.length; i++) {
  if (slots[i].status === 0 && !busy.has(slots[i].slotNo) && slots[i + 1] && slots[i + 1].status === 0 && !busy.has(slots[i + 1].slotNo)) {
    w = { startSlot: slots[i].slotNo, endSlot: slots[i + 1].slotNo + 1 }; break
  }
}
if (!w) { console.error('找不到可用时段'); process.exit(2) }
console.log(`使用时段 slot ${w.startSlot}~${w.endSlot} (${tomorrow})`)

// 1. A 创建并支付
const resv = await api('POST', '/api/v1/reservations',
  { courtId, resvDate: tomorrow, startSlot: w.startSlot, endSlot: w.endSlot, requestId: 'QP-' + Date.now() }, a.token)
check('1. 学生A 创建预约（待支付）', resv.code === 0 && resv.data.status === 0, `status=${resv.data?.status}`)
const pay = await api('POST', `/api/v1/reservations/${resv.data.id}/pay-mock`, null, a.token)
check('2. 学生A 支付成功，场地被占用', pay.code === 0 && pay.data.status === 1, `status=${pay.data?.status}`)

// 2. B 加入候补
const q = await api('POST', '/api/v1/queues', { courtId, resvDate: tomorrow, startSlot: w.startSlot, endSlot: w.endSlot }, b.token)
check('3. 学生B 加入候补队列', q.code === 0, `queueId=${q.data?.id} position=${q.data?.position}`)

// 3. A 取消 -> B 应收到推送
messages.length = 0
const cancel = await api('POST', `/api/v1/reservations/${resv.data.id}/cancel`, { reason: '候补递补验证' }, a.token)
check('4. 学生A 取消已支付预约（释放槽位）', cancel.code === 0, `penalty=${cancel.data?.penalty}`)
await sleep(1500)
const promoted = messages.find(m => m.type === 'QUEUE_PROMOTED')
check('5. 学生B 实时收到 QUEUE_PROMOTED 推送', !!promoted,
  promoted ? `「${promoted.title}」${promoted.content?.slice(0, 30)}…` : `收到 ${messages.length} 条：${messages.map(m => m.type).join(',')}`)

// 4. B 确认 -> 生成待支付预约
const confirm = await api('POST', `/api/v1/queues/${q.data.id}/confirm`, null, b.token)
check('6. 学生B 确认递补生成待支付预约', confirm.code === 0 && confirm.data.needPay === true, `needPay=${confirm.data?.needPay} resv=${confirm.data?.resvNo}`)

// 5. B 支付 -> 占位
const payB = await api('POST', `/api/v1/reservations/${confirm.data.reservationId}/pay-mock`, null, b.token)
check('7. 学生B 支付后占位成功', payB.code === 0 && payB.data.status === 1, `status=${payB.data?.status}`)

const after = await api('GET', `/api/v1/courts/${courtId}/slots?date=${tomorrow}`, null, a.token)
const occupied = (after.data?.slots || []).filter(s => s.slotNo >= w.startSlot && s.slotNo < w.endSlot).every(s => s.status === 1)
check('8. 时段视图确认该时段已被占用', occupied)

ws.close()
console.log(`\n=== 结果：PASS=${pass}  FAIL=${fail} ===`)
process.exit(fail === 0 ? 0 : 1)
