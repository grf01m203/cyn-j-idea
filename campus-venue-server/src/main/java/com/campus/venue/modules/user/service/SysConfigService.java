package com.campus.venue.modules.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.venue.modules.user.entity.SysConfig;
import com.campus.venue.modules.user.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统参数服务：读取 sys_config，本地缓存，管理端改动后调用 refresh 失效。
 */
@Service
@RequiredArgsConstructor
public class SysConfigService {

    private final SysConfigMapper sysConfigMapper;
    private final Map<String, Integer> cache = new ConcurrentHashMap<>();

    public int getInt(String key, int defaultValue) {
        Integer v = cache.get(key);
        if (v != null) {
            return v;
        }
        SysConfig config = sysConfigMapper.selectOne(
                Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, key));
        int value = defaultValue;
        if (config != null && StringUtils.hasText(config.getConfigValue())) {
            try {
                value = Integer.parseInt(config.getConfigValue().trim());
            } catch (NumberFormatException ignored) {
                // 配置值异常时回落默认值
            }
        }
        cache.put(key, value);
        return value;
    }

    public void refresh() {
        cache.clear();
    }

    /** 当前生效值（缓存未命中时读库） */
    public int getIntCached(String key, int defaultValue) {
        return getInt(key, defaultValue);
    }

    /**
     * 更新系统参数：写库（存在则更新、不存在则插入）并同步本地缓存，管理端保存后立即生效。
     */
    public int setInt(String key, int value, String remark) {
        SysConfig config = sysConfigMapper.selectOne(
                Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, key));
        LocalDateTime now = LocalDateTime.now();
        if (config == null) {
            config = new SysConfig();
            config.setConfigKey(key);
            config.setConfigValue(String.valueOf(value));
            config.setRemark(remark);
            config.setUpdateTime(now);
            sysConfigMapper.insert(config);
        } else {
            config.setConfigValue(String.valueOf(value));
            if (StringUtils.hasText(remark)) {
                config.setRemark(remark);
            }
            config.setUpdateTime(now);
            sysConfigMapper.updateById(config);
        }
        cache.put(key, value);
        return value;
    }
}
