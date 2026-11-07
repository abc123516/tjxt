package com.tianji.learning.service.impl;

import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.domain.vo.PointsBoardSeasonVO;
import com.tianji.learning.mapper.PointsBoardSeasonMapper;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author author
 * @since 2026-11-05
 */
@Service
public class PointsBoardSeasonServiceImpl extends ServiceImpl<PointsBoardSeasonMapper, PointsBoardSeason> implements IPointsBoardSeasonService {
    /**
     * 查询赛季列表
     * @return
     */
    @Override
    public List<PointsBoardSeasonVO> querySeasonList() {
        List<PointsBoardSeason> list = list();
        if(CollUtils.isEmpty(list)){
            return CollUtils.emptyList();
        }
        return BeanUtils.copyList(list, PointsBoardSeasonVO.class);

    }

    /**
     * 根据时间获取赛季
     * @param time
     * @return
     */
    @Override
    public Integer getSeasonByTime(LocalDateTime time) {
        Optional<PointsBoardSeason> season = lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .oneOpt();
        return season.map(PointsBoardSeason::getId).orElse(null);
    }
}
