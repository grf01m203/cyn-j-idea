/**
 * 生成 tabBar 图标（无第三方依赖，使用 Node 内置 zlib 手写 PNG）
 * 运行：node scripts/gen-tabbar-icons.mjs
 */
import zlib from 'node:zlib'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const OUT_DIR = path.resolve(__dirname, '../src/static/tabbar')
const SIZE = 81

function crc32(buf) {
  let c
  const table = []
  for (let n = 0; n < 256; n++) {
    c = n
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    table[n] = c >>> 0
  }
  let crc = 0xffffffff
  for (let i = 0; i < buf.length; i++) crc = table[(crc ^ buf[i]) & 0xff] ^ (crc >>> 8)
  return (crc ^ 0xffffffff) >>> 0
}

function chunk(type, data) {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length, 0)
  const typeBuf = Buffer.from(type, 'ascii')
  const body = Buffer.concat([typeBuf, data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body), 0)
  return Buffer.concat([len, body, crc])
}

/** pixels: Uint8Array RGBA, length = SIZE*SIZE*4 */
function encodePng(pixels, size) {
  const raw = Buffer.alloc((size * 4 + 1) * size)
  for (let y = 0; y < size; y++) {
    raw[y * (size * 4 + 1)] = 0 // filter none
    pixels.copy
      ? pixels.copy(raw, y * (size * 4 + 1) + 1, y * size * 4, (y + 1) * size * 4)
      : Buffer.from(pixels.buffer, y * size * 4, size * 4).copy(raw, y * (size * 4 + 1) + 1)
  }
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(size, 0)
  ihdr.writeUInt32BE(size, 4)
  ihdr[8] = 8 // bit depth
  ihdr[9] = 6 // RGBA
  ihdr[10] = 0
  ihdr[11] = 0
  ihdr[12] = 0
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0))
  ])
}

/** 简单画布 */
function createCanvas(size) {
  const px = Buffer.alloc(size * size * 4, 0)
  const set = (x, y, [r, g, b], alpha = 255) => {
    if (x < 0 || y < 0 || x >= size || y >= size) return
    const i = (y * size + x) * 4
    px[i] = r
    px[i + 1] = g
    px[i + 2] = b
    px[i + 3] = alpha
  }
  return { px, set, size }
}

const R = (c) => c
const COLORS = {
  gray: [138, 144, 153],
  blue: [22, 119, 255]
}

/** 填充矩形（带圆角） */
function roundRect(c, x0, y0, w, h, radius, color) {
  for (let y = y0; y < y0 + h; y++) {
    for (let x = x0; x < x0 + w; x++) {
      const dx = Math.min(x - x0, x0 + w - 1 - x)
      const dy = Math.min(y - y0, y0 + h - 1 - y)
      if (dx < radius && dy < radius) {
        const d = Math.hypot(radius - dx, radius - dy)
        if (d > radius) continue
      }
      c.set(x, y, color)
    }
  }
}

/** 描边矩形 */
function strokeRect(c, x0, y0, w, h, t, color) {
  roundRect(c, x0, y0, w, t, 0, color)
  roundRect(c, x0, y0 + h - t, w, t, 0, color)
  roundRect(c, x0, y0, t, h, 0, color)
  roundRect(c, x0 + w - t, y0, t, h, 0, color)
}

/** 实心圆 */
function circle(c, cx, cy, r, color) {
  for (let y = Math.floor(cy - r); y <= Math.ceil(cy + r); y++) {
    for (let x = Math.floor(cx - r); x <= Math.ceil(cx + r); x++) {
      if (Math.hypot(x - cx, y - cy) <= r) c.set(x, y, color)
    }
  }
}

function drawVenue(c, color) {
  // 房子
  for (let y = 20; y < 34; y++) {
    const half = (y - 18) * 1.15
    for (let x = Math.round(40 - half); x <= Math.round(40 + half); x++) c.set(x, y, color)
  }
  strokeRect(c, 24, 34, 33, 28, 4, color)
  roundRect(c, 35, 46, 11, 16, 0, color)
}

function drawReservation(c, color) {
  strokeRect(c, 20, 22, 41, 40, 4, color)
  roundRect(c, 20, 22, 41, 9, 0, color)
  roundRect(c, 28, 14, 5, 12, 2, color)
  roundRect(c, 48, 14, 5, 12, 2, color)
  roundRect(c, 28, 40, 8, 6, 2, color)
  roundRect(c, 40, 40, 8, 6, 2, color)
  roundRect(c, 28, 51, 8, 6, 2, color)
  roundRect(c, 40, 51, 8, 6, 2, color)
}

function drawTeam(c, color) {
  circle(c, 32, 32, 10, color)
  circle(c, 54, 36, 8, color)
  for (let y = 46; y < 62; y++) {
    const spread = 12 + (y - 46) * 0.5
    for (let x = Math.round(32 - spread); x <= Math.round(32 + spread); x++) c.set(x, y, color)
  }
  for (let y = 47; y < 60; y++) {
    const spread = 8 + (y - 47) * 0.4
    for (let x = Math.round(54 + spread - 12); x <= Math.round(54 + spread); x++) c.set(x, y, color)
  }
}

function drawNotice(c, color) {
  // 铃铛
  for (let y = 22; y < 46; y++) {
    const w = 11 + (y - 22) * 0.28
    for (let x = Math.round(40 - w); x <= Math.round(40 + w); x++) c.set(x, y, color)
  }
  roundRect(c, 25, 45, 30, 5, 2, color)
  circle(c, 40, 56, 5, color)
  roundRect(c, 37, 14, 6, 8, 3, color)
}

function drawMine(c, color) {
  circle(c, 40, 30, 11, color)
  for (let y = 44; y < 64; y++) {
    const spread = 13 + (y - 44) * 0.7
    for (let x = Math.round(40 - spread); x <= Math.round(40 + spread); x++) c.set(x, y, color)
  }
}

const drawers = {
  venue: drawVenue,
  reservation: drawReservation,
  team: drawTeam,
  notice: drawNotice,
  mine: drawMine
}

fs.mkdirSync(OUT_DIR, { recursive: true })

for (const [name, draw] of Object.entries(drawers)) {
  for (const [suffix, color] of [
    ['', COLORS.gray],
    ['-active', COLORS.blue]
  ]) {
    const canvas = createCanvas(SIZE)
    draw(canvas, color)
    const png = encodePng(canvas.px, SIZE)
    const file = path.join(OUT_DIR, `${name}${suffix}.png`)
    fs.writeFileSync(file, png)
    console.log('written', file, png.length, 'bytes')
  }
}
