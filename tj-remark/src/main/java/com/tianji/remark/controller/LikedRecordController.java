package com.tianji.remark.controller;


import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.service.ILikedRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 前端控制器
 * </p>
 *
 * @author author
 * @since 2026-11-03
 */
@Api(tags="点赞系统接口")
@RestController
@RequiredArgsConstructor
public class LikedRecordController {
    /**
     * 点赞或取消点赞
     * @param recordDTO
     */
    private final ILikedRecordService likedRecordService;
    @ApiOperation("点赞或取消点赞")
    @PostMapping("/likes")
    public void addLikeRecord(@Valid @RequestBody LikeRecordFormDTO recordDTO) {
        likedRecordService.addLikeRecord(recordDTO);
    }

    /**
     * 批量查询点赞状态
     * 返回点过赞的业务Id
     */
    @ApiOperation("批量查询点赞状态")
    @GetMapping("/liked/list")
    public Set<Long> isBizLiked(@RequestParam("bizIds")List<Long> bizIds) {
       return likedRecordService.isBizLiked(bizIds);
    }

}
