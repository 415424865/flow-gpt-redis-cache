package com.myhexin.zixun.flow.gpt.plugin.redis.cache;

import com.alibaba.fastjson.JSON;
import com.myhexin.zixun.flow.gpt.plugin.dto.CacheComponentParamParser;
import com.myhexin.zixun.flow.gpt.plugin.dto.CacheParams;
import com.myhexin.zixun.flow.gpt.plugin.dto.ComponentResult;
import com.myhexin.zixun.flow.gpt.plugin.enums.CacheOperation;
import com.myhexin.zixun.flow.gpt.plugin.enums.CacheType;
import com.myhexin.zixun.flow.gpt.plugin.exception.ExecutionException;
import com.myhexin.zixun.flow.gpt.plugin.executor.ExecutionLogCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisCacheComponentTest {

    @Mock
    private CacheService cacheService;

    @Mock
    private ExecutionLogCollector logCollector;

    @InjectMocks
    private RedisCacheComponent redisCacheComponent;

    private CacheParams cacheParams;

    @BeforeEach
    void setUp() {
        cacheParams = new CacheParams();
        cacheParams.setKey("testKey");
        cacheParams.setAppId("testAppId");
        cacheParams.setCacheName("testCacheName");
        // logCollector will be available via @Mock
        redisCacheComponent.setLogCollector(logCollector);
    }

    private String buildExpectedKey(CacheParams params) {
        return params.getAppId() + ":" + params.getCacheName() + ":" + params.getKey();
    }

    // SINGLE Type Operations

    @Test
    void cmpProcess_singleWrite_shouldSucceed() {
        cacheParams.setType(CacheType.SINGLE);
        cacheParams.setOperation(CacheOperation.SINGLE_WRITE);
        cacheParams.setValue("testValue");
        cacheParams.setTtl(3600L);

        String expectedKey = buildExpectedKey(cacheParams);
        String inputJson = JSON.toJSONString(cacheParams);

        try (MockedStatic<CacheComponentParamParser> mockedParser = Mockito.mockStatic(CacheComponentParamParser.class)) {
            mockedParser.when(() -> CacheComponentParamParser.parse(inputJson, logCollector)).thenReturn(cacheParams);

            String resultJson = redisCacheComponent.cmpProcess(inputJson);

            mockedParser.verify(() -> CacheComponentParamParser.parse(inputJson, logCollector));
            verify(cacheService).putValue(eq(expectedKey), eq("testValue"), eq(3600L));
            assertNotNull(resultJson);
            ComponentResult result = JSON.parseObject(resultJson, ComponentResult.class);
            assertTrue(result.isSuccess());
            assertEquals("Success", result.getMessage());
        }
    }

    @Test
    void cmpProcess_singleRead_shouldReturnValue() {
        cacheParams.setType(CacheType.SINGLE);
        cacheParams.setOperation(CacheOperation.SINGLE_READ);

        String expectedKey = buildExpectedKey(cacheParams);
        String expectedValue = "cachedValue";
        String inputJson = JSON.toJSONString(cacheParams);

        try (MockedStatic<CacheComponentParamParser> mockedParser = Mockito.mockStatic(CacheComponentParamParser.class)) {
            mockedParser.when(() -> CacheComponentParamParser.parse(inputJson, logCollector)).thenReturn(cacheParams);
            when(cacheService.getValue(expectedKey)).thenReturn(expectedValue);

            String resultJson = redisCacheComponent.cmpProcess(inputJson);

            mockedParser.verify(() -> CacheComponentParamParser.parse(inputJson, logCollector));
            verify(cacheService).getValue(expectedKey);
            assertNotNull(resultJson);
            ComponentResult result = JSON.parseObject(resultJson, ComponentResult.class);
            assertTrue(result.isSuccess());
            assertEquals(expectedValue, result.getData());
        }
    }

    @Test
    void cmpProcess_singleRead_shouldReturnNullWhenCacheMiss() {
        cacheParams.setType(CacheType.SINGLE);
        cacheParams.setOperation(CacheOperation.SINGLE_READ);

        String expectedKey = buildExpectedKey(cacheParams);
        String inputJson = JSON.toJSONString(cacheParams);

        try (MockedStatic<CacheComponentParamParser> mockedParser = Mockito.mockStatic(CacheComponentParamParser.class)) {
            mockedParser.when(() -> CacheComponentParamParser.parse(inputJson, logCollector)).thenReturn(cacheParams);
            when(cacheService.getValue(expectedKey)).thenReturn(null);

            String resultJson = redisCacheComponent.cmpProcess(inputJson);

            mockedParser.verify(() -> CacheComponentParamParser.parse(inputJson, logCollector));
            verify(cacheService).getValue(expectedKey);
            assertNotNull(resultJson);
            ComponentResult result = JSON.parseObject(resultJson, ComponentResult.class);
            assertTrue(result.isSuccess());
            assertNull(result.getData());
        }
    }

    // ZSET Type Operations

    @Test
    void cmpProcess_zsetOverwrite_shouldSucceed() {
        cacheParams.setType(CacheType.ZSET);
        cacheParams.setOperation(CacheOperation.ZSET_OVERWRITE);
        List<Object> values = Arrays.asList("value1", "value2");
        cacheParams.setValues(values);
        cacheParams.setTtl(3600L);

        String expectedKey = buildExpectedKey(cacheParams);
        String inputJson = JSON.toJSONString(cacheParams);

        try (MockedStatic<CacheComponentParamParser> mockedParser = Mockito.mockStatic(CacheComponentParamParser.class)) {
            mockedParser.when(() -> CacheComponentParamParser.parse(inputJson, logCollector)).thenReturn(cacheParams);

            String resultJson = redisCacheComponent.cmpProcess(inputJson);

            mockedParser.verify(() -> CacheComponentParamParser.parse(inputJson, logCollector));
            verify(cacheService).putList(eq(expectedKey), eq(values), eq(3600L));
            assertNotNull(resultJson);
            ComponentResult result = JSON.parseObject(resultJson, ComponentResult.class);
            assertTrue(result.isSuccess());
            assertEquals("Success", result.getMessage());
        }
    }

    @Test
    void cmpProcess_zsetAdd_shouldSucceed() {
        cacheParams.setType(CacheType.ZSET);
        cacheParams.setOperation(CacheOperation.ZSET_ADD);
        cacheParams.setValue("newValue");
        cacheParams.setScore(10.0);

        String expectedKey = buildExpectedKey(cacheParams);
        String inputJson = JSON.toJSONString(cacheParams);

        try (MockedStatic<CacheComponentParamParser> mockedParser = Mockito.mockStatic(CacheComponentParamParser.class)) {
            mockedParser.when(() -> CacheComponentParamParser.parse(inputJson, logCollector)).thenReturn(cacheParams);

            String resultJson = redisCacheComponent.cmpProcess(inputJson);

            mockedParser.verify(() -> CacheComponentParamParser.parse(inputJson, logCollector));
            verify(cacheService).appendToList(eq(expectedKey), eq("newValue"), eq(10.0));
            assertNotNull(resultJson);
            ComponentResult result = JSON.parseObject(resultJson, ComponentResult.class);
            assertTrue(result.isSuccess());
            assertEquals("Success", result.getMessage());
        }
    }

    @Test
    void cmpProcess_zsetReadAll_shouldReturnList() {
        cacheParams.setType(CacheType.ZSET);
        cacheParams.setOperation(CacheOperation.ZSET_READ_ALL);

        String expectedKey = buildExpectedKey(cacheParams);
        List<Object> expectedList = Arrays.asList("item1", "item2");
        String inputJson = JSON.toJSONString(cacheParams);

        try (MockedStatic<CacheComponentParamParser> mockedParser = Mockito.mockStatic(CacheComponentParamParser.class)) {
            mockedParser.when(() -> CacheComponentParamParser.parse(inputJson, logCollector)).thenReturn(cacheParams);
            when(cacheService.getList(expectedKey)).thenReturn(expectedList);

            String resultJson = redisCacheComponent.cmpProcess(inputJson);

            mockedParser.verify(() -> CacheComponentParamParser.parse(inputJson, logCollector));
            verify(cacheService).getList(expectedKey);
            assertNotNull(resultJson);
            ComponentResult result = JSON.parseObject(resultJson, ComponentResult.class);
            assertTrue(result.isSuccess());
            assertEquals(expectedList, result.getData());
        }
    }

    @Test
    void cmpProcess_zsetReadConditional_shouldReturnList() {
        cacheParams.setType(CacheType.ZSET);
        cacheParams.setOperation(CacheOperation.ZSET_READ_CONDITIONAL);
        cacheParams.setMinScore(1.0);
        cacheParams.setMaxScore(10.0);

        String expectedKey = buildExpectedKey(cacheParams);
        List<Object> expectedList = Arrays.asList("itemA", "itemB");
        String inputJson = JSON.toJSONString(cacheParams);

        try (MockedStatic<CacheComponentParamParser> mockedParser = Mockito.mockStatic(CacheComponentParamParser.class)) {
            mockedParser.when(() -> CacheComponentParamParser.parse(inputJson, logCollector)).thenReturn(cacheParams);
            when(cacheService.getListSince(expectedKey, 1.0, 10.0)).thenReturn(expectedList);

            String resultJson = redisCacheComponent.cmpProcess(inputJson);

            mockedParser.verify(() -> CacheComponentParamParser.parse(inputJson, logCollector));
            verify(cacheService).getListSince(expectedKey, 1.0, 10.0);
            assertNotNull(resultJson);
            ComponentResult result = JSON.parseObject(resultJson, ComponentResult.class);
            assertTrue(result.isSuccess());
            assertEquals(expectedList, result.getData());
        }
    }

    @Test
    void cmpProcess_zsetCheckExist_shouldReturnBoolean() {
        cacheParams.setType(CacheType.ZSET);
        cacheParams.setOperation(CacheOperation.ZSET_CHECK_EXIST);
        cacheParams.setValue("valueToCheck");

        String expectedKey = buildExpectedKey(cacheParams);
        String inputJson = JSON.toJSONString(cacheParams);

        try (MockedStatic<CacheComponentParamParser> mockedParser = Mockito.mockStatic(CacheComponentParamParser.class)) {
            mockedParser.when(() -> CacheComponentParamParser.parse(inputJson, logCollector)).thenReturn(cacheParams);
            when(cacheService.containsInList(expectedKey, "valueToCheck")).thenReturn(true);

            String resultJson = redisCacheComponent.cmpProcess(inputJson);

            mockedParser.verify(() -> CacheComponentParamParser.parse(inputJson, logCollector));
            verify(cacheService).containsInList(expectedKey, "valueToCheck");
            assertNotNull(resultJson);
            ComponentResult result = JSON.parseObject(resultJson, ComponentResult.class);
            assertTrue(result.isSuccess());
            assertEquals(true, result.getData());
        }
    }

    // Error Handling

    @Test
    void cmpProcess_parserThrowsExecutionException_shouldRethrow() {
        String inputJson = JSON.toJSONString(cacheParams); // Content doesn't matter as parse will be mocked
        ExecutionException expectedException = new ExecutionException("Parser error");

        try (MockedStatic<CacheComponentParamParser> mockedParser = Mockito.mockStatic(CacheComponentParamParser.class)) {
            mockedParser.when(() -> CacheComponentParamParser.parse(inputJson, logCollector))
                    .thenThrow(expectedException);

            ExecutionException actualException = assertThrows(ExecutionException.class, () -> {
                redisCacheComponent.cmpProcess(inputJson);
            });

            assertEquals(expectedException, actualException);
            mockedParser.verify(() -> CacheComponentParamParser.parse(inputJson, logCollector));
            verify(logCollector).logError(anyString(), eq(expectedException));
        }
    }

    @Test
    void cmpProcess_cacheServiceThrowsExecutionException_shouldRethrow() {
        cacheParams.setType(CacheType.SINGLE);
        cacheParams.setOperation(CacheOperation.SINGLE_WRITE);
        cacheParams.setValue("testValue");
        String inputJson = JSON.toJSONString(cacheParams);
        String expectedKey = buildExpectedKey(cacheParams);
        ExecutionException expectedException = new ExecutionException("Cache service error");

        try (MockedStatic<CacheComponentParamParser> mockedParser = Mockito.mockStatic(CacheComponentParamParser.class)) {
            mockedParser.when(() -> CacheComponentParamParser.parse(inputJson, logCollector)).thenReturn(cacheParams);
            doThrow(expectedException).when(cacheService).putValue(eq(expectedKey), anyString(), anyLong());

            ExecutionException actualException = assertThrows(ExecutionException.class, () -> {
                redisCacheComponent.cmpProcess(inputJson);
            });

            assertEquals(expectedException, actualException);
            mockedParser.verify(() -> CacheComponentParamParser.parse(inputJson, logCollector));
            verify(cacheService).putValue(eq(expectedKey), eq("testValue"), anyLong());
            verify(logCollector).logError(anyString(), eq(expectedException));
        }
    }

    @Test
    void cmpProcess_cacheServiceThrowsRuntimeException_shouldWrapAndRethrow() {
        cacheParams.setType(CacheType.SINGLE);
        cacheParams.setOperation(CacheOperation.SINGLE_READ);
        String inputJson = JSON.toJSONString(cacheParams);
        String expectedKey = buildExpectedKey(cacheParams);
        RuntimeException runtimeException = new RuntimeException("Unexpected cache error");

        try (MockedStatic<CacheComponentParamParser> mockedParser = Mockito.mockStatic(CacheComponentParamParser.class)) {
            mockedParser.when(() -> CacheComponentParamParser.parse(inputJson, logCollector)).thenReturn(cacheParams);
            when(cacheService.getValue(expectedKey)).thenThrow(runtimeException);

            ExecutionException actualException = assertThrows(ExecutionException.class, () -> {
                redisCacheComponent.cmpProcess(inputJson);
            });

            assertEquals("Error executing Redis cache operation", actualException.getMessage());
            assertEquals(runtimeException, actualException.getCause());
            mockedParser.verify(() -> CacheComponentParamParser.parse(inputJson, logCollector));
            verify(cacheService).getValue(expectedKey);
            verify(logCollector).logError(anyString(), eq(actualException));
        }
    }

    @Test
    void cmpProcess_unsupportedOperationForType_shouldThrowAndLog() {
        // Example: Using ZSET_ADD operation with SINGLE type
        // This should ideally be caught by the parser, but the component might have its own checks
        // or the way it handles incompletely parsed/validated params might lead to an error.
        cacheParams.setType(CacheType.SINGLE); // Set to SINGLE
        cacheParams.setOperation(CacheOperation.ZSET_ADD); // But operation is for ZSET
        cacheParams.setValue("testValue");
        cacheParams.setScore(1.0); // Relevant for ZSET_ADD

        String inputJson = JSON.toJSONString(cacheParams);

        try (MockedStatic<CacheComponentParamParser> mockedParser = Mockito.mockStatic(CacheComponentParamParser.class)) {
            // Simulate parser returning params, even if they are contradictory,
            // to test component's internal logic.
            mockedParser.when(() -> CacheComponentParamParser.parse(inputJson, logCollector)).thenReturn(cacheParams);

            ExecutionException actualException = assertThrows(ExecutionException.class, () -> {
                redisCacheComponent.cmpProcess(inputJson);
            });

            // The exact message might depend on how RedisCacheComponent handles this.
            // It might be a generic error, or something more specific if it validates operation vs type.
            // For now, let's assume it will be a generic error because the switch statement for CacheType
            // won't find a matching operation under the SINGLE case.
            assertTrue(actualException.getMessage().contains("Error executing Redis cache operation"));
            // It's likely to be a NullPointerException or similar if the specific fields needed for SINGLE operations
            // are not there, or an IllegalArgumentException if there's an explicit check.
            // Let's check for the cause being a RuntimeException as per current RedisCacheComponent structure
            // if it falls through switch cases or tries to access a field not set for SINGLE.
            assertNotNull(actualException.getCause());
            assertTrue(actualException.getCause() instanceof RuntimeException || actualException.getCause() instanceof ExecutionException);


            mockedParser.verify(() -> CacheComponentParamParser.parse(inputJson, logCollector));
            // logCollector interaction might vary based on where the error is exactly caught.
            // If it's due to falling through the switch, the generic catch block will log it.
            verify(logCollector).logError(anyString(), eq(actualException));
        }
    }


    // GetName
    @Test
    void getName_shouldReturnComponentName() {
        assertEquals("RedisCacheComponent", redisCacheComponent.getName());
    }
}
