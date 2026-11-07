package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoardSeason;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.PointsBoardSeasonVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author author
 * @since 2026-11-05
 */
public interface IPointsBoardSeasonService extends IService<PointsBoardSeason> {
    /**
     * 查询赛季列表
     * @return
     */
    List<PointsBoardSeasonVO> querySeasonList();

    /**
     * 根据时间获取赛季
     * @param time
     * @return
     */
    Integer getSeasonByTime(LocalDateTime time);
}
