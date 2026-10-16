package com.campus.venue.modules.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.venue.modules.user.entity.SysConfig;
import com.campus.venue.modules.user.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
}
