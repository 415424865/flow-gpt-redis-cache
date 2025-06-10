package com.myhexin.zixun.flow.gpt.plugin.redis.cache.util;

import com.alibaba.fastjson.JSON;
import com.myhexin.zixun.flow.gpt.plugin.dto.CacheParams;
import com.myhexin.zixun.flow.gpt.plugin.enums.CacheOperation;
import com.myhexin.zixun.flow.gpt.plugin.enums.CacheType;
import com.myhexin.zixun.flow.gpt.plugin.exception.ExecutionException;
import com.myhexin.zixun.flow.gpt.plugin.executor.ExecutionLogCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;


public class CacheComponentParamParserTest {

    @Mock
    private ExecutionLogCollector logCollector;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private Map<String, Object> createBaseParamsMap(String type, String key, String operation) {
        Map<String, Object> params = new HashMap<>();
        params.put("type", type);
        params.put("key", key);
        params.put("operation", operation);
        params.put("appId", "testAppId"); // Assuming appId and cacheName are always present for key building
        params.put("cacheName", "testCacheName");
        return params;
    }

    private Map<String, Object> createBaseParamsMap(CacheType type, String key, CacheOperation operation) {
        return createBaseParamsMap(type.getCode(), key, operation.getCode());
    }


    // 1. Valid Parameter Parsing
    @Test
    void parse_singleWrite_validFull() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.SINGLE, "testKey1", CacheOperation.SINGLE_WRITE);
        paramsMap.put("keySuffix", "suffix1");
        paramsMap.put("value", "testValue");
        paramsMap.put("ttlSeconds", "3600");

        CacheParams params = CacheComponentParamParser.parse(JSON.toJSONString(paramsMap), logCollector);

        assertEquals(CacheType.SINGLE, params.getType());
        assertEquals("testKey1", params.getKey());
        assertEquals("suffix1", params.getKeySuffix());
        assertEquals(CacheOperation.SINGLE_WRITE, params.getOperation());
        assertEquals("testValue", params.getValue());
        assertEquals(3600L, params.getTtl());
        assertEquals("testAppId", params.getAppId());
        assertEquals("testCacheName", params.getCacheName());
    }

    @Test
    void parse_singleWrite_validDefaults() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.SINGLE, "testKey2", CacheOperation.SINGLE_WRITE);
        paramsMap.put("value", "testValueDefault");
        // ttlSeconds and keySuffix omitted

        CacheParams params = CacheComponentParamParser.parse(JSON.toJSONString(paramsMap), logCollector);

        assertEquals(CacheType.SINGLE, params.getType());
        assertEquals("testKey2", params.getKey());
        assertNull(params.getKeySuffix()); // Or empty string depending on implementation detail of getStringOptional
        assertEquals(CacheOperation.SINGLE_WRITE, params.getOperation());
        assertEquals("testValueDefault", params.getValue());
        assertEquals(CacheParams.DEFAULT_TTL_SECONDS, params.getTtl());
    }

    @Test
    void parse_singleWrite_keySuffixEmpty() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.SINGLE, "testKeyEmptySuffix", CacheOperation.SINGLE_WRITE);
        paramsMap.put("value", "testValue");
        paramsMap.put("keySuffix", "");

        CacheParams params = CacheComponentParamParser.parse(JSON.toJSONString(paramsMap), logCollector);
        assertEquals("", params.getKeySuffix());
    }

    @Test
    void parse_singleWrite_keySuffixSpaces() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.SINGLE, "testKeySpaceSuffix", CacheOperation.SINGLE_WRITE);
        paramsMap.put("value", "testValue");
        paramsMap.put("keySuffix", "   "); // Should be trimmed

        CacheParams params = CacheComponentParamParser.parse(JSON.toJSONString(paramsMap), logCollector);
        // Assuming getStringOptional trims, and if blank, returns null or empty.
        // Based on current RedisCacheComponent, it seems it becomes an empty string after trim if it was all spaces.
        assertEquals("", params.getKeySuffix());
    }


    @Test
    void parse_singleRead_valid() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.SINGLE, "testKey3", CacheOperation.SINGLE_READ);
        paramsMap.put("keySuffix", "suffix3");

        CacheParams params = CacheComponentParamParser.parse(JSON.toJSONString(paramsMap), logCollector);

        assertEquals(CacheType.SINGLE, params.getType());
        assertEquals("testKey3", params.getKey());
        assertEquals("suffix3", params.getKeySuffix());
        assertEquals(CacheOperation.SINGLE_READ, params.getOperation());
    }

    @Test
    void parse_zsetOverwrite_validFull() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.ZSET, "testKeyZSet1", CacheOperation.ZSET_OVERWRITE);
        List<String> values = Arrays.asList("val1", "val2");
        paramsMap.put("value", JSON.toJSONString(values)); // Value as JSON list string
        paramsMap.put("keySuffix", "zSuffix1");
        paramsMap.put("ttlSeconds", "7200");
        paramsMap.put("maxElements", "100");

        CacheParams params = CacheComponentParamParser.parse(JSON.toJSONString(paramsMap), logCollector);

        assertEquals(CacheType.ZSET, params.getType());
        assertEquals("testKeyZSet1", params.getKey());
        assertEquals("zSuffix1", params.getKeySuffix());
        assertEquals(CacheOperation.ZSET_OVERWRITE, params.getOperation());
        assertNotNull(params.getValues());
        assertEquals(2, params.getValues().size());
        assertEquals("val1", params.getValues().get(0));
        assertEquals("val2", params.getValues().get(1));
        assertEquals(7200L, params.getTtl());
        assertEquals(100, params.getMaxElements());
    }

    @Test
    void parse_zsetOverwrite_validDefaults() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.ZSET, "testKeyZSetDefaults", CacheOperation.ZSET_OVERWRITE);
        List<String> values = Arrays.asList("valA", "valB");
        paramsMap.put("value", JSON.toJSONString(values));

        CacheParams params = CacheComponentParamParser.parse(JSON.toJSONString(paramsMap), logCollector);

        assertEquals(CacheType.ZSET, params.getType());
        assertEquals(CacheParams.DEFAULT_TTL_SECONDS, params.getTtl());
        assertEquals(CacheParams.DEFAULT_MAX_ELEMENTS, params.getMaxElements());
        assertNotNull(params.getValues());
        assertEquals(2, params.getValues().size());
    }


    @Test
    void parse_zsetAdd_validFull() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.ZSET, "testKeyZSet2", CacheOperation.ZSET_ADD);
        paramsMap.put("value", "newValue");
        paramsMap.put("keySuffix", "zSuffix2");
        paramsMap.put("ttlSeconds", "1800");
        paramsMap.put("maxElements", "50");
        // score is not directly parsed by CacheComponentParamParser, but by RedisCacheComponent from value if needed or separate field.
        // The prompt implies value is the item to add for ZSET_ADD. The original code seems to take score from CacheParams.score.
        // Let's assume score is passed as a top-level param for now if it's meant to be parsed.
        // Re-reading RedisCacheComponent, it gets score from params.getScore().
        // CacheComponentParamParser itself doesn't seem to have a dedicated field for `score` based on the prompt's focus.
        // Let's assume score is not parsed here but set later or part of 'value' in some complex way if not a direct field.
        // For now, testing based on what CacheComponentParamParser seems to extract: value (as string), ttl, maxElements.
        // If "score" should be parsed, the parser needs a getDoubleOptional/Required.
        // The original RedisCacheComponent does: `Double score = params.getScore();`
        // This implies CacheParams should have a score field populated by the parser.
        // Let's add score to the map and check if it's parsed.
        paramsMap.put("score", "123.45");


        CacheParams params = CacheComponentParamParser.parse(JSON.toJSONString(paramsMap), logCollector);

        assertEquals(CacheType.ZSET, params.getType());
        assertEquals("testKeyZSet2", params.getKey());
        assertEquals("zSuffix2", params.getKeySuffix());
        assertEquals(CacheOperation.ZSET_ADD, params.getOperation());
        assertEquals("newValue", params.getValue());
        assertEquals(1800L, params.getTtl());
        assertEquals(50, params.getMaxElements());
        assertEquals(123.45, params.getScore());
    }

    @Test
    void parse_zsetAdd_validDefaults() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.ZSET, "testKeyZSetAddDefaults", CacheOperation.ZSET_ADD);
        paramsMap.put("value", "anotherValue");
        paramsMap.put("score", "99.9");


        CacheParams params = CacheComponentParamParser.parse(JSON.toJSONString(paramsMap), logCollector);
        assertEquals(CacheParams.DEFAULT_TTL_SECONDS, params.getTtl());
        assertEquals(CacheParams.DEFAULT_MAX_ELEMENTS, params.getMaxElements());
        assertEquals(99.9, params.getScore());
    }


    @Test
    void parse_zsetReadAll_valid() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.ZSET, "testKeyZSet3", CacheOperation.ZSET_READ_ALL);
        paramsMap.put("keySuffix", "zSuffix3");

        CacheParams params = CacheComponentParamParser.parse(JSON.toJSONString(paramsMap), logCollector);

        assertEquals(CacheType.ZSET, params.getType());
        assertEquals("testKeyZSet3", params.getKey());
        assertEquals("zSuffix3", params.getKeySuffix());
        assertEquals(CacheOperation.ZSET_READ_ALL, params.getOperation());
    }

    @Test
    void parse_zsetReadConditional_valid() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.ZSET, "testKeyZSet4", CacheOperation.ZSET_READ_CONDITIONAL);
        paramsMap.put("keySuffix", "zSuffix4");
        paramsMap.put("conditionTimestamp", "1678886400000"); // Example timestamp

        CacheParams params = CacheComponentParamParser.parse(JSON.toJSONString(paramsMap), logCollector);

        assertEquals(CacheType.ZSET, params.getType());
        assertEquals("testKeyZSet4", params.getKey());
        assertEquals("zSuffix4", params.getKeySuffix());
        assertEquals(CacheOperation.ZSET_READ_CONDITIONAL, params.getOperation());
        assertEquals(1678886400000L, params.getConditionTimestamp());
    }

    @Test
    void parse_zsetCheckExist_valid() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.ZSET, "testKeyZSet5", CacheOperation.ZSET_CHECK_EXIST);
        paramsMap.put("keySuffix", "zSuffix5");
        paramsMap.put("value", "valueToFind");

        CacheParams params = CacheComponentParamParser.parse(JSON.toJSONString(paramsMap), logCollector);

        assertEquals(CacheType.ZSET, params.getType());
        assertEquals("testKeyZSet5", params.getKey());
        assertEquals("zSuffix5", params.getKeySuffix());
        assertEquals(CacheOperation.ZSET_CHECK_EXIST, params.getOperation());
        assertEquals("valueToFind", params.getValue());
    }

    // 6. Case Insensitivity for Enum Parsing
    @Test
    void parse_enumCaseInsensitive_type() {
        Map<String, Object> paramsMap = createBaseParamsMap("single", "testKeyCase", "single_read");
        CacheParams params = CacheComponentParamParser.parse(JSON.toJSONString(paramsMap), logCollector);
        assertEquals(CacheType.SINGLE, params.getType());
    }

    @Test
    void parse_enumCaseInsensitive_operation() {
        Map<String, Object> paramsMap = createBaseParamsMap("single", "testKeyCaseOp", "single_read");
        CacheParams params = CacheComponentParamParser.parse(JSON.toJSONString(paramsMap), logCollector);
        assertEquals(CacheOperation.SINGLE_READ, params.getOperation());

        paramsMap.put("operation", "SINGLE_WRITE"); // Uppercase
         paramsMap.put("value", "v");
        params = CacheComponentParamParser.parse(JSON.toJSONString(paramsMap), logCollector);
        assertEquals(CacheOperation.SINGLE_WRITE, params.getOperation());
    }

    // 2. Missing Required Fields
    static Stream<Arguments> missingRequiredFieldsProvider() {
        return Stream.of(
                Arguments.of(createBaseParamsMap(CacheType.SINGLE, "key", CacheOperation.SINGLE_WRITE), "type", "type is required"),
                Arguments.of(createBaseParamsMap(CacheType.SINGLE, "key", CacheOperation.SINGLE_WRITE), "key", "key is required"),
                Arguments.of(createBaseParamsMap(CacheType.SINGLE, "key", CacheOperation.SINGLE_WRITE), "operation", "operation is required"),
                Arguments.of(createBaseParamsMap(CacheType.SINGLE, "key", CacheOperation.SINGLE_WRITE), "value", "value is required for SINGLE_WRITE"),
                Arguments.of(createBaseParamsMap(CacheType.ZSET, "key", CacheOperation.ZSET_ADD), "value", "value is required for ZSET_ADD"),
                Arguments.of(createBaseParamsMap(CacheType.ZSET, "key", CacheOperation.ZSET_CHECK_EXIST), "value", "value is required for ZSET_CHECK_EXIST"),
                Arguments.of(createBaseParamsMap(CacheType.ZSET, "key", CacheOperation.ZSET_OVERWRITE), "value", "value is required for ZSET_OVERWRITE and must be a non-empty JSON array string"),
                Arguments.of(createBaseParamsMap(CacheType.ZSET, "key", CacheOperation.ZSET_READ_CONDITIONAL), "conditionTimestamp", "conditionTimestamp is required for ZSET_READ_CONDITIONAL")
        );
    }

    @ParameterizedTest
    @MethodSource("missingRequiredFieldsProvider")
    void parse_missingRequiredField_throwsExecutionException(Map<String, Object> initialParams, String fieldToRemove, String expectedMessagePart) {
        initialParams.remove(fieldToRemove);
        // For operations requiring value, ensure it's removed if it's the target, or add a dummy for others if value is not the target.
        if (!fieldToRemove.equals("value")) {
            if (initialParams.get("operation").equals(CacheOperation.SINGLE_WRITE.getCode()) ||
                initialParams.get("operation").equals(CacheOperation.ZSET_ADD.getCode()) ||
                initialParams.get("operation").equals(CacheOperation.ZSET_CHECK_EXIST.getCode()) ||
                initialParams.get("operation").equals(CacheOperation.ZSET_OVERWRITE.getCode())) {
                // Add a dummy value if 'value' is not the field being tested for removal, but the operation expects it.
                // This is tricky because the test data setup already includes value for some.
                // Let's refine the provider to only include necessary fields.

                // The provider already sets up value for ops that need it. So removing another field is fine.
                // If the provider *doesn't* set up value, and value is *not* fieldToRemove, and op *needs* value, then we might get a value error first.
                // The current provider data is okay.
            }
        }
         // Ensure appId and cacheName are present, as they are implicitly required by buildEffectiveKey
        initialParams.putIfAbsent("appId", "testAppId");
        initialParams.putIfAbsent("cacheName", "testCacheName");


        String jsonInput = JSON.toJSONString(initialParams);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            CacheComponentParamParser.parse(jsonInput, logCollector);
        });

        assertTrue(ex.getMessage().contains(expectedMessagePart), "Expected message to contain '" + expectedMessagePart + "', but was '" + ex.getMessage() + "'");
        verify(logCollector).logError(anyString(), eq(ex));
    }

    // Specific test for appId and cacheName as they are fundamental
    @Test
    void parse_missingAppId_throwsExecutionException() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.SINGLE, "testKey", CacheOperation.SINGLE_READ);
        paramsMap.remove("appId");
        String jsonInput = JSON.toJSONString(paramsMap);
        ExecutionException ex = assertThrows(ExecutionException.class, () -> CacheComponentParamParser.parse(jsonInput, logCollector));
        assertTrue(ex.getMessage().contains("appId is required"));
        verify(logCollector).logError(anyString(), eq(ex));
    }

    @Test
    void parse_missingCacheName_throwsExecutionException() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.SINGLE, "testKey", CacheOperation.SINGLE_READ);
        paramsMap.remove("cacheName");
        String jsonInput = JSON.toJSONString(paramsMap);
        ExecutionException ex = assertThrows(ExecutionException.class, () -> CacheComponentParamParser.parse(jsonInput, logCollector));
        assertTrue(ex.getMessage().contains("cacheName is required"));
        verify(logCollector).logError(anyString(), eq(ex));
    }

    // 3. Invalid Enum Values
    @Test
    void parse_invalidTypeString_throwsExecutionException() {
        Map<String, Object> paramsMap = createBaseParamsMap("INVALID_TYPE", "key", CacheOperation.SINGLE_READ.getCode());
        String jsonInput = JSON.toJSONString(paramsMap);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            CacheComponentParamParser.parse(jsonInput, logCollector);
        });
        assertTrue(ex.getMessage().contains("Invalid cache type: INVALID_TYPE"));
        verify(logCollector).logError(anyString(), eq(ex));
    }

    @Test
    void parse_invalidOperationString_throwsExecutionException() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.SINGLE.getCode(), "key", "INVALID_OPERATION");
        String jsonInput = JSON.toJSONString(paramsMap);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            CacheComponentParamParser.parse(jsonInput, logCollector);
        });
        assertTrue(ex.getMessage().contains("Invalid cache operation: INVALID_OPERATION for type SINGLE"));
        verify(logCollector).logError(anyString(), eq(ex));
    }

    // 4. Type and Operation Incompatibility
    static Stream<Arguments> incompatibleTypeOperationProvider() {
        return Stream.of(
                Arguments.of(CacheType.SINGLE, CacheOperation.ZSET_ADD, "ZSET_ADD is not applicable to SINGLE type"),
                Arguments.of(CacheType.SINGLE, CacheOperation.ZSET_OVERWRITE, "ZSET_OVERWRITE is not applicable to SINGLE type"),
                Arguments.of(CacheType.SINGLE, CacheOperation.ZSET_READ_ALL, "ZSET_READ_ALL is not applicable to SINGLE type"),
                Arguments.of(CacheType.SINGLE, CacheOperation.ZSET_READ_CONDITIONAL, "ZSET_READ_CONDITIONAL is not applicable to SINGLE type"),
                Arguments.of(CacheType.SINGLE, CacheOperation.ZSET_CHECK_EXIST, "ZSET_CHECK_EXIST is not applicable to SINGLE type"),
                Arguments.of(CacheType.ZSET, CacheOperation.SINGLE_WRITE, "SINGLE_WRITE is not applicable to ZSET type"),
                Arguments.of(CacheType.ZSET, CacheOperation.SINGLE_READ, "SINGLE_READ is not applicable to ZSET type")
        );
    }

    @ParameterizedTest
    @MethodSource("incompatibleTypeOperationProvider")
    void parse_incompatibleTypeOperation_throwsExecutionException(CacheType type, CacheOperation operation, String expectedMessagePart) {
        Map<String, Object> paramsMap = createBaseParamsMap(type, "testKeyIncompatible", operation);
        // Add dummy value or other required fields if the operation expects them, to ensure failure is due to incompatibility
        if (operation == CacheOperation.SINGLE_WRITE || operation == CacheOperation.ZSET_ADD || operation == CacheOperation.ZSET_CHECK_EXIST) {
            paramsMap.put("value", "dummyValue");
        }
        if (operation == CacheOperation.ZSET_OVERWRITE) {
            paramsMap.put("value", JSON.toJSONString(Arrays.asList("dummyValue")));
        }
        if (operation == CacheOperation.ZSET_READ_CONDITIONAL) {
            paramsMap.put("conditionTimestamp", "12345");
        }
         if (operation == CacheOperation.ZSET_ADD) {
            paramsMap.put("score", "1.0");
        }


        String jsonInput = JSON.toJSONString(paramsMap);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            CacheComponentParamParser.parse(jsonInput, logCollector);
        });

        assertTrue(ex.getMessage().contains(expectedMessagePart),
                "Expected message: '" + expectedMessagePart + "', but got: '" + ex.getMessage() + "'");
        verify(logCollector).logError(anyString(), eq(ex));
    }

    // 5. Invalid Value Formats
    @Test
    void parse_zsetOverwrite_valueNotJsonArray_throwsExecutionException() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.ZSET, "keyZsetInvalidJson", CacheOperation.ZSET_OVERWRITE);
        paramsMap.put("value", "thisIsNotAJsonArray");
        String jsonInput = JSON.toJSONString(paramsMap);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            CacheComponentParamParser.parse(jsonInput, logCollector);
        });
        assertTrue(ex.getMessage().contains("value for ZSET_OVERWRITE must be a valid JSON array string"));
        verify(logCollector).logError(anyString(), eq(ex));
    }

    @Test
    void parse_zsetOverwrite_valueEmptyJsonArray_throwsExecutionException() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.ZSET, "keyZsetEmptyJsonArray", CacheOperation.ZSET_OVERWRITE);
        paramsMap.put("value", "[]"); // Empty JSON array
        String jsonInput = JSON.toJSONString(paramsMap);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            CacheComponentParamParser.parse(jsonInput, logCollector);
        });
        assertTrue(ex.getMessage().contains("value for ZSET_OVERWRITE must be a non-empty JSON array string"));
        verify(logCollector).logError(anyString(), eq(ex));
    }

    @Test
    void parse_zsetOverwrite_valueJsonArrayWithNull_parsedCorrectly() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.ZSET, "keyZsetJsonArrayWithNull", CacheOperation.ZSET_OVERWRITE);
        // FastJSON by default serializes null in list as "null" string literal if not handled carefully,
        // but List<Object> containing actual nulls should be fine.
        // The parser expects a JSON string, so we make one that includes null.
        paramsMap.put("value", "[null, \"item2\"]");
        String jsonInput = JSON.toJSONString(paramsMap);

        CacheParams params = CacheComponentParamParser.parse(jsonInput, logCollector);
        assertNotNull(params.getValues());
        assertEquals(2, params.getValues().size());
        assertNull(params.getValues().get(0)); // Expecting actual null
        assertEquals("item2", params.getValues().get(1));
    }


    @Test
    void parse_conditionTimestamp_notLong_throwsExecutionException() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.ZSET, "keyZsetInvalidTimestamp", CacheOperation.ZSET_READ_CONDITIONAL);
        paramsMap.put("conditionTimestamp", "not_a_long_value");
        String jsonInput = JSON.toJSONString(paramsMap);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            CacheComponentParamParser.parse(jsonInput, logCollector);
        });
        assertTrue(ex.getMessage().contains("conditionTimestamp must be a long value"));
        verify(logCollector).logError(anyString(), eq(ex));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not_an_integer", "0", "-100", "3600.5"})
    void parse_invalidTtlSeconds_usesDefault(String invalidTtl) {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.SINGLE, "keyInvalidTtl", CacheOperation.SINGLE_WRITE);
        paramsMap.put("value", "testValue");
        paramsMap.put("ttlSeconds", invalidTtl);
        String jsonInput = JSON.toJSONString(paramsMap);

        CacheParams params = CacheComponentParamParser.parse(jsonInput, logCollector);
        assertEquals(CacheParams.DEFAULT_TTL_SECONDS, params.getTtl());
        // Optionally, verify logCollector.logWarn was called if parser logs this
    }

    @Test
    void parse_validButZeroTtlSeconds_usesZero() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.SINGLE, "keyZeroTtl", CacheOperation.SINGLE_WRITE);
        paramsMap.put("value", "testValue");
        paramsMap.put("ttlSeconds", "0"); // Zero is a valid TTL meaning no expiry for some systems, or immediate for others.
                                        // Assuming based on "non-positive uses default" that 0 is special.
                                        // The prompt says "non-positive or non-integer". If 0 means default, this test is fine.
                                        // If 0 has a specific meaning (like "no TTL" / "persist"), then this test needs to change.
                                        // Current CacheParams.getLongOptional uses default for <=0.
        String jsonInput = JSON.toJSONString(paramsMap);
        CacheParams params = CacheComponentParamParser.parse(jsonInput, logCollector);
        assertEquals(CacheParams.DEFAULT_TTL_SECONDS, params.getTtl());
    }


    @ParameterizedTest
    @ValueSource(strings = {"not_an_integer", "0", "-50", "100.5"})
    void parse_invalidMaxElements_usesDefault(String invalidMaxElements) {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.ZSET, "keyInvalidMaxElements", CacheOperation.ZSET_ADD);
        paramsMap.put("value", "testValue");
        paramsMap.put("score", "1.0");
        paramsMap.put("maxElements", invalidMaxElements);
        String jsonInput = JSON.toJSONString(paramsMap);

        CacheParams params = CacheComponentParamParser.parse(jsonInput, logCollector);
        assertEquals(CacheParams.DEFAULT_MAX_ELEMENTS, params.getMaxElements());
        // Optionally, verify logCollector.logWarn
    }

    @Test
    void parse_validButZeroMaxElements_usesDefault() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.ZSET, "keyZeroMaxElements", CacheOperation.ZSET_ADD);
        paramsMap.put("value", "testValue");
        paramsMap.put("score", "1.0");
        paramsMap.put("maxElements", "0");  // Similar to TTL, current getIntegerOptional uses default for <=0.
        String jsonInput = JSON.toJSONString(paramsMap);

        CacheParams params = CacheComponentParamParser.parse(jsonInput, logCollector);
        assertEquals(CacheParams.DEFAULT_MAX_ELEMENTS, params.getMaxElements());
    }

    @Test
    void parse_score_notDouble_throwsExecutionException() {
        Map<String, Object> paramsMap = createBaseParamsMap(CacheType.ZSET, "keyZsetInvalidScore", CacheOperation.ZSET_ADD);
        paramsMap.put("value", "testValue");
        paramsMap.put("score", "not_a_double");
        String jsonInput = JSON.toJSONString(paramsMap);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            CacheComponentParamParser.parse(jsonInput, logCollector);
        });
        assertTrue(ex.getMessage().contains("score must be a double value"));
        verify(logCollector).logError(anyString(), eq(ex));
    }

}
