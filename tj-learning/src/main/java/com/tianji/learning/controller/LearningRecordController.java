package com.tianji.learning.controller;


import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordFormDTO;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.service.ILearningRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学习记录表 前端控制器
 * </p>
 *
 * @author author
 * @since 2026-10-23
 */
@Api(tags="学习记录接口")
@RestController
@RequestMapping("/learning-records")
@RequiredArgsConstructor
public class LearningRecordController {
    private final ILearningRecordService learningRecordService;
    @ApiOperation("查询学习记录")
    @GetMapping("/course/{courseId}")
    LearningLessonDTO queryLearningRecordByCourse(@PathVariable("courseId") Long courseId){
        return learningRecordService.queryLearningRecordByCourse(courseId);
    }

    @ApiOperation("提交学习记录")
    @PostMapping
    void submitLearningRecord(@RequestBody LearningRecordFormDTO learningRecordFormDTO){
        learningRecordService.submitLearningRecord(learningRecordFormDTO);
    }


}
