package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

import static com.tianji.common.constants.MqConstants.Exchange.LEARNING_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.SIGN_IN;
import static com.tianji.learning.constants.RedisConstants.SIGN_RECORD_KEY_PREFIX;

@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {
    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper rabbitMqHelper;
    /**
     * 签到
     * @return
     */
    @Override
    public SignResultVO addSignRecords() {
       //1  获取key
        //获取用户ID
        Long userId = UserContext.getUser();
        //获取日期
        LocalDate now = LocalDate.now();
        String key=SIGN_RECORD_KEY_PREFIX +userId+now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        // 获取offset
        int offset = now.getDayOfMonth()-1;
        //2 签到
        Boolean b = redisTemplate.opsForValue().setBit(key, offset, true);//true在redis被理解为1,返回的是原来的值
        if(BooleanUtils.isTrue(b)){
            throw new BizIllegalException("不可重复签到");
        }
      //3 获取连续签到奖励的积分和天数
      int signDays=countSignDays(key,now.getDayOfMonth());
        int reward=0;
        //连续签到7，14，28奖励
        switch (signDays) {
            case 7:
                reward=10;
                break;
            case 14:
                reward=20;
                break;
            case 28:
                reward=40;
                break;
            default:
                break;
        }
        //4 保存积分明细，发送签到mq
        rabbitMqHelper.send(
          LEARNING_EXCHANGE,
                SIGN_IN,
                SignInMessage.of(userId,reward+1)
        );

        //5 封装VO，返回
        SignResultVO vo=new SignResultVO();
        vo.setSignDays(signDays);
        vo.setRewardPoints(reward);
        return vo;

    }

    /**
     * 查询签到记录
     * @return
     */
    @Override
    public List<Long> querySignRecords() {
        // 1 获取用户id
        Long userId = UserContext.getUser();
        //2 获取偏移量
        //获取日期
        LocalDate now = LocalDate.now();
        String key=SIGN_RECORD_KEY_PREFIX +userId+now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        // 获取offset
        int offset = now.getDayOfMonth();
        //3 查询本月第一天到今天的签到记录
        List<Long> list = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().
                        get(BitFieldSubCommands.BitFieldType.unsigned(offset)).
                        valueAt(0));
        if(CollUtils.isEmpty(list)){
            return CollUtils.emptyList();
        }
        return list;

    }

    private int countSignDays(String key, int dayOfMonth) {
        //1 获取从第一天到今天的签到表
        List<Long> list = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().
                        get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).
                        valueAt(0));//List长度等于你提交的valueAt操作，所以这个list其实长度为1，这个数就是十进制数
        if(CollUtils.isEmpty(list)){
            return 0;
        }
        //2 从后到前遍历签到表，记录连续签到的天数
        int signDays=0;
        int num=list.get(0).intValue();
        while((num&1)==1){
            signDays++;
            num=num>>>1;//无符号右移
        }
        return signDays;
    }
}
