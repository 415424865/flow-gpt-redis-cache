package com.myhexin.zixun.flow.gpt.plugin.redis.cache.config;

import com.myhexin.zixun.flow.gpt.plugin.base.spi.ExecutionLogCollector;
import com.myhexin.zixun.flow.gpt.plugin.redis.cache.RedisCacheComponent;
import com.myhexin.zixun.flow.gpt.plugin.redis.cache.service.CacheService;
import com.myhexin.zixun.flow.gpt.plugin.redis.cache.service.impl.RedisCacheServiceImpl;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class CacheComponentAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CacheComponentAutoConfiguration.class));

    @Configuration
    static class MockStringRedisTemplateConfig {
        @Bean
        public StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }

    @Configuration
    static class MockExecutionLogCollectorConfig {
        @Bean
        public ExecutionLogCollector executionLogCollector() {
            return mock(ExecutionLogCollector.class);
        }
    }

    @Configuration
    static class UserDefinedCacheServiceConfig {
        @Bean
        public CacheService customCacheService() {
            // Using a specific mock instance to differentiate
            return mock(CacheService.class, "customCacheServiceMock");
        }
    }

    @Test
    public void defaultBeansCreatedWhenDependenciesPresent() {
        this.contextRunner
                .withUserConfiguration(MockStringRedisTemplateConfig.class, MockExecutionLogCollectorConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheService.class);
                    assertThat(context).getBean(CacheService.class).isInstanceOf(RedisCacheServiceImpl.class);
                    assertThat(context).hasSingleBean(RedisCacheComponent.class);
                    assertThat(context).getBean(RedisCacheComponent.COMPONENT_NAME).isInstanceOf(RedisCacheComponent.class);
                    // Verify that the RedisCacheComponent is using the auto-configured RedisCacheServiceImpl
                    RedisCacheComponent component = context.getBean(RedisCacheComponent.class);
                    CacheService serviceInComponent = component.getCacheService(); // Assuming getter for test
                    assertThat(serviceInComponent).isInstanceOf(RedisCacheServiceImpl.class);
                });
    }

    @Test
    public void redisCacheServiceNotCreatedIfUserDefinesCacheService() {
        this.contextRunner
                .withUserConfiguration(MockStringRedisTemplateConfig.class, MockExecutionLogCollectorConfig.class, UserDefinedCacheServiceConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheService.class);
                    // Should be the user's mock, not RedisCacheServiceImpl
                    assertThat(context).getBean(CacheService.class).isNotInstanceOf(RedisCacheServiceImpl.class);
                    assertThat(context).getBean(CacheService.class).isSameAs(context.getBean("customCacheService"));

                    // RedisCacheComponent should still be created and use the user's CacheService
                    assertThat(context).hasSingleBean(RedisCacheComponent.class);
                    RedisCacheComponent component = context.getBean(RedisCacheComponent.class);
                    CacheService serviceInComponent = component.getCacheService(); // Assuming getter for test
                    assertThat(serviceInComponent).isSameAs(context.getBean("customCacheService"));
                });
    }

    @Test
    public void cacheServiceAndComponentNotCreatedIfStringRedisTemplateMissing() {
        this.contextRunner // No MockStringRedisTemplateConfig
                .withUserConfiguration(MockExecutionLogCollectorConfig.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CacheService.class);
                    assertThat(context).doesNotHaveBean(RedisCacheServiceImpl.class);
                    // Consequently, RedisCacheComponent should also not be created as it depends on CacheService
                    assertThat(context).doesNotHaveBean(RedisCacheComponent.class);
                    assertThat(context).doesNotHaveBean(RedisCacheComponent.COMPONENT_NAME);

                });
    }

    @Test
    public void redisCacheComponentNotCreatedIfExecutionLogCollectorMissing() {
        this.contextRunner
                .withUserConfiguration(MockStringRedisTemplateConfig.class) // CacheService (RedisCacheServiceImpl) will be created
                .run(context -> {
                    // CacheService and its implementation should be present
                    assertThat(context).hasSingleBean(CacheService.class);
                    assertThat(context).getBean(CacheService.class).isInstanceOf(RedisCacheServiceImpl.class);

                    // But RedisCacheComponent should not be, due to missing ExecutionLogCollector
                    assertThat(context).doesNotHaveBean(RedisCacheComponent.class);
                    assertThat(context).doesNotHaveBean(RedisCacheComponent.COMPONENT_NAME);
                });
    }

    @Test
    public void noBeansCreatedIfAllRelevantDependenciesMissing() {
        this.contextRunner // No user configurations providing mocks for StringRedisTemplate or ExecutionLogCollector
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CacheService.class);
                    assertThat(context).doesNotHaveBean(RedisCacheServiceImpl.class);
                    assertThat(context).doesNotHaveBean(RedisCacheComponent.class);
                    assertThat(context).doesNotHaveBean(RedisCacheComponent.COMPONENT_NAME);
                });
    }
}
