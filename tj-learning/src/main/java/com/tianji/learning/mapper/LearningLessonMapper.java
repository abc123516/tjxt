package com.tianji.learning.mapper;

import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 学生课表 Mapper 接口
 * </p>
 *
 * @author author
 * @since 2026-10-20
 */
public interface LearningLessonMapper extends BaseMapper<LearningLesson> {
    /**
     * 统计本周计划学习的小节数
     * @param userId
     * @return
     */
    Integer queryTotalPlan(@Param("userId") Long userId);
}
