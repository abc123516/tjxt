package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.TableInfoContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.tianji.learning.constants.LearningConstants.POINTS_BOARD_TABLE_PREFIX;
import static com.tianji.learning.constants.RedisConstants.POINTS_BOARD_KEY_PERFIX;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-11-05
 */
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {

    /** 单页最大条数，防止前端传超大 pageSize 时一次从 Redis 拉取过多数据 */
    private static final int MAX_PAGE_SIZE = 100;

    private final UserClient userClient;
    private final StringRedisTemplate redisTemplate;
    /**
     * 查询积分榜
     * @param query
     * @return
     */
    @Override
    public PointsBoardVO getPointsBoard(PointsBoardQuery query) {
        Long season = query.getSeason();
        // 1 是否当前赛季：不传或传 0 都表示当前赛季
        boolean isCurrentSeason = season == null || season == 0L;
        LocalDateTime now = LocalDateTime.now();
        String key = POINTS_BOARD_KEY_PERFIX + now.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);

        // 2 先查询我的积分和排名，当前赛季查 Redis，历史赛季查分表
        PointsBoard myBoard = isCurrentSeason
                ? queryMyCurrentBoard(key)
                : queryMyHistoryBoard(season);

        // 3 再查询榜单数据，分页参数做兜底和上限控制
        int pageNo = query.getPageNo() == null ? 1 : Math.max(query.getPageNo(), 1);
        int pageSize = query.getPageSize() == null ? 10 : Math.min(query.getPageSize(), MAX_PAGE_SIZE);
        List<PointsBoard> list = isCurrentSeason
                ? queryCurrentBoardList(key, pageNo, pageSize)
                : queryHistoryBoardList(season, pageNo, pageSize);

        // 4 封装我的榜单信息
        PointsBoardVO vo = new PointsBoardVO();
        if (myBoard != null) {
            vo.setPoints(myBoard.getPoints());
            vo.setRank(myBoard.getRank());
        }
        // 没有榜单数据时也返回空集合，避免前端拿到 null
        if (CollUtils.isEmpty(list)) {
            vo.setBoardList(Collections.emptyList());
            return vo;
        }

        // 5 批量查询用户信息，一页只调用一次远程接口
        List<Long> userIds = list.stream()
                .map(PointsBoard::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, String> userMap = new HashMap<>(userIds.size());
        if (!CollUtils.isEmpty(userIds)) {
            List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
            if (!CollUtils.isEmpty(userDTOS)) {
                // 不用 Collectors.toMap，value 为 null 时它会抛 NPE
                for (UserDTO user : userDTOS) {
                    userMap.put(user.getId(), user.getName() == null ? "未知用户" : user.getName());
                }
            }
        }

        // 6 封装榜单列表
        List<PointsBoardItemVO> itemVOS = new ArrayList<>(list.size());
        for (PointsBoard r : list) {
            PointsBoardItemVO itemVO = new PointsBoardItemVO();
            itemVO.setRank(r.getRank());
            itemVO.setPoints(r.getPoints());
            itemVO.setName(userMap.get(r.getUserId()));
            itemVOS.add(itemVO);
        }
        vo.setBoardList(itemVOS);
        return vo;
    }

    /**
     * 根据赛季创建积分榜表
     * @param season
     */
    @Override
    public void createPointsBoardTableBySeason(Integer season) {
        getBaseMapper().createPointsBoardTableBySeason(POINTS_BOARD_TABLE_PREFIX+season);

    }

    private List<PointsBoard> queryHistoryBoardList(Long season, int pageNo, int pageSize) {
        //1 根据赛季获取积分榜表名
        String tableName = POINTS_BOARD_TABLE_PREFIX + season;
        //2 存到ThreadLocal
        TableInfoContext.setTableName(tableName);
        try {
            //3 查询积分榜数据
            Page<PointsBoard> page = lambdaQuery()
                    .orderByAsc(PointsBoard::getId)  //id即为排名
                    .page(new Page<>(pageNo, pageSize));
            List<PointsBoard> records = page.getRecords();
            if(CollUtils.isEmpty(records)){
                return Collections.emptyList();
            }
            records.forEach(r->r.setRank(r.getId().intValue())); //id即为排名,如果不设置，rank是null，因为查询表，rank已经不是这个表的字段了，不在会查出来
            return records;
        } finally {
            //4 清理ThreadLocal
            TableInfoContext.removeTableName();
        }
    }

    public List<PointsBoard> queryCurrentBoardList(String key, Integer pageNo, Integer pageSize) {
        //1 计算页首和页尾
        int start = (pageNo-1)*pageSize;
        int end = start+pageSize-1;
        //2 redis查询，附带分数和排名
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        int rank=start+1;  //比如start为0，那排名从1开始
        if(CollUtils.isEmpty(typedTuples)){
            return Collections.emptyList();
        }
        List<PointsBoard> list = new ArrayList<>(typedTuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            PointsBoard pointsBoard = new PointsBoard();
            pointsBoard.setRank(rank++);
            pointsBoard.setPoints((tuple.getScore()==null?0:tuple.getScore().intValue()));
            pointsBoard.setUserId(Long.valueOf(tuple.getValue()));
            list.add(pointsBoard);
        }
        //3 返回
        return list;

    }

    private PointsBoard queryMyHistoryBoard(Long season) {
       // 1 根据season获取积分榜表名
        String tableName = POINTS_BOARD_TABLE_PREFIX + season;
        //2 存到ThreadLocal
        TableInfoContext.setTableName(tableName);
        try {
            //3 查询当前用户在积分榜表中的积分和名次
            PointsBoard MyHistoryBoard = lambdaQuery()
                    .eq(PointsBoard::getUserId, UserContext.getUser())
                    .one();
            if(MyHistoryBoard==null){
              return null;
            }
            MyHistoryBoard.setRank(MyHistoryBoard.getId().intValue());//id就是名称
            return MyHistoryBoard;

        } finally {
            //4 清理ThreadLocal
            TableInfoContext.removeTableName();
        }
    }

    private PointsBoard queryMyCurrentBoard(String key) {
        // 1 获取当前用户Id
        Long userId = UserContext.getUser();
        String member = userId.toString();
        //3 获得积分值和名次
        Double point = redisTemplate.opsForZSet().score(key, member);  // 获取积分值
        Long rank = redisTemplate.opsForZSet().reverseRank(key, member);// 获取名次,返回0-end-1
        //4 返回：没上榜时 rank 保持 null，让前端自己决定显示“未上榜”还是隐藏
        PointsBoard pointsBoard = new PointsBoard();
        pointsBoard.setRank(rank == null ? null : rank.intValue() + 1);
        pointsBoard.setPoints(point == null ? 0 : point.intValue());
        return pointsBoard;
    }
}
