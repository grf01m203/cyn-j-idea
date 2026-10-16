import { ref } from 'vue'
import { fetchVenues, fetchVenueCourts } from '@/api/venue'

/**
 * 场地下拉选项（公共/管理员均可读）
 * 契约中没有「全量场地列表」接口，只有 GET /venues（分页）与 GET /venues/{id}/courts，
 * 因此这里先用 /venues?pageSize=100 取场馆，再并发拉取每个场馆的场地，展平为 options。
 * 返回项：{ value: courtId, label: 场馆-场地, court: 原始场地, venueName }
 */
export function useCourtOptions () {
  const courtOptions = ref([])
  const loading = ref(false)
  let loaded = false

  async function load (force = false) {
    if (loaded && !force) return courtOptions.value
    loading.value = true
    try {
      const page = await fetchVenues({ pageNum: 1, pageSize: 100 })
      const venues = page?.records || []
      const groups = await Promise.all(
        venues.map(async (v) => {
          try {
            const courts = await fetchVenueCourts(v.id)
            return (Array.isArray(courts) ? courts : []).map((c) => ({
              value: c.id,
              label: `${v.name} - ${c.name}`,
              venueName: v.name,
              court: c
            }))
          } catch (e) {
            return []
          }
        })
      )
      courtOptions.value = groups.flat()
    } catch (e) {
      courtOptions.value = []
    } finally {
      loading.value = false
      loaded = true
    }
    return courtOptions.value
  }

  return { courtOptions, loading, load }
}
