package com.tianji.learning.mapper;

import com.tianji.learning.domain.po.PointsRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.learning.enums.PointsRecordType;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 Mapper 接口
 * </p>
 *
 * @author author
 * @since 2026-11-05
 */
public interface PointsRecordMapper extends BaseMapper<PointsRecord> {

    Integer getPointsByUserIdAndTypeAndTime(@Param("userId") Long userId,
                                            @Param("type") PointsRecordType type,
                                            @Param("begin") LocalDateTime begin,
                                            @Param("end") LocalDateTime end);

    List<PointsRecord> queryTodayPoints( @Param("userId") Long userId,
                                         @Param("begin") LocalDateTime begin,
                                         @Param("end") LocalDateTime end);
}
