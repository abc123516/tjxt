package com.tianji.learning.task;

import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class LessonExpireTask {
    /**
     * 编写一个SpringTask定时任务，定期检查learning_lesson表中的课程是否过期，如果过期则将课程状态修改为已过期
     *
     */
    private final ILearningLessonService lessonService;
    @Scheduled(cron = "0 0 0 * * ?")              //每天凌晨十二点执行
    public void checkLessonExpire() {
        // TODO: 课程过期检查
        LocalDateTime now = LocalDateTime.now();
        boolean success = lessonService.lambdaUpdate()
                .le(LearningLesson::getExpireTime, now)  //已过期
                .ne(LearningLesson::getStatus, LessonStatus.EXPIRED)  //之前状态未过期
                .set(LearningLesson::getStatus, LessonStatus.EXPIRED)  //设置为已过期
                .update();

        log.info("课程过期检查完成，更新{}", success ? "成功" : "失败");
    }



}
