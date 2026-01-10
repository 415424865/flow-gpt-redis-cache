package com.myhexin.zixun.flow.gpt.plugin.redis.cache.util;

import com.alibaba.fastjson.JSON;
import com.myhexin.zixun.flow.gpt.engine.api.exception.ExecutionException;
import com.myhexin.zixun.flow.gpt.plugin.redis.cache.dto.CacheParams;
import com.myhexin.zixun.flow.gpt.plugin.redis.cache.enums.CacheOperation;
import com.myhexin.zixun.flow.gpt.plugin.redis.cache.enums.CacheType;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class CacheComponentParamParserTest {

    private static final int DEFAULT_TTL_SECONDS = 86400; // As per CacheComponentParamParser private static final
    private static final int DEFAULT_MAX_ELEMENTS = 10000; // As per CacheComponentParamParser private static final

    private Map<String, Object> createBaseInputMap(String type, String key, String operation) {
        Map<String, Object> input = new HashMap<>();
        input.put("type", type);
        input.put("key", key);
        input.put("operation", operation);
        return input;
    }

    // Happy Paths

    @Test
    public void testParse_SingleWrite_Success() {
        Map<String, Object> input = createBaseInputMap("SINGLE", "testKey", "WRITE");
        input.put("keySuffix", "suffix");
        input.put("value", "testValue");
        input.put("ttlSeconds", 3600);

        CacheParams params = CacheComponentParamParser.parse(input);

        assertEquals(CacheType.SINGLE, params.getType());
        assertEquals("testKey", params.getKey());
        assertEquals("suffix", params.getKeySuffix());
        assertEquals(CacheOperation.SINGLE_WRITE, params.getOperation());
        assertEquals("testValue", params.getValue());
        assertEquals(Integer.valueOf(3600), params.getTtlSeconds());
        assertNull(params.getMaxElements()); // Not applicable for SINGLE_WRITE
    }

    @Test
    public void testParse_SingleWrite_Success_WithWhitespaceInRequiredFields() {
        Map<String, Object> input = createBaseInputMap("  SINGLE  ", "  testKey  ", "  WRITE  ");
        input.put("keySuffix", "  suffix  ");
        input.put("value", "testValue"); // Value is not trimmed by requireString, it's taken as is.
        input.put("ttlSeconds", 3600);

        CacheParams params = CacheComponentParamParser.parse(input);

        assertEquals(CacheType.SINGLE, params.getType());
        assertEquals("testKey", params.getKey()); // Assuming requireString trims key
        assertEquals("suffix", params.getKeySuffix()); // Assuming requireString trims keySuffix
        assertEquals(CacheOperation.SINGLE_WRITE, params.getOperation());
        assertEquals("testValue", params.getValue());
        assertEquals(Integer.valueOf(3600), params.getTtlSeconds());
    }


    @Test
    public void testParse_SingleWrite_DefaultTTL() {
        Map<String, Object> input = createBaseInputMap("SINGLE", "testKey", "WRITE");
        input.put("value", "testValue");
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory

        CacheParams params = CacheComponentParamParser.parse(input);

        assertEquals(CacheType.SINGLE, params.getType());
        assertEquals(CacheOperation.SINGLE_WRITE, params.getOperation());
        assertEquals(Integer.valueOf(DEFAULT_TTL_SECONDS), params.getTtlSeconds());
    }

    @Test
    public void testParse_SingleRead_Success() {
        Map<String, Object> input = createBaseInputMap("SINGLE", "testKey", "READ");
        input.put("keySuffix", "suffixRead");

        CacheParams params = CacheComponentParamParser.parse(input);

        assertEquals(CacheType.SINGLE, params.getType());
        assertEquals("testKey", params.getKey());
        assertEquals("suffixRead", params.getKeySuffix());
        assertEquals(CacheOperation.SINGLE_READ, params.getOperation());
        assertNull(params.getValue());
        assertNull(params.getTtlSeconds());
        assertNull(params.getMaxElements());
    }

    @Test(expected = ExecutionException.class)
    public void testParse_MissingKeySuffix_ThrowsException() { // MODIFIED: Renamed and expecting exception
        Map<String, Object> input = createBaseInputMap("SINGLE", "testKey", "READ");
        // keySuffix is NOT added to input map
        CacheComponentParamParser.parse(input); // Should throw for missing keySuffix
    }


    @Test
    public void testParse_ZsetOverwrite_Success() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "OVERWRITE");
        List<String> values = Arrays.asList("val1", "val2", "val3");
        input.put("value", JSON.toJSONString(values));
        input.put("ttlSeconds", 7200);
        input.put("maxElements", 500);
        input.put("keySuffix", "zsetSuffix");

        CacheParams params = CacheComponentParamParser.parse(input);

        assertEquals(CacheType.ZSET, params.getType());
        assertEquals("zsetKey", params.getKey());
        assertEquals("zsetSuffix", params.getKeySuffix());
        assertEquals(CacheOperation.ZSET_OVERWRITE, params.getOperation());
        assertNotNull(params.getValuesForZSetOverwrite());
        assertEquals(values, params.getValuesForZSetOverwrite());
        assertEquals(Integer.valueOf(7200), params.getTtlSeconds());
        assertEquals(Integer.valueOf(500), params.getMaxElements());
        assertNull(params.getValue()); // Original 'value' field should not be directly on params
    }

    @Test
    public void testParse_ZsetOverwrite_DefaultTTLAndMaxElements() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "OVERWRITE");
        List<String> values = Arrays.asList("v1", "v2");
        input.put("value", JSON.toJSONString(values));
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory

        CacheParams params = CacheComponentParamParser.parse(input);

        assertEquals(CacheType.ZSET, params.getType());
        assertEquals(CacheOperation.ZSET_OVERWRITE, params.getOperation());
        assertEquals(values, params.getValuesForZSetOverwrite());
        assertEquals(Integer.valueOf(DEFAULT_TTL_SECONDS), params.getTtlSeconds());
        assertEquals(Integer.valueOf(DEFAULT_MAX_ELEMENTS), params.getMaxElements());
    }

    @Test
    public void testParse_ZsetAdd_Success() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetAddKey", "ADD");
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        input.put("value", "newValue");
        input.put("ttlSeconds", 1800);
        input.put("maxElements", 200);

        CacheParams params = CacheComponentParamParser.parse(input);

        assertEquals(CacheType.ZSET, params.getType());
        assertEquals("zsetAddKey", params.getKey());
        assertEquals(CacheOperation.ZSET_ADD, params.getOperation());
        assertEquals("newValue", params.getValue());
        assertEquals(Integer.valueOf(1800), params.getTtlSeconds());
        assertEquals(Integer.valueOf(200), params.getMaxElements());
    }

    @Test
    public void testParse_ZsetAdd_DefaultTTLAndMaxElements() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetAddKey", "ADD");
        input.put("value", "newValueDefault");
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory

        CacheParams params = CacheComponentParamParser.parse(input);
        assertEquals(Integer.valueOf(DEFAULT_TTL_SECONDS), params.getTtlSeconds());
        assertEquals(Integer.valueOf(DEFAULT_MAX_ELEMENTS), params.getMaxElements());
    }


    @Test
    public void testParse_ZsetReadAll_Success() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKeyRead", "READ_ALL");
        input.put("keySuffix", "readAllSuffix");

        CacheParams params = CacheComponentParamParser.parse(input);

        assertEquals(CacheType.ZSET, params.getType());
        assertEquals("zsetKeyRead", params.getKey());
        assertEquals("readAllSuffix", params.getKeySuffix());
        assertEquals(CacheOperation.ZSET_READ_ALL, params.getOperation());
        assertNull(params.getValue());
        assertNull(params.getTtlSeconds());
        assertNull(params.getMaxElements());
    }

    @Test
    public void testParse_ZsetReadConditional_Success() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKeyCond", "READ_CONDITION");
        input.put("conditionTimestamp", 1678886400000L); // Example timestamp
        input.put("keySuffix", "condSuffix");

        CacheParams params = CacheComponentParamParser.parse(input);

        assertEquals(CacheType.ZSET, params.getType());
        assertEquals("zsetKeyCond", params.getKey());
        assertEquals("condSuffix", params.getKeySuffix());
        assertEquals(CacheOperation.ZSET_READ_CONDITION, params.getOperation());
        assertEquals(Long.valueOf(1678886400000L), params.getConditionTimestamp());
        assertNull(params.getValue());
        assertNull(params.getTtlSeconds());
        assertNull(params.getMaxElements());
    }

    @Test
    public void testParse_ZsetReadConditional_Success_WithStringTimestamp() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKeyCond", "READ_CONDITION");
        input.put("conditionTimestamp", "1678886400001");
        input.put("keySuffix", "condSuffix");

        CacheParams params = CacheComponentParamParser.parse(input);
        assertEquals(Long.valueOf(1678886400001L), params.getConditionTimestamp());
    }


    @Test
    public void testParse_ZsetCheckExist_Success() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKeyExist", "CHECK_EXIST");
        input.put("value", "elementToCheck");
        input.put("keySuffix", "existSuffix");

        CacheParams params = CacheComponentParamParser.parse(input);

        assertEquals(CacheType.ZSET, params.getType());
        assertEquals("zsetKeyExist", params.getKey());
        assertEquals("existSuffix", params.getKeySuffix());
        assertEquals(CacheOperation.ZSET_CHECK_EXIST, params.getOperation());
        assertEquals("elementToCheck", params.getValue());
        assertNull(params.getTtlSeconds());
        assertNull(params.getMaxElements());
    }

    @Test
    public void testParse_TtlAndMaxElementsAsString_Success() {
        Map<String, Object> inputSingle = createBaseInputMap("SINGLE", "testKey", "WRITE");
        inputSingle.put("value", "testValue");
        inputSingle.put("keySuffix", "dummySuffix1"); // ADDED: keySuffix is mandatory
        inputSingle.put("ttlSeconds", "3601");

        CacheParams paramsSingle = CacheComponentParamParser.parse(inputSingle);
        assertEquals(Integer.valueOf(3601), paramsSingle.getTtlSeconds());

        Map<String, Object> inputZset = createBaseInputMap("ZSET", "zsetKey", "ADD");
        inputZset.put("value", "zsetValue");
        inputZset.put("keySuffix", "dummySuffix2"); // ADDED: keySuffix is mandatory
        inputZset.put("maxElements", "501");
        CacheParams paramsZset = CacheComponentParamParser.parse(inputZset);
        assertEquals(Integer.valueOf(501), paramsZset.getMaxElements());
    }

    // Error Handling & Validation

    @Test(expected = ExecutionException.class)
    public void testParse_MissingType() {
        Map<String, Object> input = new HashMap<>();
        input.put("key", "testKey");
        input.put("keySuffix", "dummySuffix");
        input.put("operation", "READ");
        CacheComponentParamParser.parse(input);
    }

    @Test(expected = ExecutionException.class)
    public void testParse_MissingKey() {
        Map<String, Object> input = new HashMap<>();
        input.put("type", "SINGLE");
        input.put("keySuffix", "dummySuffix");
        input.put("operation", "READ");
        CacheComponentParamParser.parse(input);
    }

    @Test(expected = ExecutionException.class)
    public void testParse_MissingOperation() {
        Map<String, Object> input = new HashMap<>();
        input.put("type", "SINGLE");
        input.put("key", "testKey");
        input.put("keySuffix", "dummySuffix");
        CacheComponentParamParser.parse(input);
    }

    @Test(expected = ExecutionException.class)
    public void testParse_BlankKey() {
        Map<String, Object> input = createBaseInputMap("SINGLE", "   ", "READ");
        input.put("keySuffix", "dummySuffix"); // Need valid keySuffix to test blank key
        CacheComponentParamParser.parse(input);
    }

    @Test(expected = ExecutionException.class)
    public void testParse_BlankKeySuffix() {
        Map<String, Object> input = createBaseInputMap("SINGLE", "testKey", "READ");
        input.put("keySuffix", "  ");
        CacheComponentParamParser.parse(input);
    }


    @Test(expected = ExecutionException.class)
    public void testParse_SingleWrite_MissingValue() {
        Map<String, Object> input = createBaseInputMap("SINGLE", "testKey", "WRITE");
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        CacheComponentParamParser.parse(input);
    }

    @Test(expected = ExecutionException.class)
    public void testParse_ZsetAdd_MissingValue() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "ADD");
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        CacheComponentParamParser.parse(input);
    }

    @Test(expected = ExecutionException.class)
    public void testParse_ZsetCheckExist_MissingValue() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "CHECK_EXIST");
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        CacheComponentParamParser.parse(input);
    }

    @Test(expected = ExecutionException.class)
    public void testParse_ZsetOverwrite_MissingValue() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "OVERWRITE");
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        CacheComponentParamParser.parse(input);
    }

    @Test(expected = ExecutionException.class)
    public void testParse_ZsetReadConditional_MissingConditionTimestamp() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "READ_CONDITION");
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        CacheComponentParamParser.parse(input);
    }

    @Test
    public void testParse_InvalidTypeEnum() {
        Map<String, Object> input = createBaseInputMap("INVALID_TYPE", "testKey", "READ");
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        try {
            CacheComponentParamParser.parse(input);
            fail("Should have thrown ExecutionException");
        } catch (ExecutionException e) {
            assertEquals("Invalid type: INVALID_TYPE", e.getMessage()); // MODIFIED: Exact message check
        }
    }

    @Test
    public void testParse_InvalidOperationEnum() {
        Map<String, Object> input = createBaseInputMap("SINGLE", "testKey", "INVALID_OP");
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        try {
            CacheComponentParamParser.parse(input);
            fail("Should have thrown ExecutionException");
        } catch (ExecutionException e) {
            assertEquals("Invalid operation: INVALID_OP", e.getMessage()); // MODIFIED: Exact message check
        }
    }

    @Test
    public void testParse_IncompatibleTypeOperation_SingleWithZsetAdd() {
        Map<String, Object> input = createBaseInputMap("SINGLE", "testKey", "ADD");
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        input.put("value", "someValue"); // ZSET_ADD needs value
        try {
            CacheComponentParamParser.parse(input);
            fail("Should have thrown ExecutionException for incompatible type and operation");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("Operation 'ADD' not applicable for type 'SINGLE'"));
        }
    }

    @Test
    public void testParse_IncompatibleTypeOperation_ZsetWithSingleWrite() {
        Map<String, Object> input = createBaseInputMap("ZSET", "testKey", "WRITE");
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        input.put("value", "someValue"); // SINGLE_WRITE needs value
        try {
            CacheComponentParamParser.parse(input);
            fail("Should have thrown ExecutionException for incompatible type and operation");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("Operation 'WRITE' not applicable for type 'ZSET'"));
        }
    }


    @Test
    public void testParse_ZsetOverwrite_ValueNotJsonArray() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "OVERWRITE");
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        input.put("value", "this is not a json array");
        try {
            CacheComponentParamParser.parse(input);
            fail("Should have thrown ExecutionException");
        } catch (ExecutionException e) {
            // Based on generic catch in CacheComponentParamParser.parse:
            // new ExecutionException("Invalid params: " + e.getMessage(), e);
            // And JSON.parseArray throwing JSONException
            assertTrue(e.getMessage().startsWith("Invalid params: "));
            assertTrue(e.getCause() instanceof com.alibaba.fastjson.JSONException);
        }
    }

    @Test
    public void testParse_ZsetOverwrite_ValueNull() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "OVERWRITE");
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        input.put("value", null); // Explicitly null, requireString will handle this
        try {
            CacheComponentParamParser.parse(input);
            fail("Should have thrown ExecutionException for missing value");
        } catch (ExecutionException e) {
            assertEquals("Missing required field: value", e.getMessage()); // MODIFIED: Exact message check
        }
    }


    @Test
    public void testParse_ZsetOverwrite_ValueEmptyJsonArray() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "OVERWRITE");
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        input.put("value", JSON.toJSONString(Collections.emptyList()));
        try {
            CacheComponentParamParser.parse(input);
            fail("Should have thrown ExecutionException");
        } catch (ExecutionException e) {
            assertEquals("Values array cannot be empty", e.getMessage()); // MODIFIED: Exact message check
        }
    }

    @Test
    public void testParse_MalformedTtlSeconds() {
        Map<String, Object> input = createBaseInputMap("SINGLE", "testKey", "WRITE");
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        input.put("value", "testValue");
        input.put("ttlSeconds", "not-a-number");
        try {
            CacheComponentParamParser.parse(input);
            fail("Should have thrown ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().startsWith("Invalid params: ")); // MODIFIED: Check wrapper message
            assertTrue(e.getCause() instanceof NumberFormatException); // MODIFIED: Check cause
        }
    }

    @Test
    public void testParse_MalformedMaxElements() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "ADD");
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        input.put("value", "zsetValue");
        input.put("maxElements", "not-a-number");
        try {
            CacheComponentParamParser.parse(input);
            fail("Should have thrown ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().startsWith("Invalid params: ")); // MODIFIED: Check wrapper message
            assertTrue(e.getCause() instanceof NumberFormatException); // MODIFIED: Check cause
        }
    }

    @Test
    public void testParse_MalformedConditionTimestamp() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "READ_CONDITION");
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        input.put("conditionTimestamp", "not-a-long");
        try {
            CacheComponentParamParser.parse(input);
            fail("Should have thrown ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().startsWith("Invalid params: ")); // MODIFIED: Check wrapper message
            assertTrue(e.getCause() instanceof NumberFormatException); // MODIFIED: Check cause
        }
    }

    @Test
    public void testParse_ValueNotRequiredForSingleRead() {
        Map<String, Object> input = createBaseInputMap("SINGLE", "testKey", "READ");
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        // No "value" field
        CacheParams params = CacheComponentParamParser.parse(input);
        assertNull(params.getValue());
    }

    @Test
    public void testParse_ValueNotRequiredForZsetReadAll() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "READ_ALL");
        input.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        // No "value" field
        CacheParams params = CacheComponentParamParser.parse(input);
        assertNull(params.getValue());
        assertNull(params.getValuesForZSetOverwrite());
    }

    @Test
    public void testParse_ConditionTimestampNotRequiredForOtherOps() {
        Map<String, Object> inputZset = createBaseInputMap("ZSET", "zsetKey", "READ_ALL");
        inputZset.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        CacheParams paramsZset = CacheComponentParamParser.parse(inputZset);
        assertNull(paramsZset.getConditionTimestamp());

        Map<String, Object> inputSingle = createBaseInputMap("SINGLE", "testKeySingle", "READ");
        inputSingle.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        CacheParams paramsSingle = CacheComponentParamParser.parse(inputSingle);
        assertNull(paramsSingle.getConditionTimestamp());
    }

    @Test
    public void testParse_ValuesForZsetOverwriteNullForOtherOps() {
        Map<String, Object> inputZset = createBaseInputMap("ZSET", "zsetKey", "ADD");
        inputZset.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        inputZset.put("value", "item1");
        CacheParams paramsZset = CacheComponentParamParser.parse(inputZset);
        assertNull(paramsZset.getValuesForZSetOverwrite());

        Map<String, Object> inputSingle = createBaseInputMap("SINGLE", "testKeySingle", "WRITE");
        inputSingle.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        inputSingle.put("value", "item1");
        CacheParams paramsSingle = CacheComponentParamParser.parse(inputSingle);
        assertNull(paramsSingle.getValuesForZSetOverwrite());
    }

    @Test
    public void testParse_TtlAndMaxElementsNullForReadOps() {
        Map<String, Object> inputSingle = createBaseInputMap("SINGLE", "testKey", "READ");
        inputSingle.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        CacheParams paramsSingle = CacheComponentParamParser.parse(inputSingle);
        assertNull(paramsSingle.getTtlSeconds());
        assertNull(paramsSingle.getMaxElements());

        Map<String, Object> inputZsetReadAll = createBaseInputMap("ZSET", "zsetKey", "READ_ALL");
        inputZsetReadAll.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        CacheParams paramsZsetReadAll = CacheComponentParamParser.parse(inputZsetReadAll);
        assertNull(paramsZsetReadAll.getTtlSeconds());
        assertNull(paramsZsetReadAll.getMaxElements());

        Map<String, Object> inputZsetReadCond = createBaseInputMap("ZSET", "zsetKey", "READ_CONDITION");
        inputZsetReadCond.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        inputZsetReadCond.put("conditionTimestamp", 123L);
        CacheParams paramsZsetReadCond = CacheComponentParamParser.parse(inputZsetReadCond);
        assertNull(paramsZsetReadCond.getTtlSeconds());
        assertNull(paramsZsetReadCond.getMaxElements());

        Map<String, Object> inputZsetCheckExist = createBaseInputMap("ZSET", "zsetKey", "CHECK_EXIST");
        inputZsetCheckExist.put("keySuffix", "dummySuffix"); // ADDED: keySuffix is mandatory
        inputZsetCheckExist.put("value", "item");
        CacheParams paramsZsetCheckExist = CacheComponentParamParser.parse(inputZsetCheckExist);
        assertNull(paramsZsetCheckExist.getTtlSeconds());
        assertNull(paramsZsetCheckExist.getMaxElements());
    }
}
