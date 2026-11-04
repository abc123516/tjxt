package com.tianji.remark.service.impl;


import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE;
import static com.tianji.remark.constants.RedisConstants.LIKE_BIZ_KEY_PREFIX;
import static com.tianji.remark.constants.RedisConstants.LIKE_TIMES_KEY_PREFIX;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-11-03
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {
    /**
     * 点赞或取消点赞
     * @param recordDTO
     */
    private final RabbitMqHelper rabbitMqHelper;

    private final StringRedisTemplate redisTemplate;
    @Override
    public void addLikeRecord(LikeRecordFormDTO recordDTO) {
        //1 根据liked判断是点赞还是取消点赞
      Boolean success= recordDTO.getLiked() ? addLike(recordDTO) : cancelLike(recordDTO);
      if(!success){
          return;
      }
        //2 统计点赞数,到redis里面查询
        String key=LIKE_BIZ_KEY_PREFIX+recordDTO.getBizId();
        Long LikeTimes = redisTemplate.opsForSet().size(key);
        if(LikeTimes==null){
            return;
        }
        //3 缓存当前业务的点赞数量到redis中
        String key1=LIKE_TIMES_KEY_PREFIX+recordDTO.getBizType();
        redisTemplate.opsForZSet().add(key1, recordDTO.getBizId().toString(), LikeTimes);
    }

    /**
     * 批量查询点赞状态
     * @param bizIds
     * @return
     */
    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        //1 获取当前登录用户
        Long userId = UserContext.getUser();
        //2 查询点赞状态，利用redis管道
        List<Object> objects = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Long bizId : bizIds) {
                String key = LIKE_BIZ_KEY_PREFIX + bizId;
                src.sIsMember(key, userId.toString());
            }
            return null;
        });
        //3 返回结果
        return IntStream.range(0, objects.size())//创建从0到集合size的流
                .filter(i -> (Boolean) objects.get(i))//过滤出点赞的业务id
                .mapToObj(i -> bizIds.get(i))//用角标i取bizIds中的对应数据，就是点赞过的id
                .collect(Collectors.toSet());//收集成Set


    }

    /**
     * 读取点赞次数并发送mq消息
     * @param bizType
     * @param maxBizSize
     */
    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {
        //1 读取缓存并删除缓存
        String key=LIKE_TIMES_KEY_PREFIX+bizType;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().popMax(key, maxBizSize);
        if(CollUtils.isEmpty(typedTuples)){  // 如果集合为空
            return;
        }
        //2 数据转换
        List<LikedTimesDTO> likedTimesDTOS = typedTuples.stream()
                .map(tuple -> new LikedTimesDTO()
                        .setBizId(Long.valueOf(tuple.getValue()))
                        .setLikedTimes(tuple.getScore().intValue()))
                .collect(Collectors.toList());


        //2 发送mq消息
        rabbitMqHelper.send(
                LIKE_RECORD_EXCHANGE,
                StringUtils.format(LIKED_TIMES_KEY_TEMPLATE, bizType),
                likedTimesDTOS
        );

    }

    /**
     *点赞
     * @param recordDTO
     * @return
     */
    private Boolean addLike(LikeRecordFormDTO recordDTO) {
        //获取用户id
        Long userId = UserContext.getUser();
        //插入点赞到redis
        String key=LIKE_BIZ_KEY_PREFIX+recordDTO.getBizId();//获取key
        Long res = redisTemplate.opsForSet().add(key, userId.toString());
        return res!=null &&res>0;


    }

    /**
     * 取消点赞
     * @param recordDTO
     * @return
     */
    private Boolean cancelLike(LikeRecordFormDTO recordDTO) {
        //删除点赞到redis，不需要判断是否点过赞，因为redis set数据结构是原子操作，有就删除，返回1，没有就无事发生，返回0
        String key=LIKE_BIZ_KEY_PREFIX+recordDTO.getBizId();
        Long res = redisTemplate.opsForSet().remove(key, UserContext.getUser().toString());
        return res!=null &&res>0;
    }
}
