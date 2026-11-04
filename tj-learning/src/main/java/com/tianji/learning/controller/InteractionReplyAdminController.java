package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动问题的回答或评论 管理端控制器
 * </p>
 *
 * @author author
 * @since 2026-11-02
 */
@Api(tags="管理端回答接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/replies")
public class InteractionReplyAdminController {

    private final IInteractionReplyService replyService;

    /**
     * 管理端分页查询回答列表
     */
    @ApiOperation("管理端分页查询回答或评论")
    @GetMapping("/page")
    public PageDTO<ReplyVO> pageReplies(ReplyPageQuery query) {
        return replyService.pageRepliesForAdmin(query);
    }

    /**
     * 管理端隐藏或显示回答/评论
     * 注意：隐藏的是回答时，回答下的评论也会一起隐藏
     */
    @ApiOperation("管理端隐藏或显示回答或评论")
    @PutMapping("/{id}/hidden/{hidden}")
    public void updateHidden(@PathVariable("id") Long id,
                             @PathVariable("hidden") Boolean hidden) {
        replyService.updateReplyHidden(id, Boolean.TRUE.equals(hidden) ? 1 : 0);
    }
}
