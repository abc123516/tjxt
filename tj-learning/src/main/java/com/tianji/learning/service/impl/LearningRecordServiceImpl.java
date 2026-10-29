package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.api.dto.leanring.LearningRecordFormDTO;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-10-23
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {
    /**
     * 查询学习记录
     * @param courseId
     * @return
     */
    private final ILearningLessonService learningLessonService;

    private final CourseClient courseClient;

    private final LearningRecordDelayTaskHandler learningRecordDelayTaskHandler;
    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
       //1  获取当前登录用户
        Long userId = UserContext.getUser();
        //2 查询学习表，根据课程id和用户Id;获取课表id
        LearningLesson learningLesson = learningLessonService.queryByUserAndCourseId(userId, courseId);
        //3  根据课表id查询学习记录表
        List<LearningRecord> learningRecords = lambdaQuery().eq(LearningRecord::getLessonId, learningLesson.getId()).list();
        LearningLessonDTO learningLessonDTO = new LearningLessonDTO();
        learningLessonDTO.setId(learningLesson.getId());//课表id
        learningLessonDTO.setLatestSectionId(learningLesson.getLatestSectionId());//最近学习的小节id
        //4  将学习记录表转换为LearningRecordDTO
        List<LearningRecordDTO> recordDTOS = learningRecords.stream().map(learningRecord -> {
            LearningRecordDTO learningRecordDTO = new LearningRecordDTO();
            learningRecordDTO.setSectionId(learningRecord.getSectionId());//小节ID
            learningRecordDTO.setMoment(learningRecord.getMoment());//视频播放进度
            learningRecordDTO.setFinished(learningRecord.getFinished());//是否完成学习
            return learningRecordDTO;
        }).collect(Collectors.toList());
        learningLessonDTO.setRecords(recordDTOS);//封装records

        //5 返回
        return learningLessonDTO;


    }

    /**
     * 提交学习记录
     * @param learningRecordFormDTO
     */
    @Override
    public void submitLearningRecord(LearningRecordFormDTO learningRecordFormDTO) {
        //1 获取当前登录用户ID
        Long userId = UserContext.getUser();
        if(userId == null){
            throw new RuntimeException("用户未登录");
        }
        //2 判断小节类型，视频？考试？
        boolean finished=false;//是否全部小节学完
        if(learningRecordFormDTO.getSectionType()== SectionType.EXAM.getValue()){
            //2.1 如果是考试
             finished = handleExam(userId, learningRecordFormDTO);
        }else{
            //2.2 如果是视频
            finished=handleVideo(userId,learningRecordFormDTO);

        }
        if(!finished){ //没有新学完的小节，什么都不用做，因为异步执行已经会更新最新学习小节和id了
            return;
        }
        //3 如果学完，更新课表状态为已学完
        handleLearningLesson(learningRecordFormDTO);

    }
    private void handleLearningLesson(LearningRecordFormDTO learningRecordFormDTO) {
        // 1 根据课程id查询学习课程表
        LearningLesson lesson = learningLessonService.getById(learningRecordFormDTO.getLessonId());
        if (lesson == null) {
            throw new DbException("课程不存在");
        }

        boolean allfinished = false;
        //2 判断是否有新学完的小节，如果有，在判断是否学完全部小节
        //查询课程表
        CourseFullInfoDTO courseInfoById = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (courseInfoById == null) {
            throw new DbException("课程不存在");
        }
        allfinished = lesson.getLearnedSections() + 1 >= courseInfoById.getSectionNum(); //判断是否学完全部小节

        //3 更新学习课程表
        learningLessonService.lambdaUpdate()
                .setSql("learned_sections = learned_sections + 1")//学完小节数加1
                .set(lesson.getLearnedSections() == 0 && !allfinished, LearningLesson::getStatus, LessonStatus.LEARNING.getValue()) //如果是未学习的，那状态改为学习中
                .set(allfinished, LearningLesson::getStatus, LessonStatus.FINISHED)  //如果全部学完，更新课程状态为已学完
                .eq(LearningLesson::getId, lesson.getId())
                .update();
    }

    private Boolean handleExam(Long userId,LearningRecordFormDTO learningRecordFormDTO) {
        //新增学习记录
        LearningRecord record = BeanUtils.copyBean(learningRecordFormDTO, LearningRecord.class);
        record.setUserId(userId);//用户ID
        record.setFinished(true);//已完成
        record.setFinishTime(learningRecordFormDTO.getCommitTime());//完成时间
        boolean success = save(record);
        if(!success){
            throw new DbException("新增K考试记录失败");
        }

        return true;
    }

    private Boolean handleVideo(Long userId,LearningRecordFormDTO learningRecordFormDTO) {
        //1 学习记录是否存在
        LearningRecord oldRecord=queryOldRecord(learningRecordFormDTO.getLessonId(), learningRecordFormDTO.getSectionId());
        if(oldRecord==null){
            //1.1如果不存在，新增学习记录
            LearningRecord newRecord = BeanUtils.copyBean(learningRecordFormDTO, LearningRecord.class);
            newRecord.setUserId(userId);
            boolean success = save(newRecord);
            if(!success){
                throw new DbException("新增学习记录失败");
            }
            return false;//返回false，表示未学完全部小节

        }
           //1.2 如果已经存在，更新学习记录
            //判断是不是第一次学完；旧状态是未完成的且这一次视频播放进度超过50%，那就说明这次学完了
            //注意:moment、duration 都是 Integer,前端有些场景(比如刚打开播放页)不会传,拆箱会抛 NPE,这里先做防御
            Integer currentMoment = learningRecordFormDTO.getMoment();
            Integer duration = learningRecordFormDTO.getDuration();
            boolean finished = !Boolean.TRUE.equals(oldRecord.getFinished())
                    && currentMoment != null && duration != null && duration > 0
                    && currentMoment * 2 >= duration;
            if(!finished){//如果不是第一次学完，走异步执行，缓存到redis,并且提交延迟检测任务
                //封装当前学习记录
                LearningRecord record = BeanUtils.copyBean(learningRecordFormDTO, LearningRecord.class);
                record.setId(oldRecord.getId());
                record.setFinished(oldRecord.getFinished());
                learningRecordDelayTaskHandler.addPlayRecord(record);
                return false;
            }
            //是第一次学完
            boolean success = lambdaUpdate()
                    .set(LearningRecord::getMoment, learningRecordFormDTO.getMoment()) //视频当前观看时间点
                    .set(LearningRecord::getFinished, true)//如果为true就说明是已完成，就改为ture,否则这列不用动
                    .set(LearningRecord::getFinishTime, learningRecordFormDTO.getCommitTime())//为ture,修改当前完成时间
                    .eq(LearningRecord::getId, oldRecord.getId())
                    .update();
            //第一次完成，清理缓存，因为数据库和redis不一致了，所以直接清除就可以
            learningRecordDelayTaskHandler.deletePlayRecord(learningRecordFormDTO.getLessonId(), learningRecordFormDTO.getSectionId());
            if(!success){
                throw new DbException("更新学习记录失败");
            }
            return finished;
    }
     //查询旧记录
    private LearningRecord queryOldRecord(Long lessonId, Long sectionId) {
        //1 查询缓存
        LearningRecord learningRecord = learningRecordDelayTaskHandler.getPlayRecord(lessonId, sectionId);
        if(learningRecord!=null){
            return learningRecord;
        }
        //缓存为空，查询数据库
        learningRecord = lambdaQuery().eq(LearningRecord::getLessonId, lessonId)
                .eq(LearningRecord::getSectionId, sectionId)
                .one();
        if (learningRecord == null) {
            //数据库里也没有,说明是第一次学习,直接返回null交给上层新增记录,不能写缓存
            return null;
        }
        //仅预热缓存，不投递延迟任务（延迟任务由 handleVideo 在进度变化时投递）
        learningRecordDelayTaskHandler.addToRedis(learningRecord);
        return learningRecord;
    }

}
