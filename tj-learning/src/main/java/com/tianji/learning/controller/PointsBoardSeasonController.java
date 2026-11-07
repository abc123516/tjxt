package com.tianji.learning.controller;


import com.tianji.learning.domain.vo.PointsBoardSeasonVO;
import com.tianji.learning.service.IPointsBoardSeasonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author author
 * @since 2026-11-05
 */
@Api(tags = "积分榜赛季管理")
@RestController
@RequestMapping("/boards/seasons")
@RequiredArgsConstructor
public class PointsBoardSeasonController {
    private final IPointsBoardSeasonService pointsBoardSeasonService;

    @ApiOperation("查询赛季列表")
    @GetMapping("/list")
    public List<PointsBoardSeasonVO> querySeasonList(){
        return pointsBoardSeasonService.querySeasonList();
    }

}
