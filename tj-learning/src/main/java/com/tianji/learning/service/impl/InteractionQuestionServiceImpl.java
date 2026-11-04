package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CategoryClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-10-31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {
    private final UserClient userClient;
    private final InteractionReplyMapper interactionReplyMapper;
    private final SearchClient searchClient;
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final CategoryCache categoryCache;
    /**
     * 新增问题
     * @param questionFormDTO
     */
    @Override
    public void saveQuestion(QuestionFormDTO questionFormDTO) {
        //1 获取当前登录用户
        Long userId = UserContext.getUser();
        if(userId==null){
            log.error("用户未登录");
            return;
        }
        //创建问题对象
        InteractionQuestion question = BeanUtils.copyBean(questionFormDTO, InteractionQuestion.class);
        question.setUserId(userId);
        question.setCreateTime(java.time.LocalDateTime.now());
        question.setUpdateTime(java.time.LocalDateTime.now());
        boolean success = save(question);
        if(!success)
            log.error("保存问题失败");
    }

    /**
     * 修改问题
     * @param id
     * @param questionFormDTO
     */
    @Override
    public void updateQuestion(Long id, QuestionFormDTO questionFormDTO) {
        //1 获取当前登录用户
        Long userId = UserContext.getUser();
        if(userId==null){
            log.error("用户未登录");
            return;
        }
        boolean success = lambdaUpdate().set(InteractionQuestion::getTitle, questionFormDTO.getTitle())
                .set(InteractionQuestion::getDescription, questionFormDTO.getDescription())
                .set(InteractionQuestion::getAnonymity, questionFormDTO.getAnonymity())
                .set(InteractionQuestion::getUpdateTime, LocalDateTime.now())
                .eq(InteractionQuestion::getId, id)
                .eq(InteractionQuestion::getUserId, userId)
                .update();
        if(!success) {
            log.error("修改问题失败");
                throw new DbException("修改问题失败");

        }
    }

    /**
     * 分页查询问题
     * @param questionPageQuery
     */
    @Override
    public PageDTO<QuestionVO> pageQuestions(QuestionPageQuery questionPageQuery) {
        //1 参数性校验，要么从课程页进入提供课程id，要么从视频播放口进入，提供小节id
        Long courseId = questionPageQuery.getCourseId();
        Long sectionId = questionPageQuery.getSectionId();
        if(courseId==null && sectionId==null)
            throw new IllegalArgumentException("课程id或小节id不能都为空");
        //2 用户ID判空
        Long userId = UserContext.getUser();
        if(userId==null){
            log.error("用户未登录");
            throw new BadRequestException("用户未登录");
        }
        //3 分页查询
        Page<InteractionQuestion> page = lambdaQuery().select(InteractionQuestion.class, info -> !info.getProperty().equals("description"))   //意思就是不查description
                .eq(courseId != null, InteractionQuestion::getCourseId, courseId)
                .eq(sectionId != null, InteractionQuestion::getSectionId, sectionId)
                .eq(Boolean.TRUE.equals(questionPageQuery.getOnlyMine()), InteractionQuestion::getUserId, userId)
                .eq(InteractionQuestion::getHidden, false)
                .page(questionPageQuery.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        //4 提取出records每个问题里面的最新的一个回答的id，排除null;这里有可能出现latestAnswerId全为null
        List<Long> latestAnswerIds = records.stream().map(InteractionQuestion::getLatestAnswerId).filter(Objects::nonNull).collect(Collectors.toList());
        //4.1 根据latestAnswerIds查询最新回答的用户信息
        List<InteractionReply> replies=new ArrayList<>();
        if(CollUtils.isNotEmpty(latestAnswerIds)){
            replies = interactionReplyMapper.selectBatchIds(latestAnswerIds);
        }
        //4.2转成id,本身的map
        Map<Long, InteractionReply> replyMap = replies.stream().collect(Collectors.toMap(InteractionReply::getId, v -> v));
        //5 提取出records里面的用户ids
        List<Long> userIds = records.stream().map(InteractionQuestion::getUserId).filter(Objects::nonNull).collect(Collectors.toList());
        //5.1 把回答者的id也加进来，否则后面拿不到回答者的昵称（匿名的不需要名字，由后面统一过滤）
        replyMap.values().stream().map(InteractionReply::getUserId).filter(Objects::nonNull).forEach(userIds::add);
        //5.2  批量查询用户
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        if(CollUtils.isEmpty(userDTOS)) {
            log.error("用户信息不存在");
            throw new DbException("用户信息不存在");
        }
        //4.2 将userDTOS转换为Map，方便后续快速查询
        Map<Long, UserDTO> userDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, v -> v));
        //5 封装VO
        List<QuestionVO> questionVOs = new ArrayList<>(records.size());
        for (InteractionQuestion r : records) {
            QuestionVO questionVO = BeanUtils.copyBean(r, QuestionVO.class);
            questionVO.setUserId(null);
            if(!Boolean.TRUE.equals(r.getAnonymity())) {   //不是匿名提问
                UserDTO questionUser = userDTOMap.get(r.getUserId());
                if (questionUser != null) {
                    questionVO.setUserId(questionUser.getId());   //设置提问者id
                    questionVO.setUserName(questionUser.getName());//设置提问者名称
                    questionVO.setUserIcon(questionUser.getIcon());//设置提问者头像
                }
            }
            InteractionReply reply = replyMap.get(r.getLatestAnswerId());
            if(reply!=null){
                questionVO.setLatestReplyContent(reply.getContent());
                if(!Boolean.TRUE.equals(reply.getAnonymity())){ //不是匿名回答
                    UserDTO replyUser = userDTOMap.get(reply.getUserId());
                    if (replyUser != null) {
                        questionVO.setLatestReplyUser(replyUser.getName());
                    }
                }
            }
            questionVOs.add(questionVO);


        }
        return PageDTO.of(page, questionVOs);

    }

    /**
     * 根据id查询问题详情
     * @param id
     * @return
     */
    @Override
    public QuestionVO getQuestionById(Long id) {
        //1 根据问题id查询问题表
        InteractionQuestion question = getById(id);
        if (question == null||question.getHidden()) {
          return null;
        }
        QuestionVO questionVO = BeanUtils.copyBean(question, QuestionVO.class);
        //2 获取当前问题的提问者id，查询提问者详情
        Long userId = question.getUserId();
        if(userId!=null){  //有提问者id
            UserDTO userDTO = userClient.queryUserById(userId);
            if(!Boolean.TRUE.equals(question.getAnonymity())){  //不是匿名提问
                questionVO.setUserName(userDTO.getName());
                questionVO.setUserIcon(userDTO.getIcon());
            }
        }

        //3 返回VO
        return questionVO;
    }

    /**
     * 删除我的问题
     * @param id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)   //事务，两张表得一起删
    public void deleteMyQuestion(Long id) {
        //1 根据问题id查询问题
        InteractionQuestion question = getById(id);
        if(question == null){  //问题不存在
            log.error("问题不存在");
            throw new DbException("问题不存在");
        }
        //2 获取提问者userId和当前登录用户id
        Long userId = question.getUserId();
        Long currentUserId = UserContext.getUser();
        if(currentUserId==null){
            log.error("用户未登录");
            throw new BadRequestException("用户未登录");
        }
        if(!userId.equals(currentUserId)){  //提问者和当前登录用户不一致
            log.error("无权限删除");
            throw new BadRequestException("无权限删除");
        }
        //2.1 一致，删除当前问题
        boolean success = removeById(id);
        if(!success){
            log.error("删除问题失败");
            throw new DbException("删除问题失败");
        }
        //3 一并删除问题下的评论和回答
        int i = interactionReplyMapper.delete(new LambdaQueryWrapper<InteractionReply>()
                .eq(InteractionReply::getQuestionId, id));
        log.info("删除问题{}下的{}个回答", id, i);


    }

    /**
     * 管理端分页查询问题
     * @param query
     * @return
     */
    @Override
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query) {
       // 1.根据课程名获取课程ids
        List<Long> coursesIds = null;
        if(StringUtils.isNotBlank(query.getCourseName())) {
           coursesIds = searchClient.queryCoursesIdByName(query.getCourseName());
           if(CollUtils.isEmpty(coursesIds)){  //没查到相关课程
               return PageDTO.empty(0L,0L);
           }
        }
        //2.分页查询问题
        Integer status = query.getStatus();
        LocalDateTime begin = query.getBeginTime();
        LocalDateTime end = query.getEndTime();

        Page<InteractionQuestion> page = lambdaQuery().in(coursesIds != null, InteractionQuestion::getCourseId, coursesIds)
                .eq(status != null, InteractionQuestion::getStatus, status)
                .gt(begin != null, InteractionQuestion::getCreateTime, begin)
                .lt(end != null, InteractionQuestion::getCreateTime, end)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        //3. 获取章名称，小节名称，课程名称，分类名称，提问者昵称信息
        //3.1分别获取userid,cid,chapterId,sectionId,categoryId
        Set<Long> userIds=new HashSet<>();
        Set<Long> cIds=new HashSet<>();
        Set<Long> cataIds=new HashSet<>();
        for (InteractionQuestion r : records) {
            userIds.add(r.getUserId());
            cIds.add(r.getCourseId());
            cataIds.add(r.getChapterId());
            cataIds.add(r.getSectionId());
        }
        Map<Long, String> userDTONameMap = new HashMap<>(records.size());
        Map<Long, CourseSimpleInfoDTO> courseSimpleNameMap = new HashMap<>(records.size());
        Map<Long, String> chapterNameMap = new HashMap<>(records.size());
        if(CollUtils.isNotEmpty(userIds)){
            //3.2 根据userIds查询用户昵称
            List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
            if(CollUtils.isNotEmpty(userDTOS)){
                userDTONameMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
            }
        }
        if(CollUtils.isNotEmpty(cIds)){
            //3.3 根据courseIds查询课程名称
            List<CourseSimpleInfoDTO> courseSimpleInfoDTOS = courseClient.getSimpleInfoList(cIds);
            if(CollUtils.isNotEmpty(courseSimpleInfoDTOS)){
                 courseSimpleNameMap = courseSimpleInfoDTOS.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c->c));
            }
        }
        if(CollUtils.isNotEmpty(cataIds)){
            //3.4 根据chapterIds查询章名称
            List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(cataIds);
            if(CollUtils.isNotEmpty(cataSimpleInfoDTOS)){
                chapterNameMap = cataSimpleInfoDTOS.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
            }
        }
        //4. 封装VO
        List<QuestionAdminVO> questionAdminVOS = new ArrayList<>(records.size());
        for (InteractionQuestion r : records) {
            QuestionAdminVO questionAdminVO = BeanUtils.copyBean(r, QuestionAdminVO.class);
            questionAdminVOS.add(questionAdminVO);
            //用户信息
            String Username = userDTONameMap.get(r.getUserId());
            if(Username != null){
                questionAdminVO.setUserName(Username);
            }
            //课程信息和课程分类信息
            CourseSimpleInfoDTO cinfo = courseSimpleNameMap.get(r.getCourseId());
            if(cinfo != null){
                questionAdminVO.setCourseName(cinfo.getName());
                questionAdminVO.setCategoryName(categoryCache.getCategoryNames(cinfo.getCategoryIds()));
            }
            //章信息
            questionAdminVO.setChapterName(chapterNameMap.getOrDefault(r.getChapterId(), ""));
            //节信息
            questionAdminVO.setSectionName(chapterNameMap.getOrDefault(r.getSectionId(), ""));


        }
        //返回结果
        return PageDTO.of(page, questionAdminVOS);

    }

    /**
     * 根据id更新问题隐藏状态
     * @param id
     * @param hidden
     */
    @Override
    public void updateHidden(Long id, Integer hidden) {
        //根据id更新隐藏状态
        boolean success = lambdaUpdate().set(hidden!=null,InteractionQuestion::getHidden, hidden)
                .eq(InteractionQuestion::getId, id)
                .update();
        if(!success){
            log.error("更新问题隐藏状态失败");
            throw new DbException("更新问题隐藏状态失败");
        }

    }

    /**
     * 管理端根据id查询问题详情
     * @param id
     * @return
     */
    @Override
    public QuestionAdminVO getQuestionAdminById(Long id) {
        //1 根据问题id查询问题表
        InteractionQuestion question = getById(id);
        if (question == null) {
            log.error("问题不存在");
            throw new DbException("问题不存在");
        }
        Long userId = question.getUserId();
        Long courseId = question.getCourseId();
        List<Long> cataIds=new ArrayList<>();
        cataIds.add(question.getChapterId());
        cataIds.add(question.getSectionId());
        //2 分别根据课程id,用户Id,章节id,课程分类id查询
        UserDTO userDTO = null;
        if(userId!=null){
             userDTO = userClient.queryUserById(userId);
        }
        CourseFullInfoDTO cinfo=null;
        if(courseId!=null){
            cinfo = courseClient.getCourseInfoById(courseId, true, true);

        }
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS=null;
        if(CollUtils.isNotEmpty(cataIds)){
            cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(cataIds);
        }
        //3  查询课程负责老师
        List<Long> teacherIds = cinfo.getTeacherIds();
        List<UserDTO> teacherDTOS = null;
        String teacherNames = null;
        if(!CollUtils.isEmpty(teacherIds)){
             teacherDTOS = userClient.queryUserByIds(teacherIds);
             List<String> teacherList = teacherDTOS.stream().map(UserDTO::getName).collect(Collectors.toList());
             //teacherList转成String,用,连接
             teacherNames = String.join(",", teacherList);
        }
        //4 封装VO，并修改状态为已查看
        QuestionAdminVO questionAdminVO = BeanUtils.copyBean(question, QuestionAdminVO.class);
        questionAdminVO.setStatus(1);//已查看
        questionAdminVO.setUserName(userDTO.getName());//提问者昵称
        questionAdminVO.setUserIcon(userDTO.getIcon());//提问者头像
        questionAdminVO.setCourseName(cinfo.getName());//课程名称
        questionAdminVO.setCategoryName(categoryCache.getCategoryNames(cinfo.getCategoryIds()));//课程分类名称
        questionAdminVO.setChapterName(cataSimpleInfoDTOS.get(0).getName());//章名称
        questionAdminVO.setSectionName(cataSimpleInfoDTOS.get(1).getName());//节名称
        questionAdminVO.setTeacherName(teacherNames);  //教师名称
        return questionAdminVO;
    }
}


