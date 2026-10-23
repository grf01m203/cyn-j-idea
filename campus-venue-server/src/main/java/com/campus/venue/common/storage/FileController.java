package com.campus.venue.common.storage;

import com.campus.venue.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文件存储接口：统一入口，OSS 已配置时上传到阿里云 OSS，否则保存到本地磁盘。
 * <p>需登录后调用（Sa-Token 统一拦截 /api/**）。</p>
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final StorageService storageService;

    /** 当前存储方式，供前端提示（OSS / LOCAL） */
    @GetMapping("/config")
    public R<Map<String, Object>> config() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ossEnabled", storageService.ossEnabled());
        map.put("maxSizeMb", 5);
        map.put("allowed", "jpg/jpeg/png/webp/gif/pdf/txt/zip/docx/xlsx/pptx");
        return R.ok(map);
    }

    /**
     * 上传文件（multipart/form-data）
     *
     * @param file 文件字段名固定为 file
     * @param dir  业务目录，例如 avatar / repair / common
     */
    @PostMapping("/upload")
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(defaultValue = "common") String dir) {
        return R.ok(storageService.upload(file, dir));
    }

    /** 生成临时下载地址（bucket 为私有读时使用；public-read 可直接用 upload 返回的 url） */
    @GetMapping("/presign")
    public R<Map<String, Object>> presign(@RequestParam String key,
                                          @RequestParam(required = false) Integer expires) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", key);
        map.put("url", storageService.presign(key, expires));
        return R.ok(map);
    }
}
