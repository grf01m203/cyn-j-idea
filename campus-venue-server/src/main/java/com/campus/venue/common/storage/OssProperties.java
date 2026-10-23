package com.campus.venue.common.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 配置（对应 application.yml 中 campus.oss.*）。
 * 凭据一律通过环境变量注入，禁止写入代码仓库。
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.oss")
public class OssProperties {

    /** 是否启用 OSS 存储；关闭时回落到本地磁盘 data/upload */
    private boolean enabled = false;
    /** 地域 Endpoint，如 https://oss-cn-shanghai.aliyuncs.com */
    private String endpoint = "https://oss-cn-shanghai.aliyuncs.com";
    /** Bucket 名称 */
    private String bucket = "";
    private String accessKeyId = "";
    private String accessKeySecret = "";
    /** 对象键前缀，例如 campus-venue/ */
    private String prefix = "campus-venue/";
    /** 单文件大小上限（字节） */
    private long maxSize = 5 * 1024 * 1024;
    /** 预签名下载地址有效期（秒） */
    private int presignSeconds = 3600;
}
