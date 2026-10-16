/** 本地存储（带异常兜底，隐私模式/禁用存储时不至于白屏） */
const memory = {}

export function getItem (key) {
  try {
    return window.localStorage.getItem(key)
  } catch (e) {
    return Object.prototype.hasOwnProperty.call(memory, key) ? memory[key] : null
  }
}

export function setItem (key, value) {
  try {
    window.localStorage.setItem(key, value)
  } catch (e) {
    memory[key] = value
  }
}

export function removeItem (key) {
  try {
    window.localStorage.removeItem(key)
  } catch (e) {
    delete memory[key]
  }
}
