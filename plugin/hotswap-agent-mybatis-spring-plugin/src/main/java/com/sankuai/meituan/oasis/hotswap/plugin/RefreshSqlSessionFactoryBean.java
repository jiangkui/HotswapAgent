package com.sankuai.meituan.oasis.hotswap.plugin;

import com.dianping.zebra.dao.mybatis.ZebraMapperFactoryBean;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.hotswap.agent.logging.AgentLogger;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.net.URL;

/**
 * @author jiangkui
 * @since 1.5.0
 */
@Component
public class RefreshSqlSessionFactoryBean {
    private static AgentLogger LOGGER = AgentLogger.getLogger(RefreshSqlSessionFactoryBean.class);

    @Autowired
    private ApplicationContext applicationContext;

    private static ZebraMapperFactoryBean zebraMapperFactoryBean;
    private static SqlSessionFactoryBean sqlSessionFactoryBean;

    @PostConstruct
    public void init() {
        sqlSessionFactoryBean = applicationContext.getBean(SqlSessionFactoryBean.class);
        zebraMapperFactoryBean = applicationContext.getBean(ZebraMapperFactoryBean.class);
    }

    /**
     * 主动刷新下数据源即可。
     * @param url 要刷新的xml
     */
    public static void refresh(URL url) {
        try {
            try {
                sqlSessionFactoryBean.afterPropertiesSet();

                SqlSessionFactory newSqlSessionFactory = (SqlSessionFactory) getFieldValue(sqlSessionFactoryBean, "sqlSessionFactory");
                Configuration newConfiguration = newSqlSessionFactory.getConfiguration();

                SqlSession oldSqlSession = zebraMapperFactoryBean.getSqlSession();

                SqlSessionFactory oldSqlSessionFactory = (SqlSessionFactory)getFieldValue(oldSqlSession, "sqlSessionFactory");

                Field oldConfiguration = oldSqlSessionFactory.getClass().getDeclaredField("configuration");
                oldConfiguration.setAccessible(true);
                oldConfiguration.set(oldSqlSessionFactory, newConfiguration);

//                Configuration configuration = zebraMapperFactoryBean.getSqlSession().getConfiguration();
//                XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(mapperLocationResource.getInputStream(),
//                        configuration, mapperLocationResource.toString(), configuration.getSqlFragments());
//                xmlMapperBuilder.parse();
            } catch (Throwable t) {
                LOGGER.error("刷新 xml 失败！" + url.getPath(), t);
            } finally {
                ErrorContext.instance().reset();
            }
            LOGGER.info("刷新 xml 成功！" + url.getPath());
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private static Object getFieldValue(Object targetObject, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Field sqlSessionFactoryField = targetObject.getClass().getDeclaredField(fieldName);
        sqlSessionFactoryField.setAccessible(true);
        return sqlSessionFactoryField.get(targetObject);
    }
}
