/** 文件上传 /api/v1/files（需登录） */
import request from '@/utils/request'

/**
 * POST /api/v1/files/upload（multipart/form-data，字段名 file，可带 dir=avatar）
 * → {storage,key,url,bucket,size,contentType,originalName}
 * 注意：必须显式声明 multipart/form-data，否则 axios 会因实例默认的
 * application/json 头把 FormData 序列化成 JSON。
 */
export function uploadFile (file, dir = 'avatar') {
  const form = new FormData()
  form.append('file', file)
  if (dir) form.append('dir', dir)
  return request.post('/files/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
