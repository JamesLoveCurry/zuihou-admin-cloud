package com.github.zuihou.admin.config.datasource;


import com.github.zuihou.core.spring.autoconfigure.datasource.DataSourceFactory;
import com.github.zuihou.core.spring.autoconfigure.datasource.DataSourceProperties;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 一体化平台 中心数据库配置
 *
 * @author zuihou
 * @createTime 2017-11-18 0:34
 */
@Configuration
@EnableCaching
@MapperScan(
        basePackages = {
                "com.github.zuihou.admin.repository.account.dao",
                "com.github.zuihou.admin.repository.authority.dao",
        },
        sqlSessionFactoryRef = "sqlSessionFactory_admin")
@EnableConfigurationProperties({AdminAutoConfiguration.AdminDataSourceProperties.class})
public class AdminAutoConfiguration {
    private static final int TX_METHOD_TIMEOUT = 60;
    private static final String AOP_POINTCUT_EXPRESSION = "execution (* com.github.zuihou.admin.impl..impl.*.*(..))";

    private final AdminDataSourceProperties dataSourceProperties;

    public AdminAutoConfiguration(AdminDataSourceProperties dataSourceProperties) {
        this.dataSourceProperties = dataSourceProperties;
    }

    @Bean(name = "dataSource_admin", initMethod = "init", destroyMethod = "close")
    @Primary
    public DataSource rdsDataSource() {
        return DataSourceFactory.createDataSource(this.dataSourceProperties);
    }


    @Bean(name = "tx_admin")
    public DataSourceTransactionManager rdsTransactionManager() {
        return new DataSourceTransactionManager(rdsDataSource());
    }

    @Bean(name = "sqlSessionFactory_admin")
    public SqlSessionFactory sqlSessionFactory(@Qualifier("dataSource_admin") DataSource rdsDataSource) throws Exception {
        return DataSourceFactory.createSqlSessionFactoryBean(rdsDataSource, new String[]{"classpath:mapper_admin/**/*.xml"}).getObject();
    }

    @Bean
    public TransactionInterceptor txAdvice(@Qualifier("tx_admin") PlatformTransactionManager transactionManager) {
        NameMatchTransactionAttributeSource source = new NameMatchTransactionAttributeSource();
        /*只读事务，不做更新操作*/
        RuleBasedTransactionAttribute readOnlyTx = new RuleBasedTransactionAttribute();
        readOnlyTx.setReadOnly(true);
        readOnlyTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        /*当前存在事务就使用当前事务，当前不存在事务就创建一个新的事务*/
        RuleBasedTransactionAttribute requiredTx = new RuleBasedTransactionAttribute();
        requiredTx.setRollbackRules(Collections.singletonList(new RollbackRuleAttribute(Exception.class)));
        requiredTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        requiredTx.setTimeout(TX_METHOD_TIMEOUT);
        Map<String, TransactionAttribute> txMap = new HashMap<>(11);
        txMap.put("add*", requiredTx);
        txMap.put("save*", requiredTx);
        txMap.put("insert*", requiredTx);
        txMap.put("create*", requiredTx);
        txMap.put("update*", requiredTx);
        txMap.put("delete*", requiredTx);
        txMap.put("remove*", requiredTx);
        txMap.put("get*", readOnlyTx);
        txMap.put("query*", readOnlyTx);
        txMap.put("find*", readOnlyTx);
        txMap.put("list*", readOnlyTx);
        txMap.put("page*", readOnlyTx);
        source.setNameMap(txMap);
        return new TransactionInterceptor(transactionManager, source);
    }

    @Bean
    public Advisor txAdviceAdvisor(@Qualifier("tx_admin") PlatformTransactionManager transactionManager) {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression(AOP_POINTCUT_EXPRESSION);
        return new DefaultPointcutAdvisor(pointcut, txAdvice(transactionManager));
    }

    @ConfigurationProperties(
            prefix = "zuihou.mysql.admin"
    )
    static class AdminDataSourceProperties extends DataSourceProperties {

    }
}
