package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.remark.RemarkClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-10-31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final InteractionQuestionMapper questionMapper;
    private final UserClient userClient;
    private final RemarkClient remarkClient;

    /**
     * 新增回答或评论
     *
     * @param replyDTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addReply(ReplyDTO replyDTO) {
        // 1.登录校验
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("请先登录在回复");
        }
        // 2.问题必须存在
        InteractionQuestion question = questionMapper.selectById(replyDTO.getQuestionId());
        if (question == null) {
            throw new BadRequestException("问题不存在");
        }
        // 3.如果是评论，校验上级回答存在，且必须属于同一个问题
        if (replyDTO.getAnswerId() != null) {
            InteractionReply parent = getById(replyDTO.getAnswerId());
            if (parent == null || !replyDTO.getQuestionId().equals(parent.getQuestionId())) {
                throw new BadRequestException("回复的回答不存在");
            }
        }
        // 4.写入回答或评论
        InteractionReply reply = BeanUtils.copyBean(replyDTO, InteractionReply.class);
        reply.setUserId(userId);
        reply.setHidden(false);
        reply.setReplyTimes(0);
        reply.setLikedTimes(0);
        reply.setCreateTime(LocalDateTime.now());
        reply.setUpdateTime(LocalDateTime.now());
        if (reply.getAnonymity() == null) {
            reply.setAnonymity(false);
        }
        if (!save(reply)) {
            throw new DbException("保存回答失败");
        }
        // 5.区分回答和评论
        if (replyDTO.getAnswerId() == null) {
            // 5.1.是回答：更新问题的最新回答id，问题的回答数+1
            questionMapper.update(null, new LambdaUpdateWrapper<InteractionQuestion>()
                    .set(InteractionQuestion::getLatestAnswerId, reply.getId())
                    .setSql("answer_times = answer_times + 1")
                    .eq(InteractionQuestion::getId, replyDTO.getQuestionId()));
        } else {
            // 5.2.是评论：上级回答的评论数+1
            lambdaUpdate()
                    .setSql("reply_times = reply_times + 1")
                    .eq(InteractionReply::getId, replyDTO.getAnswerId())
                    .update();
        }
        // 6.是学生提交的回答：把问题状态改回"未查看"，提醒老师有新的学生回答待处理
        if (Boolean.TRUE.equals(replyDTO.getIsStudent())) {
            questionMapper.update(null, new LambdaUpdateWrapper<InteractionQuestion>()
                    .set(InteractionQuestion::getStatus, QuestionStatus.UN_CHECK)
                    .eq(InteractionQuestion::getId, replyDTO.getQuestionId()));
        }
        log.debug("新增回答或评论成功，id={}, questionId={}, answerId={}, isStudent={}",
                reply.getId(), replyDTO.getQuestionId(), replyDTO.getAnswerId(), replyDTO.getIsStudent());
    }

    /**
     * 分页查询回答或评论列表
     */
    @Override
    public PageDTO<ReplyVO> getReplyPage(ReplyPageQuery query) {
        // 1.参数校验，问题Id和回答Id不能都为空
        Long questionId = query.getQuestionId();
        Long answerId = query.getAnswerId();
        if (answerId == null && questionId == null) {
            throw new BadRequestException("问题Id和回答Id不能都为空");
        }
        // 2.分页查询（查回答时排除评论，被隐藏的不返回）
        Page<InteractionReply> page = lambdaQuery()
                .eq(questionId != null, InteractionReply::getQuestionId, questionId)
                .eq(answerId != null, InteractionReply::getAnswerId, answerId)
                .isNull(answerId == null, InteractionReply::getAnswerId)
                .eq(InteractionReply::getHidden, false)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 3.本次查的是回答还是评论
        boolean isAnswer = answerId == null;
        //3.1 提取出回答ids，查询当前用户是否给这个回答点了赞
        Set<Long> ids = records.stream().map(InteractionReply::getId).collect(Collectors.toSet());
        Set<Long> bizLiked = remarkClient.isBizLiked(ids);
        // 4.收集用户id：回答者 + 被回复者
        Map<Long, UserDTO> userMap = queryUserMap(records, isAnswer, true);
        // 5.转换成VO
        List<ReplyVO> voList = new ArrayList<>(records.size());
        for (InteractionReply r : records) {
            voList.add(toVO(r, isAnswer, userMap, true, bizLiked));
        }
        return PageDTO.of(page, voList);
    }

    /**
     * 管理端分页查询回答或评论列表
     */
    @Override
    public PageDTO<ReplyVO> pageRepliesForAdmin(ReplyPageQuery query) {
        Long questionId = query.getQuestionId();
        Long answerId = query.getAnswerId();
        if (answerId == null && questionId == null) {
            throw new BadRequestException("问题id和回答id不能都为空");
        }
        // 管理端需要看到被隐藏的数据，所以不过滤 hidden
        Page<InteractionReply> page = lambdaQuery()
                .eq(questionId != null, InteractionReply::getQuestionId, questionId)
                .eq(answerId != null, InteractionReply::getAnswerId, answerId)
                .isNull(answerId == null, InteractionReply::getAnswerId)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        boolean isAnswer = answerId == null;
        // 管理端需要看到真实的提问/回答人，所以不做匿名过滤
        Map<Long, UserDTO> userMap = queryUserMap(records, isAnswer, false);
        List<ReplyVO> voList = new ArrayList<>(records.size());
        for (InteractionReply r : records) {
            voList.add(toVO(r, isAnswer, userMap, false, null));
        }
        return PageDTO.of(page, voList);
    }

    /**
     * 管理端隐藏或显示回答/评论
     * 注意：隐藏的是回答时，回答下的评论要一起隐藏
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReplyHidden(Long id, Integer hidden) {
        InteractionReply reply = getById(id);
        if (reply == null) {
            throw new BadRequestException("回答或评论不存在");
        }
        boolean hiddenFlag = hidden != null && hidden == 1;
        // 1.更新自己
        lambdaUpdate()
                .set(InteractionReply::getHidden, hiddenFlag)
                .set(InteractionReply::getUpdateTime, LocalDateTime.now())
                .eq(InteractionReply::getId, id)
                .update();
        // 2.如果是回答，它下面的评论一起处理
        if (reply.getAnswerId() == null) {
            lambdaUpdate()
                    .set(InteractionReply::getHidden, hiddenFlag)
                    .set(InteractionReply::getUpdateTime, LocalDateTime.now())
                    .eq(InteractionReply::getAnswerId, id)
                    .update();
        }
        log.debug("更新回答或评论显示状态，id={}, hidden={}", id, hiddenFlag);
    }

    /**
     * 批量查询回答者、被回复者的用户信息
     *
     * @param records    当前页数据
     * @param isAnswer   true-本次查的是回答，false-评论
     * @param skipAnonymous 是否跳过匿名用户（用户端跳过，管理端不跳过）
     */
    private Map<Long, UserDTO> queryUserMap(List<InteractionReply> records, boolean isAnswer, boolean skipAnonymous) {
        Set<Long> userIds = new HashSet<>();
        for (InteractionReply r : records) {
            if (skipAnonymous && Boolean.TRUE.equals(r.getAnonymity())) {
                continue;
            }
            if (r.getUserId() != null) {
                userIds.add(r.getUserId());
            }
            // 评论要显示"回复 @某某"，需要被回复人的信息
            if (!isAnswer && r.getTargetUserId() != null) {
                userIds.add(r.getTargetUserId());
            }
        }
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if (CollUtils.isEmpty(userIds)) {
            return userMap;
        }
        List<UserDTO> users = userClient.queryUserByIds(userIds);
        if (CollUtils.isNotEmpty(users)) {
            users.forEach(u -> userMap.put(u.getId(), u));
        }
        return userMap;
    }

    /**
     * PO 转 VO
     */
    private ReplyVO toVO(InteractionReply r, boolean isAnswer, Map<Long, UserDTO> userMap, boolean skipAnonymous, Set<Long> likedIds) {
        ReplyVO vo = BeanUtils.copyBean(r, ReplyVO.class);
        // 匿名时不能泄露用户id、昵称、头像
        vo.setUserId(null);
        vo.setUserName(null);
        vo.setUserIcon(null);
        if (!skipAnonymous || !Boolean.TRUE.equals(r.getAnonymity())) {
            UserDTO user = userMap.get(r.getUserId());
            if (user != null) {
                vo.setUserId(user.getId());
                vo.setUserName(user.getName());
                vo.setUserIcon(user.getIcon());
                vo.setUserType(user.getType());
            }
        }
        if (!isAnswer) {
            // 评论不需要展示"评论数量"，但要展示"回复 @某某"
            vo.setReplyTimes(null);
            UserDTO target = userMap.get(r.getTargetUserId());
            if (target != null) {
                vo.setTargetUserName(target.getName());
            }
        }
        // 回填当前用户是否点赞过（管理端传 null，不展示点赞状态）
        vo.setLiked(likedIds != null && likedIds.contains(r.getId()));
        return vo;
    }
}
