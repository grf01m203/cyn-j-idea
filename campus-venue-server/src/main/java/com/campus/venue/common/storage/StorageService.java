package com.campus.venue.common.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 存储门面：优先 OSS，未配置时自动降级到本地磁盘。
 * 前端只需调用 /api/v1/files/upload，无需关心底层实现。
 */
@Service
@RequiredArgsConstructor
public class StorageService {

    private final OssStorageService ossStorageService;
    private final LocalStorageService localStorageService;

    public boolean ossEnabled() {
        return ossStorageService.enabled();
    }

    public Map<String, Object> upload(MultipartFile file, String dir) {
        boolean useOss = ossStorageService.enabled();
        String key = useOss
                ? ossStorageService.upload(file, dir)
                : localStorageService.upload(file, dir);
        String url = useOss
                ? ossStorageService.publicUrl(key)
                : localStorageService.publicUrl(key);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("storage", useOss ? "OSS" : "LOCAL");
        result.put("key", key);
        result.put("url", url);
        result.put("bucket", useOss ? ossStorageService.bucket() : null);
        result.put("size", file.getSize());
        result.put("contentType", file.getContentType());
        result.put("originalName", file.getOriginalFilename());
        return result;
    }

    /** 私有读场景生成临时访问地址；本地存储直接返回相对路径 */
    public String presign(String key, Integer expireSeconds) {
        if (ossStorageService.enabled()) {
            return ossStorageService.presignGet(key, expireSeconds);
        }
        return localStorageService.publicUrl(key);
    }

    public void delete(String key) {
        if (ossStorageService.enabled()) {
            ossStorageService.delete(key);
        }
    }
}
