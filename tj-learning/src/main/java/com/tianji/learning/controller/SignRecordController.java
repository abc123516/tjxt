package com.tianji.learning.controller;

import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.service.ISignRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.units.qual.C;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags="签到接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("sign-records")
public class SignRecordController {
    private final ISignRecordService signRecordService;

    /***
     * 签到
     * @return
     */
    @ApiOperation("签到")
    @PostMapping
    public SignResultVO addSignRecords() {
        return signRecordService.addSignRecords();
    }
    @ApiOperation("查询签到记录")
    @GetMapping
    public List<Long> querySignRecords() {
        return signRecordService.querySignRecords();
    }
}
