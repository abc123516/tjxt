package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;

import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务类
 * </p>
 *
 * @author author
 * @since 2026-11-05
 */
public interface IPointsRecordService extends IService<PointsRecord> {
    /**
     * 添加积分记录
     * @param userId
     * @param points
     * @param pointsRecordType
     */
    void addPointsRecord(Long userId, Integer points, PointsRecordType pointsRecordType);

    /**
     * 查询今日积分情况
     * @return
     */
    List<PointsStatisticsVO> getTodayPointsStatistics();
}
