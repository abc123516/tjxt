package com.tianji.learning.mq;

import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.LIKED_TIMES_KEY_ALL;
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeTimesChangeListener {
    /**
     * 更新点赞数
     * @param likedTimesDTO
     */
    private final IInteractionReplyService replyService;
     @RabbitListener(bindings = @QueueBinding(
             value = @Queue(value = "learning.liked.times.queue", durable = "true"),
             exchange = @Exchange(name=LIKE_RECORD_EXCHANGE,type = ExchangeTypes.TOPIC),
             key = LIKED_TIMES_KEY_ALL
     ))
    public void updateLikedTimes(List<LikedTimesDTO> likedTimesDTOs) {
          // 更新点赞数
         List<InteractionReply> replies=new ArrayList<>(likedTimesDTOs.size());
         likedTimesDTOs.forEach(likedTimesDTO -> {
             InteractionReply interactionReply = new InteractionReply();
             interactionReply.setLikedTimes(likedTimesDTO.getLikedTimes());
             interactionReply.setId(likedTimesDTO.getBizId());
             replies.add(interactionReply);
         });
         replyService.updateBatchById(replies);

    }
}
