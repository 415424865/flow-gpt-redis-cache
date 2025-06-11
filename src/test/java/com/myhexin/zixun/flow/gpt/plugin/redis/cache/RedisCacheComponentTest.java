package com.myhexin.zixun.flow.gpt.plugin.redis.cache;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.myhexin.zixun.flow.gpt.engine.api.exception.ExecutionException;
import com.myhexin.zixun.flow.gpt.plugin.base.spi.ExecutionLogCollector;
import com.myhexin.zixun.flow.gpt.plugin.redis.cache.dto.ComponentResult;
import com.myhexin.zixun.flow.gpt.plugin.redis.cache.service.CacheService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

public class RedisCacheComponentTest {

    @Mock
    private CacheService mockCacheService;

    @Mock
    private ExecutionLogCollector mockLogCollector; // Though not directly used in component's logic

    @InjectMocks
    private RedisCacheComponent redisCacheComponent;

    private static final int DEFAULT_TTL_SECONDS = 86400;
    private static final int DEFAULT_MAX_ELEMENTS = 10000;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    private Map<String, Object> baseInputMap(String type, String key, String operation) {
        Map<String, Object> input = new HashMap<>();
        input.put("type", type);
        input.put("key", key);
        input.put("operation", operation);
        return input;
    }

    // Parameter Parsing and Key Construction Tests
    @Test(expected = ExecutionException.class)
    public void testCmpProcess_Parser_MissingRequiredField_Type() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap(null, "testKey", "READ");
        // inputMap.remove("type"); // type is null from baseInputMap
        inputMap.put("keySuffix", "testSuffix"); // Add required keySuffix
        redisCacheComponent.cmpProcess(inputMap);
    }

    @Test(expected = ExecutionException.class)
    public void testCmpProcess_Parser_MissingRequiredField_Key() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("SINGLE", null, "READ");
        // inputMap.remove("key"); // key is null from baseInputMap
        inputMap.put("keySuffix", "testSuffix"); // Add required keySuffix
        redisCacheComponent.cmpProcess(inputMap);
    }

    @Test(expected = ExecutionException.class)
    public void testCmpProcess_Parser_MissingRequiredField_Operation() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("SINGLE", "testKey", null);
        // inputMap.remove("operation"); // operation is null from baseInputMap
        inputMap.put("keySuffix", "testSuffix"); // Add required keySuffix
        redisCacheComponent.cmpProcess(inputMap);
    }

    @Test(expected = ExecutionException.class)
    public void testCmpProcess_Parser_MissingRequiredField_KeySuffix() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("SINGLE", "testKey", "READ");
        // keySuffix is deliberately not added
        redisCacheComponent.cmpProcess(inputMap);
    }

    @Test(expected = ExecutionException.class)
    public void testCmpProcess_Parser_IncompatibleTypeOperation() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("SINGLE", "testKey", "ADD"); // ADD is ZSET op
        inputMap.put("keySuffix", "testSuffix"); // Add required keySuffix
        inputMap.put("value", "testValue"); // Required for ADD
        redisCacheComponent.cmpProcess(inputMap);
    }

    @Test
    public void testCmpProcess_EffectiveKey_WithSuffix() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("SINGLE", "baseKey", "READ");
        inputMap.put("keySuffix", "suffix"); // This test is valid as keySuffix is provided

        when(mockCacheService.getValue("baseKey:suffix")).thenReturn("value");
        redisCacheComponent.cmpProcess(inputMap);
        verify(mockCacheService).getValue("baseKey:suffix");
    }

    @Test(expected = ExecutionException.class) // MODIFIED: Expect parser exception
    public void testCmpProcess_EffectiveKey_WithoutSuffix_ThrowsException() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("SINGLE", "baseKey", "READ");
        // keySuffix is NOT provided, parser will throw ExecutionException
        redisCacheComponent.cmpProcess(inputMap);
    }

    @Test(expected = ExecutionException.class) // MODIFIED: Expect parser exception
    public void testCmpProcess_EffectiveKey_WithBlankSuffix_ThrowsException() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("SINGLE", "baseKey", "READ");
        inputMap.put("keySuffix", "   "); // Blank suffix, parser will throw ExecutionException
        redisCacheComponent.cmpProcess(inputMap);
    }


    // SINGLE_WRITE
    @Test
    public void testCmpProcess_SingleWrite_Success_WithTtl() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("SINGLE", "sKey1", "WRITE");
        inputMap.put("keySuffix", "testSuffix"); // ADDED: keySuffix is mandatory
        inputMap.put("value", "sValue1");
        inputMap.put("ttlSeconds", 3600);

        String jsonResult = redisCacheComponent.cmpProcess(inputMap);
        ComponentResult<?> result = JSON.parseObject(jsonResult, ComponentResult.class);

        assertTrue(result.isSuccess());
        assertNull(result.getData());
        verify(mockCacheService).putValue("sKey1", "sValue1", 3600);
    }

    @Test
    public void testCmpProcess_SingleWrite_Success_DefaultTtl() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("SINGLE", "sKey2", "WRITE");
        inputMap.put("keySuffix", "testSuffix"); // ADDED: keySuffix is mandatory
        inputMap.put("value", "sValue2");
        // No ttlSeconds, should use default

        String jsonResult = redisCacheComponent.cmpProcess(inputMap);
        ComponentResult<?> result = JSON.parseObject(jsonResult, ComponentResult.class);

        assertTrue(result.isSuccess());
        assertNull(result.getData());
        verify(mockCacheService).putValue("sKey2", "sValue2", DEFAULT_TTL_SECONDS);
    }

    // SINGLE_READ
    @Test
    public void testCmpProcess_SingleRead_WithValue() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("SINGLE", "sKey3", "READ");
        inputMap.put("keySuffix", "testSuffix"); // ADDED: keySuffix is mandatory
        String expectedData = "cachedData";
        when(mockCacheService.getValue("sKey3:testSuffix")).thenReturn(expectedData); // Adjusted expected key

        String jsonResult = redisCacheComponent.cmpProcess(inputMap);
        ComponentResult<String> result = JSON.parseObject(jsonResult, new TypeReference<ComponentResult<String>>(){});

        assertTrue(result.isSuccess());
        assertEquals(expectedData, result.getData());
        verify(mockCacheService).getValue("sKey3:testSuffix"); // Adjusted expected key
    }

    @Test
    public void testCmpProcess_SingleRead_NullValue() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("SINGLE", "sKey4", "READ");
        inputMap.put("keySuffix", "testSuffix"); // ADDED: keySuffix is mandatory
        when(mockCacheService.getValue("sKey4:testSuffix")).thenReturn(null); // Adjusted expected key

        String jsonResult = redisCacheComponent.cmpProcess(inputMap);
        ComponentResult<?> result = JSON.parseObject(jsonResult, ComponentResult.class);

        assertTrue(result.isSuccess());
        assertNull(result.getData());
        verify(mockCacheService).getValue("sKey4:testSuffix"); // Adjusted expected key
    }

    // ZSET_OVERWRITE
    @Test
    public void testCmpProcess_ZsetOverwrite_Success_WithParams() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("ZSET", "zKey1", "OVERWRITE");
        inputMap.put("keySuffix", "testSuffix"); // ADDED: keySuffix is mandatory
        List<String> values = Arrays.asList("zVal1", "zVal2");
        inputMap.put("value", JSON.toJSONString(values));
        inputMap.put("ttlSeconds", 1800);
        inputMap.put("maxElements", 50);

        String jsonResult = redisCacheComponent.cmpProcess(inputMap);
        ComponentResult<?> result = JSON.parseObject(jsonResult, ComponentResult.class);

        assertTrue(result.isSuccess());
        assertNull(result.getData());
        verify(mockCacheService).putList("zKey1:testSuffix", values, 1800, 50); // Adjusted expected key
    }

    @Test
    public void testCmpProcess_ZsetOverwrite_Success_DefaultParams() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("ZSET", "zKey2", "OVERWRITE");
        inputMap.put("keySuffix", "testSuffix"); // ADDED: keySuffix is mandatory
        List<String> values = Arrays.asList("zValDefault1", "zValDefault2");
        inputMap.put("value", JSON.toJSONString(values));

        String jsonResult = redisCacheComponent.cmpProcess(inputMap);
        ComponentResult<?> result = JSON.parseObject(jsonResult, ComponentResult.class);

        assertTrue(result.isSuccess());
        assertNull(result.getData());
        verify(mockCacheService).putList("zKey2:testSuffix", values, DEFAULT_TTL_SECONDS, DEFAULT_MAX_ELEMENTS); // Adjusted expected key
    }


    // ZSET_ADD
    @Test
    public void testCmpProcess_ZsetAdd_Success_WithParams() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("ZSET", "zKey3", "ADD");
        inputMap.put("keySuffix", "testSuffix"); // ADDED: keySuffix is mandatory
        inputMap.put("value", "newZVal");
        inputMap.put("ttlSeconds", 1200);
        inputMap.put("maxElements", 30);

        String jsonResult = redisCacheComponent.cmpProcess(inputMap);
        ComponentResult<?> result = JSON.parseObject(jsonResult, ComponentResult.class);

        assertTrue(result.isSuccess());
        assertNull(result.getData());
        verify(mockCacheService).appendToList("zKey3:testSuffix", "newZVal", 1200, 30); // Adjusted expected key
    }

    @Test
    public void testCmpProcess_ZsetAdd_Success_DefaultParams() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("ZSET", "zKey4", "ADD");
        inputMap.put("keySuffix", "testSuffix"); // ADDED: keySuffix is mandatory
        inputMap.put("value", "newZValDefault");

        String jsonResult = redisCacheComponent.cmpProcess(inputMap);
        ComponentResult<?> result = JSON.parseObject(jsonResult, ComponentResult.class);

        assertTrue(result.isSuccess());
        assertNull(result.getData());
        verify(mockCacheService).appendToList("zKey4:testSuffix", "newZValDefault", DEFAULT_TTL_SECONDS, DEFAULT_MAX_ELEMENTS); // Adjusted expected key
    }

    // ZSET_READ_ALL
    @Test
    public void testCmpProcess_ZsetReadAll_WithData() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("ZSET", "zKey5", "READ_ALL");
        inputMap.put("keySuffix", "testSuffix"); // ADDED: keySuffix is mandatory
        List<String> expectedList = Arrays.asList("zA", "zB");
        when(mockCacheService.getList("zKey5:testSuffix")).thenReturn(expectedList); // Adjusted expected key

        String jsonResult = redisCacheComponent.cmpProcess(inputMap);
        ComponentResult<List<String>> result = JSON.parseObject(jsonResult, new TypeReference<ComponentResult<List<String>>>(){});

        assertTrue(result.isSuccess());
        assertEquals(expectedList, result.getData());
        verify(mockCacheService).getList("zKey5:testSuffix"); // Adjusted expected key
    }

    @Test
    public void testCmpProcess_ZsetReadAll_EmptyData() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("ZSET", "zKey6", "READ_ALL");
        inputMap.put("keySuffix", "testSuffix"); // ADDED: keySuffix is mandatory
        when(mockCacheService.getList("zKey6:testSuffix")).thenReturn(Collections.emptyList()); // Adjusted expected key

        String jsonResult = redisCacheComponent.cmpProcess(inputMap);
        ComponentResult<List<String>> result = JSON.parseObject(jsonResult, new TypeReference<ComponentResult<List<String>>>(){});

        assertTrue(result.isSuccess());
        assertTrue(result.getData().isEmpty());
        verify(mockCacheService).getList("zKey6:testSuffix"); // Adjusted expected key
    }

    // ZSET_READ_CONDITIONAL
    @Test
    public void testCmpProcess_ZsetReadConditional_WithData() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("ZSET", "zKey7", "READ_CONDITION"); // MODIFIED operation name
        inputMap.put("keySuffix", "testSuffix"); // ADDED: keySuffix is mandatory
        long timestamp = System.currentTimeMillis();
        inputMap.put("conditionTimestamp", timestamp);
        List<String> expectedList = Arrays.asList("zC", "zD");
        when(mockCacheService.getListSince("zKey7:testSuffix", timestamp)).thenReturn(expectedList); // Adjusted expected key

        String jsonResult = redisCacheComponent.cmpProcess(inputMap);
        ComponentResult<List<String>> result = JSON.parseObject(jsonResult, new TypeReference<ComponentResult<List<String>>>(){});

        assertTrue(result.isSuccess());
        assertEquals(expectedList, result.getData());
        verify(mockCacheService).getListSince("zKey7:testSuffix", timestamp); // Adjusted expected key
    }

    // ZSET_CHECK_EXIST
    @Test
    public void testCmpProcess_ZsetCheckExist_True() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("ZSET", "zKey8", "CHECK_EXIST");
        inputMap.put("keySuffix", "testSuffix"); // ADDED: keySuffix is mandatory
        inputMap.put("value", "itemExists");
        when(mockCacheService.containsInList("zKey8:testSuffix", "itemExists")).thenReturn(true); // Adjusted expected key

        String jsonResult = redisCacheComponent.cmpProcess(inputMap);
        ComponentResult<Boolean> result = JSON.parseObject(jsonResult, new TypeReference<ComponentResult<Boolean>>(){});

        assertTrue(result.isSuccess());
        assertTrue(result.getData());
        verify(mockCacheService).containsInList("zKey8:testSuffix", "itemExists"); // Adjusted expected key
    }

    @Test
    public void testCmpProcess_ZsetCheckExist_False() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("ZSET", "zKey9", "CHECK_EXIST");
        inputMap.put("keySuffix", "testSuffix"); // ADDED: keySuffix is mandatory
        inputMap.put("value", "itemNotExists");
        when(mockCacheService.containsInList("zKey9:testSuffix", "itemNotExists")).thenReturn(false); // Adjusted expected key

        String jsonResult = redisCacheComponent.cmpProcess(inputMap);
        ComponentResult<Boolean> result = JSON.parseObject(jsonResult, new TypeReference<ComponentResult<Boolean>>(){});

        assertTrue(result.isSuccess());
        assertFalse(result.getData());
        verify(mockCacheService).containsInList("zKey9:testSuffix", "itemNotExists"); // Adjusted expected key
    }


    // CacheService Error Handling
    @Test(expected = ExecutionException.class)
    public void testCmpProcess_CacheServiceThrowsExecutionException() throws ExecutionException {
        Map<String, Object> inputMap = baseInputMap("SINGLE", "errKey1", "READ");
        inputMap.put("keySuffix", "testSuffix"); // ADDED: keySuffix is mandatory
        when(mockCacheService.getValue("errKey1:testSuffix")).thenThrow(new ExecutionException("Service layer error")); // Adjusted expected key
        redisCacheComponent.cmpProcess(inputMap);
    }

    @Test
    public void testCmpProcess_CacheServiceThrowsRuntimeException() {
        Map<String, Object> inputMap = baseInputMap("SINGLE", "errKey2", "READ");
        inputMap.put("keySuffix", "runtimeSuffix"); // Ensure all required fields for parser are present

        // Effective key will be "errKey2:runtimeSuffix"
        when(mockCacheService.getValue(eq("errKey2:runtimeSuffix")))
            .thenThrow(new RuntimeException("Unexpected service problem"));

        try {
            redisCacheComponent.cmpProcess(inputMap);
            fail("Should have thrown ExecutionException");
        } catch (ExecutionException e) {
            // These assertions should now pass if the service exception is correctly wrapped
            assertEquals("Unexpected error during " + redisCacheComponent.getName() + " execution: Unexpected service problem", e.getMessage());
            assertNotNull("Cause should not be null", e.getCause());
            assertTrue("Cause should be RuntimeException", e.getCause() instanceof RuntimeException);
            assertEquals("Original RuntimeException message should be preserved in cause", "Unexpected service problem", e.getCause().getMessage());
        }
    }

    // Test DELETE operation (not explicitly in CacheOperation enum, but good to consider if it were)
    // Based on the provided CacheOperation enum, DELETE is not an operation.
    // If it were, tests would be similar to other write ops.

    // Test EXISTS operation (not explicitly in CacheOperation enum)
    // If it were, tests would be similar to read ops returning boolean.

    // Verify log collector is not null after setup (basic DI check)
    @Test
    public void testLogCollectorNotNull() {
        // This test just ensures that mockLogCollector is injected or available if needed.
        // No direct calls are made to it by RedisCacheComponent in the provided code.
        // If RedisCacheComponent used it, we would verify those calls.
        // For now, this is a trivial check.
        // redisCacheComponent.setLogCollector(mockLogCollector); // If AbstractNodeComponent needs it explicitly set
        assertNotNull(mockLogCollector); // Just check our mock setup
    }
}
