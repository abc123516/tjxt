package com.tianji.remark.service;

import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 服务类
 * </p>
 *
 * @author author
 * @since 2026-11-03
 */
public interface ILikedRecordService extends IService<LikedRecord> {
    /**
     * 点赞或取消点赞
     * @param recordDTO
     */
    void addLikeRecord(LikeRecordFormDTO recordDTO);

    /**
     * 批量查询点赞状态
     * @param bizIds
     * @return
     */
    Set<Long> isBizLiked(List<Long> bizIds);

    /**
     * 读取点赞次数并发送mq消息
     * @param bizType
     * @param maxBizSize
     */
    void readLikedTimesAndSendMessage(String bizType, int maxBizSize);
}
