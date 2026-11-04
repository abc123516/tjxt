package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;

/**
 * <p>
 * 互动提问的问题表 服务类
 * </p>
 *
 * @author author
 * @since 2026-10-31
 */
public interface IInteractionQuestionService extends IService<InteractionQuestion> {
    /**
     * 新增问题
     * @param questionFormDTO
     */
    void saveQuestion(QuestionFormDTO questionFormDTO);

    /**
     * 修改问题
     * @param id
     * @param questionFormDTO
     */
    void updateQuestion(Long id, QuestionFormDTO questionFormDTO);

    /**
     * 分页查询问题
     * @param questionPageQuery
     */
    PageDTO<QuestionVO> pageQuestions(QuestionPageQuery questionPageQuery);

    /**
     * 根据id查询问题详情
     * @param id
     * @return
     */
    QuestionVO getQuestionById(Long id);

    /**
     * 删除我的问题
     * @param id
     */
    void deleteMyQuestion(Long id);

    /**
     * 管理端分页查询问题
     * @param query
     * @return
     */
    PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query);

    /**
     * 管理端隐藏或显示问题
     * @param id
     * @param hidden
     */
    void updateHidden(Long id, Integer hidden);

    /**
     * 管理端根据Id查询问题详情
     * @param id
     * @return
     */
    QuestionAdminVO getQuestionAdminById(Long id);
}
