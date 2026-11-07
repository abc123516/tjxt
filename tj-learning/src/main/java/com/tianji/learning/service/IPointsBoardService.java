package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoard;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardVO;

import java.util.List;

/**
 * <p>
 * 学霸天梯榜 服务类
 * </p>
 *
 * @author author
 * @since 2026-11-05
 */
public interface IPointsBoardService extends IService<PointsBoard> {
    /**
     * 查询积分榜
     * @param query
     * @return
     */
    PointsBoardVO getPointsBoard(PointsBoardQuery query);

    /**
     * 根据赛季创建积分榜
     * @param season
     */
    void createPointsBoardTableBySeason(Integer season);

    /**
     * 查询当前赛季的积分榜列表
     * @param key
     * @param pageno
     * @param pagesize
     */
    List<PointsBoard> queryCurrentBoardList(String key, Integer pageno, Integer pagesize);
}
