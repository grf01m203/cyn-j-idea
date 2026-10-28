# -*- coding: utf-8 -*-
import json, sys, time, datetime, urllib.request, urllib.error

BASE = "http://127.0.0.1:8080/api/v1"
results = []

def call(method, path, body=None, token=None):
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = token
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            text = r.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8")
    except Exception as e:
        return {"code": -1, "message": "网络错误: %s" % e, "data": None}
    try:
        return json.loads(text)
    except Exception:
        return {"code": -1, "message": text[:200], "data": None}

def check(name, expect, r):
    ok = (r.get("code") == expect)
    results.append((name, expect, r.get("code"), ok, r.get("message")))
    print(("[PASS] " if ok else "[FAIL] ") + name + "  expect=%s actual=%s msg=%s" % (expect, r.get("code"), r.get("message")))
    return ok

def mark(name, ok, detail):
    results.append((name, "OK", detail, ok, ""))
    print(("[PASS] " if ok else "[FAIL] ") + name + "  " + str(detail))

now = datetime.datetime.now()
today = now.strftime("%Y-%m-%d")
yesterday = (now - datetime.timedelta(days=1)).strftime("%Y-%m-%d")
tomorrow = (now + datetime.timedelta(days=1)).strftime("%Y-%m-%d")
stamp = now.strftime("%H%M%S")

# ---- 认证 ----
check("A1 错误密码 -> 20002", 20002, call("POST", "/auth/login", {"username": "admin", "password": "bad-pass"}))
login = call("POST", "/auth/login", {"username": "admin", "password": "123456"})
check("A2 管理员登录 -> 0", 0, login)
admin_token = (login.get("data") or {}).get("token")
slogin = call("POST", "/auth/login", {"username": "student", "password": "123456"})
check("A3 学生登录 -> 0", 0, slogin)
student_token = (slogin.get("data") or {}).get("token")

# ---- #1 过去时间校验 ----
check("B1 昨天预约 -> 30008", 30008, call("POST", "/reservations",
      {"courtId": 1, "resvDate": yesterday, "startSlot": 20, "endSlot": 22, "requestId": "rt-past-" + stamp}, student_token))
past_slot = max(0, now.hour * 2 - 6)
check("B2 今日已过时段预约 -> 30008", 30008, call("POST", "/reservations",
      {"courtId": 1, "resvDate": today, "startSlot": past_slot, "endSlot": past_slot + 2, "requestId": "rt-todaypast-" + stamp}, student_token))
slot_res = call("GET", "/courts/1/slots?date=" + today, token=student_token)
past_count = len([s for s in (slot_res.get("data") or {}).get("slots", []) if s.get("status") == 3])
mark("B3 时段图含已过期(status=3) 数量=%d" % past_count, past_count > 0, past_count)

# ---- #5 / #8 场馆参数校验 ----
check("C1 开放时间倒挂 -> 10001", 10001, call("POST", "/admin/venues",
      {"name": "回归倒塌" + stamp, "address": "x", "openTime": "22:00", "closeTime": "08:00", "phone": "1", "description": "x"}, admin_token))
check("C2 场馆名超长 -> 10001", 10001, call("POST", "/admin/venues",
      {"name": "回归超长" + ("长" * 60), "address": "x", "openTime": "08:00", "closeTime": "22:00"}, admin_token))
tv = call("POST", "/admin/venues", {"name": "回归测试场馆" + stamp, "address": "测试地址", "openTime": "08:00", "closeTime": "22:00", "phone": "13800000000", "description": "reg"}, admin_token)
check("C3 正常创建场馆 -> 0", 0, tv)
check("C4 重复场馆名 -> 10001", 10001, call("POST", "/admin/venues",
      {"name": "回归测试场馆" + stamp, "address": "测试地址", "openTime": "08:00", "closeTime": "22:00"}, admin_token))
check("C5 删除有场地的场馆 -> 30010", 30010, call("DELETE", "/admin/venues/1", token=admin_token))
check("C6 负容量场地 -> 10001", 10001, call("POST", "/admin/courts",
      {"venueId": 1, "name": "负容量" + stamp, "type": "篮球", "capacity": -1, "pricePerHour": 10}, admin_token))
check("C7 负价格场地 -> 10001", 10001, call("POST", "/admin/courts",
      {"venueId": 1, "name": "负价格" + stamp, "type": "篮球", "capacity": 10, "pricePerHour": -5}, admin_token))
check("C8 规则负价格 -> 10001", 10001, call("PUT", "/admin/courts/1/rules",
      {"rules": [{"weekday": 1, "startTime": "08:00", "endTime": "10:00", "slotMinutes": 30, "maxDuration": 240, "price": -1}]}, admin_token))
check("C9 规则起止倒挂 -> 10001", 10001, call("PUT", "/admin/courts/1/rules",
      {"rules": [{"weekday": 1, "startTime": "10:00", "endTime": "08:00", "slotMinutes": 30, "maxDuration": 240, "price": 10}]}, admin_token))
new_venue_id = (tv.get("data") or {}).get("id")
if new_venue_id:
    check("C10 删除无场地场馆 -> 0", 0, call("DELETE", "/admin/venues/%s" % new_venue_id, token=admin_token))

# ---- #12 / #7 / #3 用户状态、信用分、违约计数 ----
users = call("GET", "/admin/users?keyword=student&pageNum=1&pageSize=10", token=admin_token)
recs = (users.get("data") or {}).get("records", [])
stu = next((u for u in recs if u.get("username") == "student"), None)
if stu:
    sid = stu["id"]
    check("D1 用户状态=2 -> 10001", 10001, call("PUT", "/admin/users/%s/status" % sid, {"status": 2}, admin_token))
    check("D2 用户状态=1 -> 0", 0, call("PUT", "/admin/users/%s/status" % sid, {"status": 1}, admin_token))
    check("D3 信用分 120 -> 10001", 10001, call("PUT", "/admin/users/%s/credit" % sid, {"score": 120, "reason": "回归测试"}, admin_token))
    before = stu.get("violateCount")
    check("D4 管理员调分 95 -> 0", 0, call("PUT", "/admin/users/%s/credit" % sid, {"score": 95, "reason": "回归测试-调分"}, admin_token))
    time.sleep(0.3)
    users2 = call("GET", "/admin/users?keyword=student&pageNum=1&pageSize=10", token=admin_token)
    after = next((u for u in (users2.get("data") or {}).get("records", []) if u.get("id") == sid), {})
    mark("D5 管理员调分不计违约 before=%s after=%s" % (before, after.get("violateCount")), before == after.get("violateCount"), after.get("violateCount"))
    call("PUT", "/admin/users/%s/credit" % sid, {"score": 100, "reason": "回归测试-恢复"}, admin_token)

# ---- #10 管理端新接口 ----
teams = call("GET", "/admin/teams?pageNum=1&pageSize=5", token=admin_token)
check("E1 管理端拼场分页 -> 0", 0, teams)
trecs = (teams.get("data") or {}).get("records", [])
mark("E1b 拼场记录字段: " + (",".join(trecs[0].keys()) if trecs else "(无数据)"), True, len(trecs))
queues = call("GET", "/admin/queues?pageNum=1&pageSize=5", token=admin_token)
check("E2 管理端候补分页 -> 0", 0, queues)
qrecs = (queues.get("data") or {}).get("records", [])
mark("E2b 候补记录字段: " + (",".join(qrecs[0].keys()) if qrecs else "(无数据)"), True, len(qrecs))
ann = call("POST", "/admin/announcements", {"title": "回归公告" + stamp, "content": "v0.4.0 回归测试公告，发布后应推送给全部启用用户。"}, admin_token)
check("E3 发布公告 -> 0", 0, ann)
print("     receiverCount=%s" % (ann.get("data") or {}).get("receiverCount"))
ann_list = call("GET", "/admin/announcements?pageNum=1&pageSize=5", token=admin_token)
check("E4 公告列表 -> 0", 0, ann_list)
notice_list = call("GET", "/notifications?pageNum=1&pageSize=10", token=student_token)
ann_notices = [n for n in (notice_list.get("data") or {}).get("records", []) if n.get("type") == "ANNOUNCEMENT"]
mark("E5 学生端可见公告消息 数量=%d" % len(ann_notices), len(ann_notices) > 0, len(ann_notices))
check("E6 撤销公告 -> 0", 0, call("DELETE", "/admin/announcements/%s" % (ann.get("data") or {}).get("id"), token=admin_token))
notice_after = call("GET", "/notifications?pageNum=1&pageSize=10", token=student_token)
ann_after = [n for n in (notice_after.get("data") or {}).get("records", []) if n.get("type") == "ANNOUNCEMENT"]
mark("E7 撤销后学生端公告副本已删除 残留=%d" % len(ann_after), len(ann_after) == 0, len(ann_after))

# ---- #6 异常处理 ----
check("F1 不存在接口 -> 40001", 40001, call("GET", "/not-exists-endpoint", token=admin_token))
check("F2 缺少必填参数 date -> 10001", 10001, call("GET", "/courts/1/slots", token=admin_token))
check("F3 分页参数类型错误 -> 10001", 10001, call("GET", "/admin/teams?pageNum=abc&pageSize=10", token=admin_token))

# ---- #1/#2 下单字段 + 取消流程 ----
slots = call("GET", "/courts/1/slots?date=" + tomorrow, token=student_token)
free = [s for s in (slots.get("data") or {}).get("slots", []) if s.get("status") == 0]
if free:
    s0 = int(free[0]["slotNo"])
    order = call("POST", "/reservations", {"courtId": 1, "resvDate": tomorrow, "startSlot": s0, "endSlot": s0 + 2, "requestId": "rt-book-" + stamp, "remark": "回归测试"}, student_token)
    check("G1 未来时段下单 -> 0", 0, order)
    od = order.get("data") or {}
    has = all(k in od for k in ("canPay", "canCancel", "canCheckin"))
    mark("G2 下单响应含 canPay/canCancel/canCheckin status=%s amount=%s" % (od.get("status"), od.get("amount")), has,
         "canPay=%s canCancel=%s canCheckin=%s" % (od.get("canPay"), od.get("canCancel"), od.get("canCheckin")))
    if od.get("status") == 0:
        check("G3 模拟支付 -> 0", 0, call("POST", "/reservations/%s/pay-mock" % od.get("id"), token=student_token))
    cancel = call("POST", "/reservations/%s/cancel" % od.get("id"), {"reason": "回归测试取消"}, student_token)
    check("G4 开始前取消 -> 0", 0, cancel)
    cd = cancel.get("data") or {}
    print("     cancel returned status=%s canCancel=%s" % (cd.get("status"), cd.get("canCancel")))
else:
    print("[SKIP] 明日无空闲时段，取消流程未测")

my = call("GET", "/reservations/my?pageNum=1&pageSize=20", token=student_token)
started = [r for r in (my.get("data") or {}).get("records", []) if r.get("canCancel") is False and r.get("status") in (0, 1)]
if started:
    check("G5 超过取消窗口 -> 30006", 30006, call("POST", "/reservations/%s/cancel" % started[0]["id"], {"reason": "回归测试-窗口外"}, student_token))
else:
    print("[SKIP] 无超窗口订单可测 G5")

total = len(results)
passed = len([r for r in results if r[3]])
print("\n===== 回归结果: PASS=%d/%d FAIL=%d =====" % (passed, total, total - passed))
for r in results:
    if not r[3]:
        print("FAILED:", r[0], "expect=", r[1], "actual=", r[2], "msg=", r[4])
