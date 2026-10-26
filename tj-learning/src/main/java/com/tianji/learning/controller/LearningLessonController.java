package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.units.qual.A;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * <p>
 * 学生课表 前端控制器
 * </p>
 *
 * @author author
 * @since 2026-10-20
 */
@Api(tags = "我的课表相关接口")
@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
public class LearningLessonController {

    private final ILearningLessonService learningLessonService;
    @ApiOperation("分页查询我的课表")
   @GetMapping("/page")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery){
        return learningLessonService.queryMyLessons(pageQuery);
    }

    @ApiOperation("查询我最近正在学习的课程")
    @GetMapping("/now")
    public LearningLessonVO queryRecentLessons(){
        return learningLessonService.queryRecentLessons();
    }

    @ApiOperation("根据id查询指定课程的学习状态")
    @GetMapping("/{courseId}")
    public LearningLessonVO queryLearningLessonById(@PathVariable Long courseId){
        return learningLessonService.queryLearningLessonById(courseId);
    }

    @ApiOperation("删除已失效的课程")
    @DeleteMapping("/{courseId}")
    public void deleteLearningLesson(@PathVariable Long courseId){
        learningLessonService.deleteLearningLesson(courseId);
    }
    @ApiOperation("检查课程是否有效")
    @GetMapping("/{courseId}/valid")
    public Long isLessonValid(@PathVariable Long courseId){
        return learningLessonService.isLessonValid(courseId);
    }
    @ApiOperation(("统计课程的学习人数"))
    @GetMapping("/{courseId}/count")
    public Integer countLearningLesson(@PathVariable Long courseId){
        return learningLessonService.countLearningLesson(courseId);
    }

    @ApiOperation("创建学习计划")
    @PostMapping("/plans")
    void createLearningPlan(@Valid @RequestBody LearningPlanDTO learningPlanDTO){
        learningLessonService.createLearningPlan(learningPlanDTO);
    }
    @ApiOperation("查询我的学习计划")
    @GetMapping("/plans")
    LearningPlanPageVO queryMyLearningPlans(PageQuery pageQuery){
        return learningLessonService.queryMyLearningPlans(pageQuery);
    }

}
