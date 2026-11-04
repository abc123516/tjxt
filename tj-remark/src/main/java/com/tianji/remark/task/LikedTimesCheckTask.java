package com.tianji.remark.task;

import com.tianji.common.utils.CollUtils;
import com.tianji.remark.config.LikedProperties;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 点赞数同步任务
 * <p>
 * 业务类型不再写死在代码里，而是从 Nacos 配置 tj.remark.liked.biz-types 中读取，
 * 新增业务类型只需修改配置，无需改动代码和重启服务。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikedTimesCheckTask {

    private final ILikedRecordService recordService;
    private final LikedProperties likedProperties;

    @Scheduled(fixedDelay = 20000)
    public void checkLikedTimes() {
        Map<String, String> bizTypes = likedProperties.getBizTypes();
        // 未配置业务类型时直接跳过，避免空转
        if (CollUtils.isEmpty(bizTypes)) {
            log.warn("未配置点赞业务类型 tj.remark.liked.biz-types，本次跳过");
            return;
        }
        for (String bizType : bizTypes.keySet()) {
            // 具体业务交给service实现
            recordService.readLikedTimesAndSendMessage(bizType, likedProperties.getMaxBizSize());
        }
    }
}
