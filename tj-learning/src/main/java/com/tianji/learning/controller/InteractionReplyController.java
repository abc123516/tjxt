package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author author
 * @since 2026-10-31
 */
@Api(tags="评论相关接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/replies")
public class InteractionReplyController {
    private final IInteractionReplyService replyService;

    /**
     * 新增回答或评论
     * @param replyDTO
     */
    @ApiOperation("新增回答或评论")
    @PostMapping

    public void addReply(@Valid @RequestBody ReplyDTO replyDTO){
        replyService.addReply(replyDTO);
    }

    /**
     * 分页查询回答或评论列表
     */
    @ApiOperation("分页查询回答或评论列表")
    @GetMapping("/page")
    public PageDTO<ReplyVO> getReplyPage(ReplyPageQuery query){
        return replyService.getReplyPage(query);
    }


}
