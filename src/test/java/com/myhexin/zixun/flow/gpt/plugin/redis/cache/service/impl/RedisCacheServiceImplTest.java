package com.myhexin.zixun.flow.gpt.plugin.redis.cache.service.impl;

import com.myhexin.zixun.flow.gpt.engine.api.exception.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;

public class RedisCacheServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperationsMock;

    @Mock
    private ZSetOperations<String, String> zSetOperationsMock;

    @InjectMocks
    private RedisCacheServiceImpl redisCacheService;

    @Captor
    private ArgumentCaptor<String> stringCaptor;
    @Captor
    private ArgumentCaptor<Long> longCaptor;
    @Captor
    private ArgumentCaptor<TimeUnit> timeUnitCaptor;
    @Captor
    private ArgumentCaptor<Set<ZSetOperations.TypedTuple<String>>> typedTuplesCaptor;
    @Captor
    private ArgumentCaptor<Double> doubleCaptor;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperationsMock);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperationsMock);
    }

    // Tests for putValue
    @Test
    public void testPutValue_Success() {
        String key = "testKey";
        String value = "testValue";
        Integer ttlSeconds = 3600;

        redisCacheService.putValue(key, value, ttlSeconds);

        verify(valueOperationsMock).set(stringCaptor.capture(), stringCaptor.capture(), longCaptor.capture(), timeUnitCaptor.capture());
        assertEquals(key, stringCaptor.getAllValues().get(0));
        assertEquals(value, stringCaptor.getAllValues().get(1));
        assertEquals(Long.valueOf(ttlSeconds), longCaptor.getValue());
        assertEquals(TimeUnit.SECONDS, timeUnitCaptor.getValue());
    }

    @Test(expected = ExecutionException.class)
    public void testPutValue_RedisException() {
        String key = "testKey";
        String value = "testValue";
        Integer ttlSeconds = 3600;

        doThrow(new RuntimeException("Redis error")).when(valueOperationsMock).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        redisCacheService.putValue(key, value, ttlSeconds);
    }

    // Tests for getValue
    @Test
    public void testGetValue_KeyExists() {
        String key = "testKey";
        String expectedValue = "testValue";
        when(valueOperationsMock.get(key)).thenReturn(expectedValue);

        String actualValue = redisCacheService.getValue(key);

        assertEquals(expectedValue, actualValue);
        verify(valueOperationsMock).get(key);
    }

    @Test
    public void testGetValue_KeyNotExists() {
        String key = "testKeyNonExistent";
        when(valueOperationsMock.get(key)).thenReturn(null);

        String actualValue = redisCacheService.getValue(key);

        assertNull(actualValue);
        verify(valueOperationsMock).get(key);
    }

    @Test(expected = ExecutionException.class)
    public void testGetValue_RedisException() {
        String key = "testKey";
        when(valueOperationsMock.get(key)).thenThrow(new RuntimeException("Redis error"));
        redisCacheService.getValue(key);
    }

    // Tests for putList
    @Test(expected = ExecutionException.class)
    public void testPutList_ItemsExceedMaxSize() {
        String key = "listKey";
        List<String> items = Arrays.asList("item1", "item2", "item3");
        Integer ttlSeconds = 3600;
        Integer maxSize = 2; // items.size() > maxSize

        redisCacheService.putList(key, items, ttlSeconds, maxSize);
    }

    @Test
    public void testPutList_MaxSizeZeroOrNegative_BypassesSizeCheck() {
        String key = "listKey";
        List<String> items = Arrays.asList("item1", "item2", "item3");
        Integer ttlSeconds = 3600;
        Integer maxSize = 0; // Bypasses size check

        long timeBefore = System.currentTimeMillis();
        redisCacheService.putList(key, items, ttlSeconds, maxSize);
        long timeAfter = System.currentTimeMillis();

        verify(stringRedisTemplate).delete(key);
        verify(zSetOperationsMock).add(eq(key), typedTuplesCaptor.capture());
        verify(stringRedisTemplate).expire(key, ttlSeconds, TimeUnit.SECONDS);

        Set<ZSetOperations.TypedTuple<String>> capturedTuples = typedTuplesCaptor.getValue();
        assertEquals(items.size(), capturedTuples.size());
         for (ZSetOperations.TypedTuple<String> tuple : capturedTuples) {
            assertTrue(tuple.getScore() >= timeBefore && tuple.getScore() <= timeAfter);
        }
    }


    @Test
    public void testPutList_Success_NonEmptyList() {
        String key = "listKey";
        List<String> items = Arrays.asList("item1", "item2");
        Integer ttlSeconds = 3600;
        Integer maxSize = 5;
        long timeBefore = System.currentTimeMillis();

        redisCacheService.putList(key, items, ttlSeconds, maxSize);
        long timeAfter = System.currentTimeMillis();

        verify(stringRedisTemplate).delete(key);
        verify(zSetOperationsMock).add(eq(key), typedTuplesCaptor.capture());
        verify(stringRedisTemplate).expire(key, ttlSeconds, TimeUnit.SECONDS);

        Set<ZSetOperations.TypedTuple<String>> capturedTuples = typedTuplesCaptor.getValue();
        assertEquals(items.size(), capturedTuples.size());
        Set<String> capturedValues = capturedTuples.stream().map(ZSetOperations.TypedTuple::getValue).collect(Collectors.toSet());
        assertTrue(capturedValues.containsAll(items));
        for (ZSetOperations.TypedTuple<String> tuple : capturedTuples) {
            assertTrue(tuple.getScore() >= timeBefore && tuple.getScore() <= timeAfter);
        }
    }

    @Test
    public void testPutList_WithNullsInItemsList() {
        String key = "listKeyWithNulls";
        List<String> itemsWithNulls = Arrays.asList("item1", null, "item2", null);
        List<String> expectedNonNullItems = Arrays.asList("item1", "item2");
        Integer ttlSeconds = 3600;
        Integer maxSize = itemsWithNulls.size() + 5;

        long timeBefore = System.currentTimeMillis();
        redisCacheService.putList(key, itemsWithNulls, ttlSeconds, maxSize);
        long timeAfter = System.currentTimeMillis();

        verify(stringRedisTemplate).delete(key);
        verify(zSetOperationsMock).add(eq(key), typedTuplesCaptor.capture());
        verify(stringRedisTemplate).expire(key, ttlSeconds, TimeUnit.SECONDS);

        Set<ZSetOperations.TypedTuple<String>> capturedTuplesSet = typedTuplesCaptor.getValue();
        assertNotNull("Captured tuples set should not be null", capturedTuplesSet);
        assertEquals("Number of tuples should match non-null items", expectedNonNullItems.size(), capturedTuplesSet.size());

        Set<String> capturedValues = capturedTuplesSet.stream()
                                          .map(ZSetOperations.TypedTuple::getValue)
                                          .collect(Collectors.toSet());
        assertTrue("Captured values should contain all expected non-null items", capturedValues.containsAll(expectedNonNullItems));
        assertEquals("Captured values size should match expected non-null items size", expectedNonNullItems.size(), capturedValues.size());

        for (ZSetOperations.TypedTuple<String> tuple : capturedTuplesSet) {
            assertNotNull("Tuple value should not be null", tuple.getValue());
            assertTrue("Tuple value should be one of the expected non-null items", expectedNonNullItems.contains(tuple.getValue()));
            assertNotNull("Tuple score should not be null", tuple.getScore());
            double score = tuple.getScore();
            assertTrue("Score " + score + " should be >= " + timeBefore, score >= timeBefore);
            assertTrue("Score " + score + " should be <= " + timeAfter, score <= timeAfter);
        }
    }

    @Test
    public void testPutList_EmptyList() {
        String key = "emptyListKey";
        List<String> items = Collections.emptyList();
        Integer ttlSeconds = 3600;
        Integer maxSize = 5;

        redisCacheService.putList(key, items, ttlSeconds, maxSize);

        verify(stringRedisTemplate).delete(key);
        // add should not be called if tuples is empty (which it will be for an empty item list)
        verify(zSetOperationsMock, never()).add(eq(key), anySet());
        // expire should be called even if no items are added, as per current RedisCacheServiceImpl logic
        verify(stringRedisTemplate).expire(key, ttlSeconds, TimeUnit.SECONDS);
    }

    @Test
    public void testPutList_ListWithOnlyNulls() {
        String key = "listKeyOnlyNulls";
        List<String> items = Arrays.asList(null, null, null);
        Integer ttlSeconds = 3600;
        Integer maxSize = 10;

        redisCacheService.putList(key, items, ttlSeconds, maxSize);

        verify(stringRedisTemplate).delete(key);
        verify(zSetOperationsMock, never()).add(eq(key), anySet()); // No non-null items to add
        // expire should be called even if no items are added, as per current RedisCacheServiceImpl logic
        verify(stringRedisTemplate).expire(eq(key), eq(ttlSeconds), any(TimeUnit.class));
    }

    @Test(expected = ExecutionException.class)
    public void testPutList_RedisDeleteException() {
        String key = "listKey";
        List<String> items = Arrays.asList("item1");
        Integer ttlSeconds = 3600;
        Integer maxSize = 5;

        doThrow(new RuntimeException("Redis delete error")).when(stringRedisTemplate).delete(key);
        redisCacheService.putList(key, items, ttlSeconds, maxSize);
    }

    @Test(expected = ExecutionException.class)
    public void testPutList_RedisAddException() {
        String key = "listKey";
        List<String> items = Arrays.asList("item1");
        Integer ttlSeconds = 3600;
        Integer maxSize = 5;

        doThrow(new RuntimeException("Redis add error")).when(zSetOperationsMock).add(eq(key), anySet());
        redisCacheService.putList(key, items, ttlSeconds, maxSize);
    }

    @Test(expected = ExecutionException.class)
    public void testPutList_RedisExpireException() {
        String key = "listKey";
        List<String> items = Arrays.asList("item1");
        Integer ttlSeconds = 3600;
        Integer maxSize = 5;

        // Ensure add doesn't throw
        when(zSetOperationsMock.add(eq(key), anySet())).thenReturn(1L);


        doThrow(new RuntimeException("Redis expire error")).when(stringRedisTemplate).expire(key, ttlSeconds, TimeUnit.SECONDS);
        redisCacheService.putList(key, items, ttlSeconds, maxSize);
    }


    // Tests for appendToList
    @Test
    public void testAppendToList_NoTrimNeeded() {
        String key = "appendKey";
        String item = "newItem";
        Integer ttlSeconds = 3600;
        Integer maxSize = 5;

        when(zSetOperationsMock.add(eq(key), eq(item), anyDouble())).thenReturn(true);
        when(zSetOperationsMock.size(key)).thenReturn(3L); // current size < maxSize

        long timeBefore = System.currentTimeMillis();
        redisCacheService.appendToList(key, item, ttlSeconds, maxSize);
        long timeAfter = System.currentTimeMillis();

        verify(zSetOperationsMock).add(eq(key), eq(item), doubleCaptor.capture());
        assertTrue(doubleCaptor.getValue() >= timeBefore && doubleCaptor.getValue() <= timeAfter);
        verify(zSetOperationsMock).size(key);
        verify(zSetOperationsMock, never()).removeRange(anyString(), anyLong(), anyLong());
        verify(stringRedisTemplate).expire(key, ttlSeconds, TimeUnit.SECONDS);
    }

    @Test
    public void testAppendToList_TrimNeeded() {
        String key = "appendKeyTrim";
        String item = "newItem";
        Integer ttlSeconds = 3600;
        Integer maxSize = 2;
        long currentSize = 3L; // Mocked size after add, currentSize > maxSize

        when(zSetOperationsMock.add(eq(key), eq(item), anyDouble())).thenReturn(true);
        when(zSetOperationsMock.size(key)).thenReturn(currentSize);

        redisCacheService.appendToList(key, item, ttlSeconds, maxSize);

        verify(zSetOperationsMock).add(eq(key), eq(item), anyDouble());
        verify(zSetOperationsMock).size(key);
        // removeCount = currentSize - maxSize = 3 - 2 = 1. removeRange(key, 0, 0)
        verify(zSetOperationsMock).removeRange(key, 0, currentSize - maxSize -1);
        verify(stringRedisTemplate).expire(key, ttlSeconds, TimeUnit.SECONDS);
    }

    @Test
    public void testAppendToList_SizeReturnsNull_NoTrim() {
        String key = "appendKeySizeNull";
        String item = "newItem";
        Integer ttlSeconds = 3600;
        Integer maxSize = 2;

        when(zSetOperationsMock.add(eq(key), eq(item), anyDouble())).thenReturn(true);
        when(zSetOperationsMock.size(key)).thenReturn(null); // Simulate key not existing or other issue

        redisCacheService.appendToList(key, item, ttlSeconds, maxSize);

        verify(zSetOperationsMock).add(eq(key), eq(item), anyDouble());
        verify(zSetOperationsMock).size(key);
        verify(zSetOperationsMock, never()).removeRange(anyString(), anyLong(), anyLong()); // Trimming skipped
        verify(stringRedisTemplate).expire(key, ttlSeconds, TimeUnit.SECONDS);
    }


    @Test
    public void testAppendToList_MaxSizeZeroOrNegative_NoTrimLogic() {
        String key = "appendKeyNoTrim";
        String item = "newItem";
        Integer ttlSeconds = 3600;
        Integer maxSize = 0; // Should skip trimming logic

        when(zSetOperationsMock.add(eq(key), eq(item), anyDouble())).thenReturn(true);

        redisCacheService.appendToList(key, item, ttlSeconds, maxSize);

        verify(zSetOperationsMock).add(eq(key), eq(item), anyDouble());
        verify(zSetOperationsMock, never()).size(key); // Size check for trimming skipped
        verify(zSetOperationsMock, never()).removeRange(anyString(), anyLong(), anyLong());
        verify(stringRedisTemplate).expire(key, ttlSeconds, TimeUnit.SECONDS);
    }

    @Test(expected = ExecutionException.class)
    public void testAppendToList_RedisAddException() {
        String key = "appendKey";
        String item = "newItem";
        Integer ttlSeconds = 3600;
        Integer maxSize = 5;

        when(zSetOperationsMock.add(eq(key), eq(item), anyDouble())).thenThrow(new RuntimeException("Redis add error"));
        redisCacheService.appendToList(key, item, ttlSeconds, maxSize);
    }

    @Test(expected = ExecutionException.class)
    public void testAppendToList_RedisSizeException() {
        String key = "appendKey";
        String item = "newItem";
        Integer ttlSeconds = 3600;
        Integer maxSize = 5;

        when(zSetOperationsMock.add(eq(key), eq(item), anyDouble())).thenReturn(true);
        when(zSetOperationsMock.size(key)).thenThrow(new RuntimeException("Redis size error"));
        redisCacheService.appendToList(key, item, ttlSeconds, maxSize);
    }

    @Test(expected = ExecutionException.class)
    public void testAppendToList_RedisRemoveRangeException() {
        String key = "appendKeyTrim";
        String item = "newItem";
        Integer ttlSeconds = 3600;
        Integer maxSize = 1; // to trigger trim
        long currentSize = 2L;

        when(zSetOperationsMock.add(eq(key), eq(item), anyDouble())).thenReturn(true);
        when(zSetOperationsMock.size(key)).thenReturn(currentSize);
        when(zSetOperationsMock.removeRange(key, 0, currentSize - maxSize - 1)).thenThrow(new RuntimeException("Redis removeRange error"));

        redisCacheService.appendToList(key, item, ttlSeconds, maxSize);
    }

    @Test(expected = ExecutionException.class)
    public void testAppendToList_RedisExpireException() {
        String key = "appendKey";
        String item = "newItem";
        Integer ttlSeconds = 3600;
        Integer maxSize = 5;

        when(zSetOperationsMock.add(eq(key), eq(item), anyDouble())).thenReturn(true);
        when(zSetOperationsMock.size(key)).thenReturn(3L);
        doThrow(new RuntimeException("Redis expire error")).when(stringRedisTemplate).expire(key, ttlSeconds, TimeUnit.SECONDS);

        redisCacheService.appendToList(key, item, ttlSeconds, maxSize);
    }


    // Tests for getList
    @Test
    public void testGetList_Success() {
        String key = "getListKey";
        Set<String> expectedSet = new HashSet<>(Arrays.asList("item1", "item2"));
        when(zSetOperationsMock.range(key, 0, -1)).thenReturn(expectedSet);

        List<String> actualList = redisCacheService.getList(key);

        assertNotNull(actualList);
        assertEquals(expectedSet.size(), actualList.size());
        assertTrue(actualList.containsAll(expectedSet));
        verify(zSetOperationsMock).range(key, 0, -1);
    }

    @Test
    public void testGetList_EmptyOrNull() {
        String key = "getListEmptyKey";
        when(zSetOperationsMock.range(key, 0, -1)).thenReturn(null); // Or Collections.emptySet()
        List<String> actualList = redisCacheService.getList(key);
        assertTrue(actualList.isEmpty());

        when(zSetOperationsMock.range(key, 0, -1)).thenReturn(Collections.emptySet());
        actualList = redisCacheService.getList(key);
        assertTrue(actualList.isEmpty());
    }

    @Test(expected = ExecutionException.class)
    public void testGetList_RedisException() {
        String key = "getListKey";
        when(zSetOperationsMock.range(key, 0, -1)).thenThrow(new RuntimeException("Redis range error"));
        redisCacheService.getList(key);
    }

    // Tests for getListSince
    @Test
    public void testGetListSince_Success() {
        String key = "getListSinceKey";
        Long sinceTimestamp = 1000L;
        Set<String> expectedSet = new HashSet<>(Arrays.asList("itemA", "itemB"));
        when(zSetOperationsMock.rangeByScore(key, sinceTimestamp.doubleValue(), Double.MAX_VALUE)).thenReturn(expectedSet);

        List<String> actualList = redisCacheService.getListSince(key, sinceTimestamp);

        assertNotNull(actualList);
        assertEquals(expectedSet.size(), actualList.size());
        assertTrue(actualList.containsAll(expectedSet));
        verify(zSetOperationsMock).rangeByScore(key, sinceTimestamp.doubleValue(), Double.MAX_VALUE);
    }

    @Test
    public void testGetListSince_EmptyOrNull() {
        String key = "getListSinceEmptyKey";
        Long sinceTimestamp = 1000L;
        when(zSetOperationsMock.rangeByScore(key, sinceTimestamp.doubleValue(), Double.MAX_VALUE)).thenReturn(null);
        List<String> actualList = redisCacheService.getListSince(key, sinceTimestamp);
        assertTrue(actualList.isEmpty());

        when(zSetOperationsMock.rangeByScore(key, sinceTimestamp.doubleValue(), Double.MAX_VALUE)).thenReturn(Collections.emptySet());
        actualList = redisCacheService.getListSince(key, sinceTimestamp);
        assertTrue(actualList.isEmpty());
    }

    @Test(expected = ExecutionException.class)
    public void testGetListSince_RedisException() {
        String key = "getListSinceKey";
        Long sinceTimestamp = 1000L;
        when(zSetOperationsMock.rangeByScore(key, sinceTimestamp.doubleValue(), Double.MAX_VALUE)).thenThrow(new RuntimeException("Redis rangeByScore error"));
        redisCacheService.getListSince(key, sinceTimestamp);
    }

    // Tests for containsInList
    @Test
    public void testContainsInList_ItemExists() {
        String key = "containsListKey";
        String item = "existingItem";
        when(zSetOperationsMock.score(key, item)).thenReturn(12345.0); // Any non-null score

        assertTrue(redisCacheService.containsInList(key, item));
        verify(zSetOperationsMock).score(key, item);
    }

    @Test
    public void testContainsInList_ItemNotExists() {
        String key = "containsListKey";
        String item = "nonExistingItem";
        when(zSetOperationsMock.score(key, item)).thenReturn(null);

        assertFalse(redisCacheService.containsInList(key, item));
        verify(zSetOperationsMock).score(key, item);
    }

    @Test(expected = ExecutionException.class)
    public void testContainsInList_RedisException() {
        String key = "containsListKey";
        String item = "item";
        when(zSetOperationsMock.score(key, item)).thenThrow(new RuntimeException("Redis score error"));
        redisCacheService.containsInList(key, item);
    }

    // Tests for delete
    @Test
    public void testDelete_Success() {
        String key = "deleteKey";
        when(stringRedisTemplate.delete(key)).thenReturn(Boolean.TRUE);
        assertTrue(redisCacheService.delete(key));
        verify(stringRedisTemplate).delete(key);
    }

    @Test
    public void testDelete_KeyNotExists() {
        String key = "deleteKeyNotExist";
        when(stringRedisTemplate.delete(key)).thenReturn(Boolean.FALSE); // or null
        assertFalse(redisCacheService.delete(key));

        when(stringRedisTemplate.delete(key)).thenReturn(null); // for some Redis driver versions
        assertFalse(redisCacheService.delete(key));
    }

    @Test(expected = ExecutionException.class)
    public void testDelete_RedisException() {
        String key = "deleteKey";
        when(stringRedisTemplate.delete(key)).thenThrow(new RuntimeException("Redis delete error"));
        redisCacheService.delete(key);
    }

    // Tests for exists
    @Test
    public void testExists_KeyExists() {
        String key = "existsKey";
        when(stringRedisTemplate.hasKey(key)).thenReturn(Boolean.TRUE);
        assertTrue(redisCacheService.exists(key));
        verify(stringRedisTemplate).hasKey(key);
    }

    @Test
    public void testExists_KeyNotExists() {
        String key = "existsKeyNotExist";
        when(stringRedisTemplate.hasKey(key)).thenReturn(Boolean.FALSE); // or null
        assertFalse(redisCacheService.exists(key));

        when(stringRedisTemplate.hasKey(key)).thenReturn(null); // for some Redis driver versions
        assertFalse(redisCacheService.exists(key));
    }

    @Test(expected = ExecutionException.class)
    public void testExists_RedisException() {
        String key = "existsKey";
        when(stringRedisTemplate.hasKey(key)).thenThrow(new RuntimeException("Redis hasKey error"));
        redisCacheService.exists(key);
    }
}
