package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionReply;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;

/**
 * <p>
 * 互动问题的回答或评论 服务类
 * </p>
 *
 * @author author
 * @since 2026-10-31
 */
public interface IInteractionReplyService extends IService<InteractionReply> {
    /**
     * 新增回答或评论
     * @param replyDTO
     */
    void addReply(ReplyDTO replyDTO);

    /**
     * 分页查询回答或评论列表
     * @param query
     * @return
     */
    PageDTO<ReplyVO> getReplyPage(ReplyPageQuery query);

    /**
     * 管理端分页查询回答或评论列表
     * @param query
     * @return
     */
    PageDTO<ReplyVO> pageRepliesForAdmin(ReplyPageQuery query);

    /**
     * 管理端隐藏或显示回答/评论
     * @param id 回答或评论id
     * @param hidden 是否隐藏，1-隐藏，0-显示
     */
    void updateReplyHidden(Long id, Integer hidden);
}
