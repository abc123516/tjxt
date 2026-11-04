package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
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
@Api(tags="问题接口")
@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
public class InteractionQuestionController {
    /**
     * 新增问题
     */
    private final IInteractionQuestionService iInteractionQuestionService;
    @ApiOperation("新增问题")
    @PostMapping
    public void addQuestion(@Valid @RequestBody QuestionFormDTO questionFormDTO) {
        iInteractionQuestionService.saveQuestion(questionFormDTO);

    }
    /**
     * 修改问题
     */
    @ApiOperation("修改问题")
    @PutMapping("/{id}")
    public void updateQuestion(@Valid @PathVariable("id") Long id, @Valid @RequestBody QuestionFormDTO questionFormDTO) {
        iInteractionQuestionService.updateQuestion(id,questionFormDTO);
    }

    /**
     * 用户端分页查询问题
     */
    @ApiOperation("用户端分页查询问题")
    @GetMapping("/page")
    public PageDTO<QuestionVO> pageQuestions(QuestionPageQuery questionPageQuery) {
        return iInteractionQuestionService.pageQuestions(questionPageQuery);
    }
    /**
     * 根据id查询问题详情
     */
    @ApiOperation("根据id查询问题详情")
    @GetMapping("/{id}")
    public QuestionVO getQuestionById(@PathVariable("id") Long id) {
        return iInteractionQuestionService.getQuestionById(id);
    }
    /**
     * 删除我的问题
     */
    @ApiOperation("删除我的问题")
    @DeleteMapping("/{id}")
    public void deleteMyQuestion(@PathVariable("id") Long id) {
        iInteractionQuestionService.deleteMyQuestion(id);
    }

}
