package com.tianji.learning.controller;


import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.service.IPointsRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 前端控制器
 * </p>
 *
 * @author author
 * @since 2026-11-05
 */
@Api(tags = "积分记录接口")
@RestController
@RequestMapping("/points")
@RequiredArgsConstructor
public class PointsRecordController {
    private final IPointsRecordService pointsRecordService;

    /**
     * 查询今日积分情况
     * @return
     */
    @GetMapping("/today")
    @ApiOperation("查询今日积分情况")
    public List<PointsStatisticsVO> getTodayPointsStatistics() {
        return pointsRecordService.getTodayPointsStatistics();
    }



}
