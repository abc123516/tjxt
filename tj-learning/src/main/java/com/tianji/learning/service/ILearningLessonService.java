package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import io.swagger.models.auth.In;

import java.util.List;

/**
 * <p>
 * 学生课表 服务类
 * </p>
 *
 * @author author
 * @since 2026-10-20
 */
public interface ILearningLessonService extends IService<LearningLesson> {
    /**
     * 添加用户课程
     * @param userId
     * @param courseIds
     */
    void addUserLessons(Long userId, List<Long> courseIds);

    /**
     * 分页查询我的课表
     * @param pageQuery
     * @return
     */
    PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery);

    /**
     * 查询我正在学习的课程
     * @return
     */
     LearningLessonVO queryRecentLessons();

    /**
     * 根据课程id查询课程
     * @param courseId
     * @return
     */
    LearningLessonVO queryLearningLessonById(Long courseId);

    /**
     * 删除用户课程
     * @param userId
     * @param courseIds
     */
    void removeUserLessons(Long userId, List<Long> courseIds);

    /**
     * 删除已失效的课程
     * @param courseId
     */
    void deleteLearningLesson(Long courseId);

    /**
     * 查询课程是否有效
     * @param courseId
     * @return
     */
    Long isLessonValid(Long courseId);

    /**
     * 统计课程的学习人数
     * @param courseId
     * @return
     */
    Integer countLearningLesson(Long courseId);

    /**
     * 根据用户id和课程id查询学习课程表
     * @param userId
     * @param courseId
     */
    LearningLesson queryByUserAndCourseId(Long userId, Long courseId);

    /**
     * 创建学习计划
     * @param learningPlanDTO
     */
    void createLearningPlan(LearningPlanDTO learningPlanDTO);

    /**
     * 查询我的学习计划
     * @param pageQuery
     * @return
     */
    LearningPlanPageVO queryMyLearningPlans(PageQuery pageQuery);
}
