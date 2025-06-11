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

    @Test
    public void testParse_SingleRead_NoKeySuffix_Success() {
        Map<String, Object> input = createBaseInputMap("SINGLE", "testKey", "READ");

        CacheParams params = CacheComponentParamParser.parse(input);

        assertEquals(CacheType.SINGLE, params.getType());
        assertEquals("testKey", params.getKey());
        assertNull(params.getKeySuffix());
        assertEquals(CacheOperation.SINGLE_READ, params.getOperation());
        assertNull(params.getValue());
        assertNull(params.getTtlSeconds());
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
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKeyCond", "READ_CONDITIONAL");
        input.put("conditionTimestamp", 1678886400000L); // Example timestamp
        input.put("keySuffix", "condSuffix");

        CacheParams params = CacheComponentParamParser.parse(input);

        assertEquals(CacheType.ZSET, params.getType());
        assertEquals("zsetKeyCond", params.getKey());
        assertEquals("condSuffix", params.getKeySuffix());
        assertEquals(CacheOperation.ZSET_READ_CONDITIONAL, params.getOperation());
        assertEquals(Long.valueOf(1678886400000L), params.getConditionTimestamp());
        assertNull(params.getValue());
        assertNull(params.getTtlSeconds());
        assertNull(params.getMaxElements());
    }

    @Test
    public void testParse_ZsetReadConditional_Success_WithStringTimestamp() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKeyCond", "READ_CONDITIONAL");
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
        Map<String, Object> input = createBaseInputMap("SINGLE", "testKey", "WRITE");
        input.put("value", "testValue");
        input.put("ttlSeconds", "3601");

        CacheParams params = CacheComponentParamParser.parse(input);
        assertEquals(Integer.valueOf(3601), params.getTtlSeconds());

        input = createBaseInputMap("ZSET", "zsetKey", "ADD");
        input.put("value", "zsetValue");
        input.put("maxElements", "501");
        params = CacheComponentParamParser.parse(input);
        assertEquals(Integer.valueOf(501), params.getMaxElements());
    }

    // Error Handling & Validation

    @Test(expected = ExecutionException.class)
    public void testParse_MissingType() {
        Map<String, Object> input = new HashMap<>();
        input.put("key", "testKey");
        input.put("operation", "READ");
        CacheComponentParamParser.parse(input);
    }

    @Test(expected = ExecutionException.class)
    public void testParse_MissingKey() {
        Map<String, Object> input = new HashMap<>();
        input.put("type", "SINGLE");
        input.put("operation", "READ");
        CacheComponentParamParser.parse(input);
    }

    @Test(expected = ExecutionException.class)
    public void testParse_MissingOperation() {
        Map<String, Object> input = new HashMap<>();
        input.put("type", "SINGLE");
        input.put("key", "testKey");
        CacheComponentParamParser.parse(input);
    }

    @Test(expected = ExecutionException.class)
    public void testParse_BlankKey() {
        Map<String, Object> input = createBaseInputMap("SINGLE", "   ", "READ");
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
        CacheComponentParamParser.parse(input);
    }

    @Test(expected = ExecutionException.class)
    public void testParse_ZsetAdd_MissingValue() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "ADD");
        CacheComponentParamParser.parse(input);
    }

    @Test(expected = ExecutionException.class)
    public void testParse_ZsetCheckExist_MissingValue() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "CHECK_EXIST");
        CacheComponentParamParser.parse(input);
    }

    @Test(expected = ExecutionException.class)
    public void testParse_ZsetOverwrite_MissingValue() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "OVERWRITE");
        CacheComponentParamParser.parse(input);
    }

    @Test(expected = ExecutionException.class)
    public void testParse_ZsetReadConditional_MissingConditionTimestamp() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "READ_CONDITIONAL");
        CacheComponentParamParser.parse(input);
    }

    @Test
    public void testParse_InvalidTypeEnum() {
        Map<String, Object> input = createBaseInputMap("INVALID_TYPE", "testKey", "READ");
        try {
            CacheComponentParamParser.parse(input);
            fail("Should have thrown ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("Invalid cache type"));
        }
    }

    @Test
    public void testParse_InvalidOperationEnum() {
        Map<String, Object> input = createBaseInputMap("SINGLE", "testKey", "INVALID_OP");
        try {
            CacheComponentParamParser.parse(input);
            fail("Should have thrown ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("Invalid cache operation"));
        }
    }

    @Test
    public void testParse_IncompatibleTypeOperation_SingleWithZsetAdd() {
        Map<String, Object> input = createBaseInputMap("SINGLE", "testKey", "ADD");
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
        input.put("value", "this is not a json array");
        try {
            CacheComponentParamParser.parse(input);
            fail("Should have thrown ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().toLowerCase().contains("failed to parse value for zset_overwrite") ||
                       e.getMessage().toLowerCase().contains("com.alibaba.fastjson.jsonexception"));
        }
    }

    @Test
    public void testParse_ZsetOverwrite_ValueNull() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "OVERWRITE");
        input.put("value", null); // Explicitly null
        try {
            CacheComponentParamParser.parse(input);
            fail("Should have thrown ExecutionException for missing value");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("Missing required field 'value'"));
        }
    }


    @Test
    public void testParse_ZsetOverwrite_ValueEmptyJsonArray() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "OVERWRITE");
        input.put("value", JSON.toJSONString(Collections.emptyList()));
        try {
            CacheComponentParamParser.parse(input);
            fail("Should have thrown ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("Value for ZSET_OVERWRITE cannot be an empty list"));
        }
    }

    @Test
    public void testParse_MalformedTtlSeconds() {
        Map<String, Object> input = createBaseInputMap("SINGLE", "testKey", "WRITE");
        input.put("value", "testValue");
        input.put("ttlSeconds", "not-a-number");
        try {
            CacheComponentParamParser.parse(input);
            fail("Should have thrown ExecutionException");
        } catch (ExecutionException e) {
             assertTrue(e.getMessage().toLowerCase().contains("invalid integer value for ttlseconds") ||
                       e.getMessage().toLowerCase().contains("numberformatexception"));
        }
    }

    @Test
    public void testParse_MalformedMaxElements() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "ADD");
        input.put("value", "zsetValue");
        input.put("maxElements", "not-a-number");
        try {
            CacheComponentParamParser.parse(input);
            fail("Should have thrown ExecutionException");
        } catch (ExecutionException e) {
             assertTrue(e.getMessage().toLowerCase().contains("invalid integer value for maxelements") ||
                       e.getMessage().toLowerCase().contains("numberformatexception"));
        }
    }

    @Test
    public void testParse_MalformedConditionTimestamp() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "READ_CONDITIONAL");
        input.put("conditionTimestamp", "not-a-long");
        try {
            CacheComponentParamParser.parse(input);
            fail("Should have thrown ExecutionException");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().toLowerCase().contains("invalid long value for conditiontimestamp") ||
                       e.getMessage().toLowerCase().contains("numberformatexception"));
        }
    }

    @Test
    public void testParse_ValueNotRequiredForSingleRead() {
        Map<String, Object> input = createBaseInputMap("SINGLE", "testKey", "READ");
        // No "value" field
        CacheParams params = CacheComponentParamParser.parse(input);
        assertNull(params.getValue());
    }

    @Test
    public void testParse_ValueNotRequiredForZsetReadAll() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "READ_ALL");
        // No "value" field
        CacheParams params = CacheComponentParamParser.parse(input);
        assertNull(params.getValue());
        assertNull(params.getValuesForZSetOverwrite());
    }

    @Test
    public void testParse_ConditionTimestampNotRequiredForOtherOps() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "READ_ALL");
        CacheParams params = CacheComponentParamParser.parse(input);
        assertNull(params.getConditionTimestamp());

        input = createBaseInputMap("SINGLE", "testKeySingle", "READ");
        params = CacheComponentParamParser.parse(input);
        assertNull(params.getConditionTimestamp());
    }

    @Test
    public void testParse_ValuesForZsetOverwriteNullForOtherOps() {
        Map<String, Object> input = createBaseInputMap("ZSET", "zsetKey", "ADD");
        input.put("value", "item1");
        CacheParams params = CacheComponentParamParser.parse(input);
        assertNull(params.getValuesForZSetOverwrite());

        input = createBaseInputMap("SINGLE", "testKeySingle", "WRITE");
        input.put("value", "item1");
        params = CacheComponentParamParser.parse(input);
        assertNull(params.getValuesForZSetOverwrite());
    }

    @Test
    public void testParse_TtlAndMaxElementsNullForReadOps() {
        Map<String, Object> input = createBaseInputMap("SINGLE", "testKey", "READ");
        CacheParams params = CacheComponentParamParser.parse(input);
        assertNull(params.getTtlSeconds());
        assertNull(params.getMaxElements());

        input = createBaseInputMap("ZSET", "zsetKey", "READ_ALL");
        params = CacheComponentParamParser.parse(input);
        assertNull(params.getTtlSeconds());
        assertNull(params.getMaxElements());

        input = createBaseInputMap("ZSET", "zsetKey", "READ_CONDITIONAL");
        input.put("conditionTimestamp", 123L);
        params = CacheComponentParamParser.parse(input);
        assertNull(params.getTtlSeconds());
        assertNull(params.getMaxElements());

        input = createBaseInputMap("ZSET", "zsetKey", "CHECK_EXIST");
        input.put("value", "item");
        params = CacheComponentParamParser.parse(input);
        assertNull(params.getTtlSeconds());
        assertNull(params.getMaxElements());
    }
}
