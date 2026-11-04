package com.tianji.remark.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 点赞业务配置
 * <p>
 * 对应 Nacos 中 remark-service.yaml 的 tj.remark.liked 节点，支持动态刷新。
 * 修改 Nacos 配置后无需重启服务，下一次定时任务执行时即生效。
 * </p>
 *
 * @author author
 * @since 2026-11-04
 */
@Data
@Component
@ConfigurationProperties(prefix = "tj.remark.liked")
public class LikedProperties {

    /**
     * 业务类型：key 为 bizType，value 为业务描述
     * <p>
     * 用 ConcurrentHashMap 而非 HashMap，因为定时任务线程在遍历时，
     * Nacos 配置刷新线程可能同时在修改，普通 HashMap 会抛 ConcurrentModificationException。
     * </p>
     */
    private Map<String, String> bizTypes = new ConcurrentHashMap<>();

    /**
     * 每种业务类型单次批量处理的最大条数
     */
    private int maxBizSize = 30;
}
