package com.campus.venue.common.storage;

import com.campus.venue.common.exception.BizException;
import com.campus.venue.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 阿里云 OSS 存储服务（手写 V1 签名，不引入额外 SDK 依赖）。
 *
 * <p>提供：上传对象、生成预签名下载地址、删除对象。
 * 未启用 OSS（campus.oss.enabled=false）时由 {@link LocalStorageService} 处理，
 * 由 {@link StorageService} 统一对外。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssStorageService {

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
    private static final Set<String> ALLOWED_EXT = Set.of(
            "jpg", "jpeg", "png", "webp", "gif", "pdf", "txt", "zip", "docx", "xlsx", "pptx");

    private final OssProperties props;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public boolean enabled() {
        return props.isEnabled()
                && StringUtils.hasText(props.getBucket())
                && StringUtils.hasText(props.getAccessKeyId())
                && StringUtils.hasText(props.getAccessKeySecret());
    }

    public String bucket() {
        return props.getBucket();
    }

    public static Set<String> allowedExtensions() {
        return ALLOWED_EXT;
    }

    /**
     * 上传文件到 OSS，返回对象 key（可通过 {@link #publicUrl(String)} 拼出访问地址）。
     */
    public String upload(MultipartFile file, String dir) {
        if (!enabled()) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "OSS 未启用");
        }
        validate(file);
        String key = buildKey(file.getOriginalFilename(), dir);
        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "读取上传文件失败");
        }
        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType() : guessContentType(key);
        String date = ZonedDateTime.now(ZoneOffset.UTC).format(HTTP_DATE);
        String resource = "/" + props.getBucket() + "/" + key;
        String stringToSign = "PUT\n\n" + contentType + "\n" + date + "\n" + resource;
        String authorization = "OSS " + props.getAccessKeyId() + ":" + hmacSha1(stringToSign);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/" + encodeKey(key)))
                .timeout(Duration.ofSeconds(30))
                .header("Date", date)
                .header("Authorization", authorization)
                .header("Content-Type", contentType)
                // Content-Length 由 HttpClient 依据 BodyPublisher 自动计算，手动设置会被拒绝
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("OSS 上传失败: status={}, body={}", response.statusCode(), truncate(response.body()));
                throw new BizException(ResultCode.SYSTEM_ERROR, "OSS 上传失败（HTTP " + response.statusCode() + "）");
            }
            return key;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("OSS 上传异常", e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "OSS 上传异常：" + e.getMessage());
        }
    }

    /** 生成带签名的临时下载地址（私有读场景使用） */
    public String presignGet(String key, Integer expireSeconds) {
        if (!enabled()) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "OSS 未启用");
        }
        if (!StringUtils.hasText(key)) {
            throw new BizException(ResultCode.PARAM_ERROR, "key 不能为空");
        }
        String cleanKey = key.startsWith("/") ? key.substring(1) : key;
        long expires = System.currentTimeMillis() / 1000 + (expireSeconds == null
                ? props.getPresignSeconds() : Math.max(60, Math.min(expireSeconds, 86400)));
        String stringToSign = "GET\n\n\n" + expires + "\n/" + props.getBucket() + "/" + cleanKey;
        String signature = hmacSha1(stringToSign);
        return baseUrl() + "/" + encodeKey(cleanKey)
                + "?OSSAccessKeyId=" + urlEncode(props.getAccessKeyId())
                + "&Expires=" + expires
                + "&Signature=" + urlEncode(signature);
    }

    /** 删除对象（管理端清理用） */
    public void delete(String key) {
        if (!enabled() || !StringUtils.hasText(key)) {
            return;
        }
        String cleanKey = key.startsWith("/") ? key.substring(1) : key;
        String date = ZonedDateTime.now(ZoneOffset.UTC).format(HTTP_DATE);
        String stringToSign = "DELETE\n\n\n" + date + "\n/" + props.getBucket() + "/" + cleanKey;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/" + encodeKey(cleanKey)))
                .timeout(Duration.ofSeconds(15))
                .header("Date", date)
                .header("Authorization", "OSS " + props.getAccessKeyId() + ":" + hmacSha1(stringToSign))
                .DELETE()
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                log.warn("OSS 删除失败: key={}, status={}", cleanKey, response.statusCode());
            }
        } catch (Exception e) {
            log.warn("OSS 删除异常: key={}, err={}", cleanKey, e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 内部 ----

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "文件为空");
        }
        if (file.getSize() > props.getMaxSize()) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "文件超过大小限制（" + (props.getMaxSize() / 1024 / 1024) + "MB）");
        }
        String name = file.getOriginalFilename();
        String ext = name != null && name.contains(".")
                ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException(ResultCode.PARAM_ERROR, "不支持的文件类型：" + ext);
        }
    }

    private String buildKey(String originalName, String dir) {
        String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "bin";
        String safeDir = StringUtils.hasText(dir) ? dir.replaceAll("[^a-zA-Z0-9/_-]", "") : "common";
        return props.getPrefix() + safeDir + "/"
                + LocalDate.now() + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;
    }

    private String baseUrl() {
        String endpoint = props.getEndpoint().trim();
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint.replace("://", "://" + props.getBucket() + ".");
    }

    public String publicUrl(String key) {
        return baseUrl() + "/" + encodeKey(key);
    }

    private String encodeKey(String key) {
        StringBuilder sb = new StringBuilder();
        for (String seg : key.split("/")) {
            if (seg.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(urlEncode(seg));
        }
        return sb.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String hmacSha1(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(props.getAccessKeySecret().getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "签名计算失败：" + e.getMessage());
        }
    }

    private String guessContentType(String key) {
        String ext = key.substring(key.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "pdf" -> "application/pdf";
            case "zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }

    private String truncate(String text) {
        return text == null ? "" : (text.length() > 300 ? text.substring(0, 300) : text);
    }
}
