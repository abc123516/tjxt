package com.tianji.learning.utils;


import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;


import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;

//学习记录任务延迟处理器
@Slf4j
@RequiredArgsConstructor
@Component
public class LearningRecordDelayTaskHandler {

    private final DelayQueue<DelayTask<RecordTaskData>> queue = new DelayQueue<>();
    private final StringRedisTemplate redisTemplate;
    private final static String RECORD_KEY_TEMPLATE = "learning:record:{}";
    private static volatile Boolean begin=true;
    private final ILearningLessonService learningLessonService;
    private final LearningRecordMapper learningRecordMapper;
    @PostConstruct
    public void init(){
        CompletableFuture.runAsync(this::execute);
    }
    @PreDestroy
    public void destroy(){
        begin=false;
        log.debug("取消延迟队列执行");
    }


   // 添加播放记录到redis,并且添加一个延迟检测任务道delayqueue
    public void addPlayRecord(LearningRecord learningRecord) {
      //1 添加数据到redis
      addToRedis(learningRecord);
      //2 添加延迟任务到delayqueue，延迟20秒执行
        queue.put(new DelayTask<>(new RecordTaskData(learningRecord), Duration.ofSeconds(20)));
     
    }
 // 仅添加播放记录到缓存，不投递延迟任务（用于查询到旧记录后预热缓存）
    public void addToRedis(LearningRecord learningRecord) {
        log.debug("更新缓存");
        try {
            //1 转换为JSON
            String json = JsonUtils.toJsonStr(new RecordCacheData(learningRecord));
            //2 获取Key
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, learningRecord.getLessonId());
            // 添加数据到redis,StringRedisTemplate 的 hash field 必须是 String,sectionId 是 Long 需要转换
            redisTemplate.opsForHash().put(key,learningRecord.getSectionId().toString(),json);
            // 添加过期时间
            redisTemplate.expire(key, Duration.ofMinutes(1));
        } catch (Exception e) {
            throw new RuntimeException("更新缓存异常", e);
        }
    }
    //查询Redis缓存中的指定小节的播放记录
    public LearningRecord getPlayRecord(Long lessonId, Long sectionId) {
        log.debug("查询缓存");
        try {
            //1 获取Key
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
            //2 获取JSON
            Object o = redisTemplate.opsForHash().get(key, sectionId.toString());
            if(o==null){
                return null;
            }
            //3 转换为对象
            LearningRecord record = JsonUtils.toBean(o.toString(), LearningRecord.class);
            //兜底：历史脏数据（序列化成了 {}）反序列化出来是字段全 null 的对象，
            //如果不处理，会被当成"记录已存在"，后续拿 null 的 id 去更新数据库，必然失败
            if (record == null || record.getId() == null) {
                log.warn("缓存中的播放记录无效，直接清理。key={}, sectionId={}, value={}", key, sectionId, o);
                redisTemplate.opsForHash().delete(key, sectionId.toString());
                return null;
            }
            return record;
        } catch (Exception e) {
            throw new RuntimeException("查询缓存异常", e);
        }
    }
     //删除Redis缓存中的指定小节的播放记录
    public void deletePlayRecord(Long lessonId, Long sectionId) {
        log.debug("删除缓存");
        try {
            //1 获取Key
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
            //2 删除数据
            redisTemplate.opsForHash().delete(key, sectionId.toString());
        } catch (Exception e) {
            throw new RuntimeException("删除缓存异常", e);
        }
    }

    //异步执行DelayQueue中的任务,检测播放进度是否变化，无变化写入数据库
    public void execute() {
        while(begin){

            try { //从延迟队列取出任务
                DelayTask<RecordTaskData> take = queue.take();
                RecordTaskData data = take.getData();
                //查询缓存
                LearningRecord cacheRecord = getPlayRecord(data.lessonId, data.sectionId);
                if(cacheRecord==null) {
                    log.debug("缓存已失效，放弃本次落库。lessonId={}, sectionId={}", data.lessonId, data.sectionId);
                    continue;
                }
                if(!Objects.equals(cacheRecord.getMoment(), data.moment)) {
                    log.debug("播放进度已变化，等下一个任务再落库。lessonId={}, sectionId={}, cacheMoment={}, taskMoment={}",
                            data.lessonId, data.sectionId, cacheRecord.getMoment(), data.moment);
                    continue;
                }
                //进度无变化，更新数据库
                log.debug("更新数据库。lessonId={}, sectionId={}, moment={}", data.lessonId, data.sectionId, data.moment);
                cacheRecord.setFinished(null);
                learningRecordMapper.updateById(cacheRecord); //更新学习进度,mp默认跳过空，非null字段更新，也就是不会处理finished
                LearningLesson learningLesson=new LearningLesson();
                //注意：cacheRecord 是从缓存反序列化来的,只存了 id/moment/finished,lessonId 和 sectionId 都是 null
                //所以主键必须取 data 里的,不能用 cacheRecord.getLessonId()
                learningLesson.setId(data.lessonId);
                learningLesson.setLatestSectionId(data.sectionId);
                learningLesson.setLatestLearnTime(LocalDateTime.now());
                boolean success = learningLessonService.updateById(learningLesson);//更新最新学习时间
                log.debug("更新最新学习小节。lessonId={}, latestSectionId={}, result={}", data.lessonId, data.sectionId, success);
            } catch (InterruptedException e) {
                //恢复中断标志,让线程能够正常退出
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                //单条任务处理失败不能拖死整个消费循环,否则后续所有学习记录都不会再落库
                log.error("处理学习记录延迟任务失败", e);
            }
        }
    }
    //静态内部类，其实就是你后面执行延迟任务，到redis里面查询需要用到的变量
    @Data
    @NoArgsConstructor
    private static class RecordTaskData {
        private Long lessonId;
        private Long sectionId;
        private Integer moment;

        public RecordTaskData(LearningRecord learningRecord) {
            this.lessonId = learningRecord.getLessonId();
            this.sectionId = learningRecord.getSectionId();
            this.moment = learningRecord.getMoment();
        }


    }

  //保存到redis里面的实体类
    @Data //没有getter,setter，redis里面的value会拿不到这个值
    @NoArgsConstructor
    private static class RecordCacheData  {
       private Long id;
       private Integer moment;
       private Boolean finished;

       public RecordCacheData(LearningRecord learningRecord) {
           this.id = learningRecord.getId();
           this.moment = learningRecord.getMoment();
           this.finished = learningRecord.getFinished();
       }
    }
}