package com.tianji.learning.handler;

import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import static com.tianji.learning.constants.LearningConstants.POINTS_BOARD_TABLE_PREFIX;
import static com.tianji.learning.constants.RedisConstants.POINTS_BOARD_KEY_PERFIX;

@Component
@RequiredArgsConstructor
public class PointBoardPersistentHandler {
    private final IPointsBoardSeasonService pointsBoardSeasonService;
    private final StringRedisTemplate redisTemplate;
    private final IPointsBoardService pointsBoardService;
    /**
     * 定时任务，每月初动态生成赛季榜单表
     */
    @XxlJob("createTableJob") // 每月1号凌晨3点执行
    public void persistentPointBoard() {
        // 生成赛季榜单表
        //获取上个月的赛季id
        LocalDateTime time = LocalDateTime.now().minusMonths(1);// 获取上个月的当前时间
        Integer season = pointsBoardSeasonService.getSeasonByTime(time);
        if(season == null){
            return;
        }
        //存在，就创建上个赛季榜单表
        pointsBoardService.createPointsBoardTableBySeason(season);
    }
    /**
     * 定时任务：持久化redis历史榜单数据到DB
     *
     */
    @XxlJob("savePointsBoard2DB")
    public void savePointsBoard2DB() {
        //  1 获取上个月的今天的时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        //2 获取动态表名并设置到线程池中
        Integer season = pointsBoardSeasonService.getSeasonByTime(time);
        if(season == null){
            return;
        }
        TableInfoContext.setTableName(POINTS_BOARD_TABLE_PREFIX+season);
        //3  获取Key
        String key=POINTS_BOARD_KEY_PERFIX+time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        int index = XxlJobHelper.getShardIndex();  // 当前分片序号
        int total = XxlJobHelper.getShardTotal();  // 分片总数
        int pageno=index+1;
        int pagesize=10;
        //3 根据Key取出上个月的全部数据（分页取可以减轻redis压力）
        try {
            while(true){
                List<PointsBoard> pointsBoards = pointsBoardService.queryCurrentBoardList(key, pageno, pagesize);
                if(CollUtils.isEmpty(pointsBoards)){  //查完了，退出循环
                    break;
                }
                //4 遍历把rank设置为id
                pointsBoards.forEach(p -> p.setId(p.getRank().longValue()));
                //5 保存到DB，这里会被拦截器拦截，然后替换表名，而且会去ThreadLocal中获取表名
                pointsBoardService.saveBatch(pointsBoards);
                pageno+=total; //下一次翻页，跳过其他执行器执行过的页数
            }
        } finally { // 确保线程池中的表名被移除
            TableInfoContext.removeTableName();

        }

    }
    /**
     * 定时任务，清理Redis缓存任务
     */
    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis() {
        // 1. 获取上个月的今天的时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 3. 获取Key
        String key=POINTS_BOARD_KEY_PERFIX+time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        // 4. 删除Key
        redisTemplate.delete(key);
    }
}
