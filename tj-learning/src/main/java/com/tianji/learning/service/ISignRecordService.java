package com.tianji.learning.service;

import com.tianji.learning.domain.vo.SignResultVO;
import org.springframework.stereotype.Service;

import java.util.List;


public interface ISignRecordService {
    /**
     * 签到
     * @return
     */
    SignResultVO addSignRecords();

    /**
     * 查询签到记录
     * @return
     */
    List<Long> querySignRecords();
}
