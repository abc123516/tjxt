package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author author
 * @since 2026-10-31
 */
@Api(tags="管理端问题接口")
@RestController
@RequestMapping("/admin/questions")
@RequiredArgsConstructor
public class InteractionQuestionAdminController {
    /**
     * 管理端分页查询问题
     */
    private final IInteractionQuestionService questionService;
    @ApiOperation("管理端分页查询问题")
    @GetMapping("/page")
    public PageDTO<QuestionAdminVO> pageQuestions(QuestionAdminPageQuery query) {
        return questionService.queryQuestionPageAdmin(query);
    }
    /**
     * 管理端隐藏或显示问题
     */
    @ApiOperation("管理端隐藏或显示问题")
    @PutMapping("/{id}/hidden/{hidden}")
    public void updateStatus(@PathVariable("id") Long id, @PathVariable("hidden") Boolean hidden) {
        questionService.updateHidden(id, Boolean.TRUE.equals(hidden) ? 1 : 0);
    }
    /**
     * 管理端根据Id查询问题详情
     */
    @ApiOperation("管理端根据Id查询问题详情")
    @GetMapping("/{id}")
    public QuestionAdminVO getQuestionAdminById(@PathVariable("id") Long id) {
        return questionService.getQuestionAdminById(id);
    }
}
