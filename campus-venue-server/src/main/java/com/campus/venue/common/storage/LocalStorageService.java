package com.campus.venue.common.storage;

import com.campus.venue.common.exception.BizException;
import com.campus.venue.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * 本地磁盘存储（OSS 未启用时的降级实现），文件由 WebConfig 映射到 /upload/** 访问。
 */
@Slf4j
@Service
public class LocalStorageService {

    private static final long MAX_SIZE = 5 * 1024 * 1024L;

    public String upload(MultipartFile file, String dir) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "文件为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ResultCode.PARAM_ERROR, "文件超过大小限制（5MB）");
        }
        String original = file.getOriginalFilename();
        String ext = original != null && original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "bin";
        if (!OssStorageService.allowedExtensions().contains(ext)) {
            throw new BizException(ResultCode.PARAM_ERROR, "不支持的文件类型：" + ext);
        }
        String safeDir = StringUtils.hasText(dir) ? dir.replaceAll("[^a-zA-Z0-9_-]", "") : "common";
        String relative = safeDir + "/" + LocalDate.now() + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        File target = new File(System.getProperty("user.dir") + File.separator + "data" + File.separator
                + "upload" + File.separator + relative.replace("/", File.separator));
        try {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("创建目录失败: " + parent);
            }
            file.transferTo(target);
            log.info("本地文件已保存: {}", target.getAbsolutePath());
            return relative;
        } catch (Exception e) {
            log.error("本地文件保存失败", e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "文件保存失败：" + e.getMessage());
        }
    }

    public String publicUrl(String key) {
        return "/upload/" + key;
    }
}
