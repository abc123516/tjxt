package com.tianji.learning.service.impl;

import cn.hutool.core.io.unit.DataUnit;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.*;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课表 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-10-20
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {
    /**
     * 添加用户课程
     * @param userId
     * @param courseIds
     */
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final LearningRecordMapper learningRecordMapper;
    @Override
    public void addUserLessons(Long userId, List<Long> courseIds) {
        //1.根据课程IDS查询课程信息
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(courseIds);
        if(CollUtils.isEmpty(cInfoList)) {
            log.error("课程信息为空");
            return;
        }

        //2.使用流式编程封装LearningLesson
        List<LearningLesson> learningLessons = cInfoList.stream().map(cinfolist ->
        {
            LearningLesson learningLesson = new LearningLesson();
            Integer validDuration = cinfolist.getValidDuration();
            if (validDuration != null && validDuration > 0) {
                learningLesson.setExpireTime(LocalDateTime.now().plusMonths(validDuration));
            }
            learningLesson.setCreateTime(LocalDateTime.now());
            learningLesson.setUserId(userId);
            learningLesson.setCourseId(cinfolist.getId());
            return learningLesson;
        }).collect(Collectors.toList());
        //3.批量插入
        saveBatch(learningLessons);
    }

    /**
     * 分页查询我的课表
     * @param pageQuery
     * @return
     */
    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery) {
        //1 获取当前用户ID
        Long userId = UserContext.getUser();
        //2 根据用户ID查询学习课程表（我的课表）  select * from learning_lesson where useID=user_id order by xxxx limit xx,xx
        Page<LearningLesson> page = lambdaQuery().eq(LearningLesson::getUserId, userId)
                .page(pageQuery.toMpPage("latest_learn_time", false));

        List<LearningLesson> records= page.getRecords();
        if(CollUtils.isEmpty(records))
            return PageDTO.empty(page);
        Map<Long, CourseSimpleInfoDTO> cInfoMap = getLongCourseSimpleInfoDTOMap(records);
        //3 封装成VO
        List<LearningLessonVO> learningLessonVOS = new ArrayList<>(records.size());
        for (LearningLesson record : records) {
            LearningLessonVO vo = BeanUtils.copyBean(record, LearningLessonVO.class);
            CourseSimpleInfoDTO courseInfo = cInfoMap.get(record.getCourseId());
            vo.setCourseName(courseInfo.getName());//添加课程名称
            vo.setCourseCoverUrl(courseInfo.getCoverUrl()); // 添加课程封面URL
            vo.setSections(courseInfo.getSectionNum()); // 添加课程章节数量
            learningLessonVOS.add(vo);
        }

        //4 返回分页数据
        return PageDTO.of(page,learningLessonVOS);
    }

    /**
     * 根据records查询课程信息
     * @param records
     * @return
     */
    private Map<Long, CourseSimpleInfoDTO> getLongCourseSimpleInfoDTOMap(List<LearningLesson> records) {
        //先获取课程ID，根据课程ID查询课程信息
        List<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toList());
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(courseIds);
        if(CollUtils.isEmpty(cInfoList)){
            log.error("课程信息为空");
            throw new BadRequestException("课程信息为空");
        }
        //把cInfoList转成以课程ID为key，本身为值的Map
        Map<Long, CourseSimpleInfoDTO> cInfoMap = cInfoList.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, cInfo -> cInfo));
        return cInfoMap;
    }

    /**
     * 查询我正在学习的课程
     * @return
     */
    @Override
    public  LearningLessonVO queryRecentLessons() {
        //1 先获取当前用户ID
        Long userId = UserContext.getUser();
        //2 根据当前用户ID，课程状态为学习中查询学习课程表（我的课表）
        LearningLesson learningLesson = lambdaQuery().eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING)
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if(learningLesson == null){
            return null;
        }
        //根据课程ID查课程表
        CourseFullInfoDTO courseInfo = courseClient.getCourseInfoById(learningLesson.getCourseId(), false, false);

        //4 封装成VO
        LearningLessonVO vo = BeanUtils.copyBean(learningLesson, LearningLessonVO.class);
        vo.setCourseName(courseInfo.getName());//课程名
        vo.setCourseCoverUrl(courseInfo.getCoverUrl());//课程封面
        vo.setSections(courseInfo.getSectionNum());//课程章节数量
        //查询总已报名课程数  select count(*) from learning_lesson where user_id=#{userId}
        Integer courseAmount = lambdaQuery().eq(LearningLesson::getUserId, userId).count();
        vo.setCourseAmount(courseAmount);//总已报名课程数
        //查询小节信息
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(CollUtils.singletonList(learningLesson.getLatestSectionId()));
        if(!CollUtils.isEmpty(cataSimpleInfoDTOS)) {
            vo.setLatestSectionName(cataSimpleInfoDTOS.get(0).getName());//取第一个,小节名，就是最近学习的小节名
            vo.setLatestSectionIndex(cataSimpleInfoDTOS.get(0).getCIndex());//取第一个,小节编号，就是最近学习的小节编号
        }
        return vo;//返回VO



    }

    /**
     * 根据课程ID查询学习课程表
     * @param courseId
     * @return
     */
    @Override
    public LearningLessonVO queryLearningLessonById(Long courseId) {
        //1 获取当前用户ID
        Long userId = UserContext.getUser();
        //2 根据用户ID和课程ID查询学习表
        LearningLesson learningLesson = lambdaQuery().eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if(learningLesson==null){
            log.error("课程不存在");
            throw new BadRequestException("课程不存在");
        }
        //3 根据课程ID查询课程信息
        CourseFullInfoDTO courseInfo = courseClient.getCourseInfoById(courseId, false, false);
        //3 封装VO
        LearningLessonVO vo = BeanUtils.copyBean(learningLesson, LearningLessonVO.class);
        //查询总已报名课程数  select count(*) from learning_lesson where user_id=#{userId}
        Integer courseAmount = lambdaQuery().eq(LearningLesson::getUserId, userId).count();
        vo.setCourseAmount(courseAmount);//总已报名课程数
        vo.setCourseName(courseInfo.getName());
        vo.setCourseCoverUrl(courseInfo.getCoverUrl());
        vo.setSections(courseInfo.getSectionNum());
        //查询小节信息
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(CollUtils.singletonList(learningLesson.getLatestSectionId()));
        if(!CollUtils.isEmpty(cataSimpleInfoDTOS)) {
            vo.setLatestSectionName(cataSimpleInfoDTOS.get(0).getName());//取第一个,小节名，就是最近学习的小节名
            vo.setLatestSectionIndex(cataSimpleInfoDTOS.get(0).getCIndex());//取第一个,小节编号，就是最近学习的小节编号
        }
        return vo;

    }

    /**
     * 删除用户课程
     * @param userId
     * @param courseIds
     */
    @Override
    public void removeUserLessons(Long userId, List<Long> courseIds) {
        boolean sucess = lambdaUpdate().eq(LearningLesson::getUserId, userId)
                .in(LearningLesson::getCourseId, courseIds)
                .remove();
        if(sucess){
            log.info("删除成功");
        }

    }

    /**
     * 删除已失效的课程
     * @param courseId
     */
    @Override
    public void deleteLearningLesson(Long courseId) {
        //1 获取当前用户ID
        Long userId = UserContext.getUser();
        //2 根据用户ID和课程ID删除
        boolean sucess = lambdaUpdate().eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .remove();
        if(sucess){
            log.info("删除成功");
        }
    }

    /**
     * 查询课程是否有效
     * @param courseId
     * @return
     */
    @Override
    public Long isLessonValid(Long courseId) {
       //1  获取当前登录用户
        Long userId = UserContext.getUser();
        if(userId==null){
            log.error("用户未登录");
            throw new BadRequestException("用户未登录");
        }
        //2根据用户ID，课程ID，是否过期，判断该课程是否能查到
        LearningLesson lesson = lambdaQuery().eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                // .eq(LearningLesson::getExpireTime, null)   //不能这样判断null,如果不是Null,返回的是fasle,导致查不到数据
                .one();
        if(lesson==null){
            log.error("课程不存在");
            throw new BadRequestException("课程不存在");
        }
        LocalDateTime expireTime = lesson.getExpireTime();//课程过期时间
        if(expireTime!=null&&LocalDateTime.now().isAfter(expireTime)) {
            log.error("课程已过期");
            throw new BadRequestException("课程已过期");
        }
        //3 如果可以，返回课表ID
        return lesson.getId();
    }

    /**
     * 统计课程的学习人数
     * @param courseId
     * @return
     */
    @Override
    public Integer countLearningLesson(Long courseId) {
        //select count(*) from learning_lesson where courseId=#{courseId}
        return lambdaQuery().eq(LearningLesson::getCourseId, courseId).count();
    }

    /**
     * 根据用户id和课程id查询学习表
     * @param userId
     * @param courseId
     */
    @Override
    public LearningLesson queryByUserAndCourseId(Long userId, Long courseId) {
        LearningLesson lesson = lambdaQuery().eq(LearningLesson::getUserId, userId).eq(LearningLesson::getCourseId, courseId).one();
        if(lesson==null){
            log.error("课程不存在");
            throw new BadRequestException("课程不存在");
        }
        return lesson;
    }

    /**
     * 创建学习计划
     * @param learningPlanDTO
     */
    @Override
    public void createLearningPlan(LearningPlanDTO learningPlanDTO) {
        //1 获取当前登录用户
        Long userId = UserContext.getUser();
        //2 查询课表中的指定课程有关的数据
        LearningLesson learningLesson = queryByUserAndCourseId(userId, learningPlanDTO.getCourseId());
        AssertUtils.isNotNull(learningLesson, "课程不存在");
        //3 更新学习计划
    boolean success=lambdaUpdate()
                .set(LearningLesson::getWeekFreq,learningPlanDTO.getFreq()) // 设置每周学习频率
                .set(learningLesson.getPlanStatus().equals(PlanStatus.NO_PLAN),LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING) //如果当前状态是未计划，则更新为计划中
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, learningPlanDTO.getCourseId())
                .update();
        if(success){
            log.info("更新成功");
        }

    }

    /**
     * 查询我的学习计划
     * @param pageQuery
     * @return
     */
    @Override
    public LearningPlanPageVO queryMyLearningPlans(PageQuery pageQuery) {
        LearningPlanPageVO learningPlanPageVO = new LearningPlanPageVO();
      //1 获取当前登录用户
        Long userId = UserContext.getUser();
        if(userId==null){
            throw new BadRequestException("用户未登录");
        }
        LocalDate now = LocalDate.now();
        LocalDateTime begin = DateUtils.getWeekBeginTime(now);
        LocalDateTime end = DateUtils.getWeekEndTime(now);

        //2 查询学习记录表,获取我本周学习数据
        //2.1 获取本周已学完小节数量  select count(1) from learning_record where user_id=#{userId} and finished=true and finish_time>#{begin} and finish_time<#{end}
        Integer weekFinished = learningRecordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .gt(LearningRecord::getFinishTime, begin)
                .lt(LearningRecord::getFinishTime, end)
        );
       learningPlanPageVO.setWeekFinished(weekFinished);
        //2.2 获取本周计划学习小节数量,查询学习课程表，求和week_feq这个元素
        Integer weekTotalPlan = getBaseMapper().queryTotalPlan(userId);
        learningPlanPageVO.setWeekTotalPlan(weekTotalPlan);
        //2.3 TODO 获取本周学习积分
        //3  查询学习课程表和课程表，获取本周计划学习的每个课程的学习进度
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .page(pageQuery.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return learningPlanPageVO.pageInfo(page.getTotal(),page.getPages(),CollUtils.emptyList());
        }
        List<LearningPlanVO> voList = new ArrayList<>(records.size());
        //3.1 获取课程信息，并转成map集合
        Map<Long, CourseSimpleInfoDTO> simpleInfoDTOMap = getLongCourseSimpleInfoDTOMap(records);
        //3.2  查询学习记录表，获取本周学习的小节数量
        List<IdAndNumDTO> learningRecords = learningRecordMapper.selectWeekLearnedSections(userId, begin, end);
        Map<Long, Integer> map = IdAndNumDTO.toMap(learningRecords);
        for (LearningLesson record : records) {
            LearningPlanVO vo = BeanUtils.copyBean(record, LearningPlanVO.class);//拷贝基础属性
            CourseSimpleInfoDTO courseInfo = simpleInfoDTOMap.get(record.getCourseId());
            if(courseInfo!=null){
                vo.setCourseName(courseInfo.getName());//课程名
                vo.setSections(courseInfo.getSectionNum());//课程小节数
            }
            vo.setWeekLearnedSections(map.getOrDefault(record.getId(), 0));//本周学习小节数
            voList.add(vo);
        }

        return learningPlanPageVO.pageInfo(page.getTotal(),page.getPages(),voList);



    }
}

