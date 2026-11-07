package com.tianji.common.autoconfigure.mybatis;


import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass({MybatisPlusInterceptor.class, BaseMapper.class})
public class MybatisConfig {

    /**
     * @deprecated 存在任务更新数据导致updater写入0或null的问题，暂时废弃
     * @see MyBatisAutoFillInterceptor 通过自定义拦截器来实现自动注入creater和updater
     */
    // @Bean
    // @ConditionalOnMissingBean
    public BaseMetaObjectHandler baseMetaObjectHandler(){
        return new BaseMetaObjectHandler();
    }

    /**
     * 装配 MyBatis-Plus 插件。
     * <p>
     * 动态表名属于可选插件：只有需要分表的服务（如 tj-learning 的积分排行榜）才会
     * 在各自的容器里声明 {@link DynamicTableNameInnerInterceptor}，其余服务没有这个 Bean，
     * 因此这里必须用 {@code required = false}，否则那些服务启动时会因为找不到 Bean 而失败。
     * <p>
     * 插件顺序不能随意调整，动态表名必须排在分页之前，
     * 保证 count 语句和列表查询用的是同一张物理表。
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            @Autowired(required = false) DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 1. 动态表名（选配，必须在分页之前）
        if (dynamicTableNameInnerInterceptor != null) {
            interceptor.addInnerInterceptor(dynamicTableNameInnerInterceptor);
        }
        // 2. 分页插件
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInnerInterceptor.setMaxLimit(200L);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        // 3. 字段自动填充
        interceptor.addInnerInterceptor(new MyBatisAutoFillInterceptor());
        return interceptor;
    }
}
