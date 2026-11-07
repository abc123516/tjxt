package com.tianji.learning.service.impl;

import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.tianji.learning.constants.RedisConstants.POINTS_BOARD_KEY_PERFIX;
import static java.time.LocalDateTime.now;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-11-05
 */
@Service
@RequiredArgsConstructor
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {
    private final PointsRecordMapper pointsRecordMapper;
    private final StringRedisTemplate redisTemplate;
    /**
     * 添加积分记录
     * @param userId
     * @param points
     * @param type
     */
    @Override
    public void addPointsRecord(Long userId, Integer points, PointsRecordType type) {
        LocalDateTime now = now();
        //1 判断积分类型，是否有每天上限值
        int maxPoints = type.getMaxPoints();
        int realPoints=points;
        if(maxPoints>0){//1.1 如果有
            //获取这个用户当天已经获得的积分值
            LocalDateTime begin = DateUtils.getDayStartTime(now);
            LocalDateTime end = DateUtils.getDayEndTime(now);
            int todayPoints = pointsRecordMapper.getPointsByUserIdAndTypeAndTime(userId, type, begin, end);
            if(todayPoints>=maxPoints){ //1.1.1 如果有上限值，并且已经达到了上限值
                    return;
            }
            if(todayPoints+points>maxPoints){ //1.1.2 如果有上限值，并且没有达到上限值
               realPoints = maxPoints - todayPoints;
            }
        }
        //1.2 如果没有上限值，添加积分记录
        PointsRecord pointsRecord = new PointsRecord();
        pointsRecord.setUserId(userId);
        pointsRecord.setPoints(realPoints);
        pointsRecord.setType(type);
        pointsRecord.setCreateTime(now);
        save(pointsRecord);
        //1.2 累计积分到redis
        String key=POINTS_BOARD_KEY_PERFIX+now.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        redisTemplate.opsForZSet().incrementScore(key,userId.toString(),realPoints);  //累计积分，不能用add，add只是覆盖

    }

    /**
     * 查询今日积分情况
     * @return
     */
    @Override
    public List<PointsStatisticsVO> getTodayPointsStatistics() {
       //1 获取当前登录用户和今天的开始时间和结束时间
        Long userId = UserContext.getUser();
        LocalDateTime begin = DateUtils.getDayStartTime(DateUtils.now());
        LocalDateTime end = DateUtils.getDayEndTime(DateUtils.now());
        //2 查询今日积分情况 select sum(points) total,type from point_record where userid=xxx and begin<=creatime<=end group by type order by total
        List<PointsRecord> list = pointsRecordMapper.queryTodayPoints(userId,begin,end);
        if(CollUtils.isEmpty(list)){
            return CollUtils.emptyList();
        }
        //3 封装VO
        List<PointsStatisticsVO> vos=new ArrayList<>(list.size());
        for (PointsRecord record : list) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setPoints(record.getPoints());
            vo.setType(record.getType().getDesc());
            vo.setMaxPoints(record.getType().getMaxPoints());
            vos.add(vo);
        }
        return vos;
    }
}
