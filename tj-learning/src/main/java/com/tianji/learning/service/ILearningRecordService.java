package com.tianji.learning.service;

import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordFormDTO;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningRecord;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 学习记录表 服务类
 * </p>
 *
 * @author author
 * @since 2026-10-23
 */
public interface ILearningRecordService extends IService<LearningRecord> {
    /**
     * 查询学习记录
     * @param courseId
     * @return
     */
    LearningLessonDTO queryLearningRecordByCourse(Long courseId);

    /**
     * 提交学习记录
     * @param learningRecordFormDTO
     */
    void submitLearningRecord(LearningRecordFormDTO learningRecordFormDTO);

}
