package com.myhexin.zixun.flow.gpt.plugin.redis.cache.service.impl;

import com.myhexin.zixun.flow.gpt.plugin.exception.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisCacheServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private RedisCacheServiceImpl redisCacheService;

    @BeforeEach
    void setUp() {
        // Configure stringRedisTemplate to return mocked operations when opsForValue() or opsForZSet() is called
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    // 1. putValue Method
    @Test
    void putValue_shouldSetValueWithTtl() {
        String key = "testKey";
        String value = "testValue";
        long ttlSeconds = 3600L;

        redisCacheService.putValue(key, value, ttlSeconds);

        verify(valueOperations).set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }

    @Test
    void putValue_whenRedisThrowsException_shouldThrowExecutionException() {
        String key = "testKeyException";
        String value = "testValue";
        long ttlSeconds = 3600L;
        doThrow(new RuntimeException("Redis error")).when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            redisCacheService.putValue(key, value, ttlSeconds);
        });

        assertTrue(exception.getMessage().contains("Error putting value to Redis"));
        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof RuntimeException);
    }

    // 2. getValue Method
    @Test
    void getValue_shouldReturnValueFromRedis() {
        String key = "testKeyGet";
        String expectedValue = "expectedValue";
        when(valueOperations.get(key)).thenReturn(expectedValue);

        String actualValue = redisCacheService.getValue(key);

        assertEquals(expectedValue, actualValue);
        verify(valueOperations).get(key);
    }

    @Test
    void getValue_whenRedisReturnsNull_shouldReturnNull() {
        String key = "testKeyGetNull";
        when(valueOperations.get(key)).thenReturn(null);

        String actualValue = redisCacheService.getValue(key);

        assertNull(actualValue);
        verify(valueOperations).get(key);
    }


    @Test
    void getValue_whenRedisThrowsException_shouldThrowExecutionException() {
        String key = "testKeyGetException";
        when(valueOperations.get(key)).thenThrow(new RuntimeException("Redis error"));

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            redisCacheService.getValue(key);
        });

        assertTrue(exception.getMessage().contains("Error getting value from Redis"));
        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof RuntimeException);
    }

    // 3. putList Method
    @Test
    void putList_itemsExceedMaxSize_shouldThrowExecutionException() {
        String key = "testKeyPutListExceed";
        List<Object> items = Arrays.asList("item1", "item2", "item3");
        long ttlSeconds = 3600L;
        int maxSize = 2; // items.size() > maxSize

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            redisCacheService.putList(key, items, ttlSeconds, maxSize);
        });

        assertEquals("Items size 3 exceeds max size 2", exception.getMessage());
        verifyNoInteractions(stringRedisTemplate); // Ensure no Redis interaction
    }

    @Test
    void putList_maxSizeZeroOrLess_shouldNotThrowForSize() {
        String key = "testKeyPutListMaxSizeZero";
        List<Object> items = Arrays.asList("item1", "item2", "item3");
        long ttlSeconds = 3600L;
        int maxSize = 0; // maxSize <= 0

        // This will proceed to Redis interaction, so we need to mock that part
        // For this specific test, we only care that it doesn't throw the size exception.
        // The actual Redis interaction will be tested in other putList tests.
        // We can mock execute to avoid NPE or further processing for this narrow test.
        when(stringRedisTemplate.execute(any(SessionCallback.class))).thenReturn(null);


        assertDoesNotThrow(() -> {
            redisCacheService.putList(key, items, ttlSeconds, maxSize);
        });
        verify(stringRedisTemplate).execute(any(SessionCallback.class)); // Verify it proceeded to execute
    }


    @SuppressWarnings("unchecked")
    @Test
    void putList_successfulExecution_emptyList() {
        String key = "testKeyPutListEmpty";
        List<Object> items = Collections.emptyList();
        long ttlSeconds = 3600L;
        int maxSize = 10;

        ArgumentCaptor<SessionCallback<List<Object>>> sessionCallbackCaptor = ArgumentCaptor.forClass(SessionCallback.class);
        // Mock RedisOperations that will be passed to the callback
        RedisOperations<String, String> mockRedisOps = mock(RedisOperations.class);
        ZSetOperations<String, String> mockZSetOpsInCallback = mock(ZSetOperations.class);
        when(mockRedisOps.opsForZSet()).thenReturn(mockZSetOpsInCallback);


        // Capture the callback
        when(stringRedisTemplate.execute(sessionCallbackCaptor.capture())).thenReturn(Collections.singletonList(true)); // Mock successful exec

        redisCacheService.putList(key, items, ttlSeconds, maxSize);

        // Execute the captured callback
        SessionCallback<List<Object>> actualCallback = sessionCallbackCaptor.getValue();
        assertNotNull(actualCallback);
        actualCallback.execute(mockRedisOps); // Pass the mockRedisOps

        // Verify interactions within the callback
        InOrder inOrder = Mockito.inOrder(mockRedisOps, mockZSetOpsInCallback);
        inOrder.verify(mockRedisOps).multi();
        inOrder.verify(mockRedisOps).delete(key);
        // For an empty list, opsForZSet().add should not be called with items,
        // but the method itself might be called to get the ZSetOperations instance.
        // The original code would create an empty set of tuples, so add would not be called with elements.
        // verify(mockZSetOpsInCallback, never()).add(eq(key), anySet()); // More precise: no add with elements
        inOrder.verify(mockRedisOps).expire(key, ttlSeconds, TimeUnit.SECONDS);
        inOrder.verify(mockRedisOps).exec();
    }


    @SuppressWarnings("unchecked")
    @Test
    void putList_successfulExecution_withItems() {
        String key = "testKeyPutListWithItems";
        List<Object> items = Arrays.asList("item1", "item2");
        long ttlSeconds = 1800L;
        int maxSize = 5;

        ArgumentCaptor<SessionCallback<List<Object>>> sessionCallbackCaptor = ArgumentCaptor.forClass(SessionCallback.class);
        ArgumentCaptor<Set<ZSetOperations.TypedTuple<String>>> typedTuplesCaptor = ArgumentCaptor.forClass(Set.class);

        RedisOperations<String, String> mockRedisOps = mock(RedisOperations.class);
        ZSetOperations<String, String> mockZSetOpsInCallback = mock(ZSetOperations.class);
        when(mockRedisOps.opsForZSet()).thenReturn(mockZSetOpsInCallback);


        when(stringRedisTemplate.execute(sessionCallbackCaptor.capture())).thenReturn(Collections.singletonList(true));

        // Call the actual method
        redisCacheService.putList(key, items, ttlSeconds, maxSize);

        // Execute the captured callback
        SessionCallback<List<Object>> actualCallback = sessionCallbackCaptor.getValue();
        assertNotNull(actualCallback);
        long beforeExecuteTimestamp = System.currentTimeMillis();
        actualCallback.execute(mockRedisOps);
        long afterExecuteTimestamp = System.currentTimeMillis();


        // Verify interactions
        InOrder inOrder = Mockito.inOrder(mockRedisOps, mockZSetOpsInCallback);
        inOrder.verify(mockRedisOps).multi();
        inOrder.verify(mockRedisOps).delete(key);
        inOrder.verify(mockZSetOpsInCallback).add(eq(key), typedTuplesCaptor.capture());
        inOrder.verify(mockRedisOps).expire(key, ttlSeconds, TimeUnit.SECONDS);
        inOrder.verify(mockRedisOps).exec();

        // Verify tuple contents
        Set<ZSetOperations.TypedTuple<String>> capturedTuples = typedTuplesCaptor.getValue();
        assertEquals(items.size(), capturedTuples.size());
        List<String> capturedValues = capturedTuples.stream().map(ZSetOperations.TypedTuple::getValue).collect(Collectors.toList());
        assertTrue(capturedValues.containsAll(items.stream().map(String::valueOf).collect(Collectors.toList())));

        // Verify scores (timestamps) - they should be very close to now, and all identical for putList
        double expectedScore = -1;
        for (ZSetOperations.TypedTuple<String> tuple : capturedTuples) {
            if (expectedScore == -1) {
                expectedScore = tuple.getScore();
                assertTrue(expectedScore >= beforeExecuteTimestamp && expectedScore <= afterExecuteTimestamp,
                        "Score " + expectedScore + " out of range [" + beforeExecuteTimestamp + ", " + afterExecuteTimestamp + "]");
            } else {
                assertEquals(expectedScore, tuple.getScore(), "All items in putList should have the same score.");
            }
        }
    }

    @Test
    void putList_whenRedisExecuteThrowsException_shouldThrowExecutionException() {
        String key = "testKeyPutListExecuteException";
        List<Object> items = Arrays.asList("item1");
        long ttlSeconds = 3600L;
        int maxSize = 2;

        when(stringRedisTemplate.execute(any(SessionCallback.class))).thenThrow(new RuntimeException("Redis execute error"));

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            redisCacheService.putList(key, items, ttlSeconds, maxSize);
        });

        assertTrue(exception.getMessage().contains("Error putting list to Redis"));
        assertNotNull(exception.getCause());
    }

    // 4. appendToList Method
    @SuppressWarnings("unchecked")
    @Test
    void appendToList_successfulExecution_withMaxSize() {
        String key = "testKeyAppendListWithMax";
        String item = "newItem";
        double score = 123.45;
        long ttlSeconds = 3600L;
        int maxSize = 5; // maxSize > 0

        ArgumentCaptor<SessionCallback<List<Object>>> sessionCallbackCaptor = ArgumentCaptor.forClass(SessionCallback.class);
        RedisOperations<String, String> mockRedisOps = mock(RedisOperations.class);
        ZSetOperations<String, String> mockZSetOpsInCallback = mock(ZSetOperations.class);
        when(mockRedisOps.opsForZSet()).thenReturn(mockZSetOpsInCallback);

        when(stringRedisTemplate.execute(sessionCallbackCaptor.capture())).thenReturn(Collections.singletonList(true));

        redisCacheService.appendToList(key, item, score, ttlSeconds, maxSize);

        SessionCallback<List<Object>> actualCallback = sessionCallbackCaptor.getValue();
        assertNotNull(actualCallback);
        actualCallback.execute(mockRedisOps);

        InOrder inOrder = Mockito.inOrder(mockRedisOps, mockZSetOpsInCallback);
        inOrder.verify(mockRedisOps).multi();
        inOrder.verify(mockZSetOpsInCallback).add(key, item, score);
        inOrder.verify(mockZSetOpsInCallback).removeRange(key, 0, -(maxSize + 1)); // Trim if maxSize is positive
        inOrder.verify(mockRedisOps).expire(key, ttlSeconds, TimeUnit.SECONDS);
        inOrder.verify(mockRedisOps).exec();
    }

    @SuppressWarnings("unchecked")
    @Test
    void appendToList_successfulExecution_maxSizeZeroOrLess() {
        String key = "testKeyAppendListNoMax";
        String item = "anotherNewItem";
        double score = 67.89;
        long ttlSeconds = 1800L;
        int maxSize = 0; // maxSize <= 0

        ArgumentCaptor<SessionCallback<List<Object>>> sessionCallbackCaptor = ArgumentCaptor.forClass(SessionCallback.class);
        RedisOperations<String, String> mockRedisOps = mock(RedisOperations.class);
        ZSetOperations<String, String> mockZSetOpsInCallback = mock(ZSetOperations.class);
        when(mockRedisOps.opsForZSet()).thenReturn(mockZSetOpsInCallback);


        when(stringRedisTemplate.execute(sessionCallbackCaptor.capture())).thenReturn(Collections.singletonList(true));

        redisCacheService.appendToList(key, item, score, ttlSeconds, maxSize);

        SessionCallback<List<Object>> actualCallback = sessionCallbackCaptor.getValue();
        assertNotNull(actualCallback);
        actualCallback.execute(mockRedisOps);

        InOrder inOrder = Mockito.inOrder(mockRedisOps, mockZSetOpsInCallback);
        inOrder.verify(mockRedisOps).multi();
        inOrder.verify(mockZSetOpsInCallback).add(key, item, score);
        inOrder.verify(mockZSetOpsInCallback, never()).removeRange(anyString(), anyLong(), anyLong()); // No trimming
        inOrder.verify(mockRedisOps).expire(key, ttlSeconds, TimeUnit.SECONDS);
        inOrder.verify(mockRedisOps).exec();
    }

    @Test
    void appendToList_whenRedisExecuteThrowsException_shouldThrowExecutionException() {
        String key = "testKeyAppendListExecuteException";
        String item = "exceptionItem";
        double score = 10.0;
        long ttlSeconds = 3600L;
        int maxSize = 5;

        when(stringRedisTemplate.execute(any(SessionCallback.class))).thenThrow(new RuntimeException("Redis execute error for append"));

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            redisCacheService.appendToList(key, item, score, ttlSeconds, maxSize);
        });

        assertTrue(exception.getMessage().contains("Error appending to list in Redis"));
        assertNotNull(exception.getCause());
    }

    // 5. getList Method
    @Test
    void getList_shouldReturnListFromRedis() {
        String key = "testKeyGetList";
        Set<String> mockSet = new LinkedHashSet<>(Arrays.asList("item3", "item2", "item1")); // Reverse order for reverseRange
        when(zSetOperations.reverseRange(key, 0, -1)).thenReturn(mockSet);

        List<String> actualList = redisCacheService.getList(key);

        assertEquals(Arrays.asList("item3", "item2", "item1"), actualList);
        verify(zSetOperations).reverseRange(key, 0, -1);
    }

    @Test
    void getList_whenRedisReturnsNull_shouldReturnEmptyList() {
        String key = "testKeyGetListNull";
        when(zSetOperations.reverseRange(key, 0, -1)).thenReturn(null);

        List<String> actualList = redisCacheService.getList(key);

        assertNotNull(actualList);
        assertTrue(actualList.isEmpty());
        verify(zSetOperations).reverseRange(key, 0, -1);
    }

    @Test
    void getList_whenRedisReturnsEmptySet_shouldReturnEmptyList() {
        String key = "testKeyGetListEmptySet";
        when(zSetOperations.reverseRange(key, 0, -1)).thenReturn(Collections.emptySet());

        List<String> actualList = redisCacheService.getList(key);

        assertNotNull(actualList);
        assertTrue(actualList.isEmpty());
        verify(zSetOperations).reverseRange(key, 0, -1);
    }

    @Test
    void getList_whenRedisThrowsException_shouldThrowExecutionException() {
        String key = "testKeyGetListException";
        when(zSetOperations.reverseRange(key, 0, -1)).thenThrow(new RuntimeException("Redis error"));

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            redisCacheService.getList(key);
        });

        assertTrue(exception.getMessage().contains("Error getting list from Redis"));
        assertNotNull(exception.getCause());
    }

    // 6. getListSince Method
    @Test
    void getListSince_shouldReturnListFromRedis() {
        String key = "testKeyGetListSince";
        long sinceTimestamp = 1678880000000L;
        Set<String> mockSet = new LinkedHashSet<>(Arrays.asList("itemB", "itemA")); // Order by score desc
        when(zSetOperations.reverseRangeByScore(key, sinceTimestamp, Double.MAX_VALUE)).thenReturn(mockSet);

        List<String> actualList = redisCacheService.getListSince(key, sinceTimestamp);

        assertEquals(Arrays.asList("itemB", "itemA"), actualList);
        verify(zSetOperations).reverseRangeByScore(key, sinceTimestamp, Double.MAX_VALUE);
    }

    @Test
    void getListSince_whenRedisReturnsNull_shouldReturnEmptyList() {
        String key = "testKeyGetListSinceNull";
        long sinceTimestamp = 1678880000000L;
        when(zSetOperations.reverseRangeByScore(key, sinceTimestamp, Double.MAX_VALUE)).thenReturn(null);

        List<String> actualList = redisCacheService.getListSince(key, sinceTimestamp);

        assertNotNull(actualList);
        assertTrue(actualList.isEmpty());
        verify(zSetOperations).reverseRangeByScore(key, sinceTimestamp, Double.MAX_VALUE);
    }

    @Test
    void getListSince_whenRedisReturnsEmptySet_shouldReturnEmptyList() {
        String key = "testKeyGetListSinceEmptySet";
        long sinceTimestamp = 1678880000000L;
        when(zSetOperations.reverseRangeByScore(key, sinceTimestamp, Double.MAX_VALUE)).thenReturn(Collections.emptySet());

        List<String> actualList = redisCacheService.getListSince(key, sinceTimestamp);

        assertNotNull(actualList);
        assertTrue(actualList.isEmpty());
        verify(zSetOperations).reverseRangeByScore(key, sinceTimestamp, Double.MAX_VALUE);
    }

    @Test
    void getListSince_whenRedisThrowsException_shouldThrowExecutionException() {
        String key = "testKeyGetListSinceException";
        long sinceTimestamp = 1678880000000L;
        when(zSetOperations.reverseRangeByScore(key, sinceTimestamp, Double.MAX_VALUE))
                .thenThrow(new RuntimeException("Redis error"));

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            redisCacheService.getListSince(key, sinceTimestamp);
        });

        assertTrue(exception.getMessage().contains("Error getting list since timestamp from Redis"));
        assertNotNull(exception.getCause());
    }

    // 7. containsInList Method
    @Test
    void containsInList_whenItemExists_shouldReturnTrue() {
        String key = "testKeyContains";
        String item = "existingItem";
        when(zSetOperations.score(key, item)).thenReturn(123.0); // Any non-null Double indicates existence

        boolean result = redisCacheService.containsInList(key, item);

        assertTrue(result);
        verify(zSetOperations).score(key, item);
    }

    @Test
    void containsInList_whenItemDoesNotExist_shouldReturnFalse() {
        String key = "testKeyContainsNotExist";
        String item = "nonExistingItem";
        when(zSetOperations.score(key, item)).thenReturn(null); // Null score indicates non-existence

        boolean result = redisCacheService.containsInList(key, item);

        assertFalse(result);
        verify(zSetOperations).score(key, item);
    }

    @Test
    void containsInList_whenRedisThrowsException_shouldThrowExecutionException() {
        String key = "testKeyContainsException";
        String item = "itemToCheck";
        when(zSetOperations.score(key, item)).thenThrow(new RuntimeException("Redis error"));

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            redisCacheService.containsInList(key, item);
        });

        assertTrue(exception.getMessage().contains("Error checking if item exists in list in Redis"));
        assertNotNull(exception.getCause());
    }

    // 8. delete Method
    @Test
    void delete_whenKeyExistsAndDeleted_shouldReturnTrue() {
        String key = "testKeyDelete";
        when(stringRedisTemplate.delete(key)).thenReturn(Boolean.TRUE);

        boolean result = redisCacheService.delete(key);

        assertTrue(result);
        verify(stringRedisTemplate).delete(key);
    }

    @Test
    void delete_whenKeyDoesNotExistOrNotDeleted_shouldReturnFalse() {
        String key = "testKeyDeleteNotExist";
        when(stringRedisTemplate.delete(key)).thenReturn(Boolean.FALSE);

        boolean result = redisCacheService.delete(key);

        assertFalse(result);
        verify(stringRedisTemplate).delete(key);
    }

    @Test
    void delete_whenRedisReturnsNull_shouldReturnFalse() {
        // Some Redis client versions or configurations might return null for delete
        // The method signature for stringRedisTemplate.delete(K key) returns Boolean
        // so Mockito default is null.
        String key = "testKeyDeleteReturnsNull";
        when(stringRedisTemplate.delete(key)).thenReturn(null);

        boolean result = redisCacheService.delete(key);

        assertFalse(result);
        verify(stringRedisTemplate).delete(key);
    }


    @Test
    void delete_whenRedisThrowsException_shouldThrowExecutionException() {
        String key = "testKeyDeleteException";
        when(stringRedisTemplate.delete(key)).thenThrow(new RuntimeException("Redis error"));

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            redisCacheService.delete(key);
        });

        assertTrue(exception.getMessage().contains("Error deleting key from Redis"));
        assertNotNull(exception.getCause());
    }

    // 9. exists Method
    @Test
    void exists_whenKeyExists_shouldReturnTrue() {
        String key = "testKeyExists";
        when(stringRedisTemplate.hasKey(key)).thenReturn(Boolean.TRUE);

        boolean result = redisCacheService.exists(key);

        assertTrue(result);
        verify(stringRedisTemplate).hasKey(key);
    }

    @Test
    void exists_whenKeyDoesNotExist_shouldReturnFalse() {
        String key = "testKeyExistsNotExist";
        when(stringRedisTemplate.hasKey(key)).thenReturn(Boolean.FALSE);

        boolean result = redisCacheService.exists(key);

        assertFalse(result);
        verify(stringRedisTemplate).hasKey(key);
    }

    @Test
    void exists_whenRedisReturnsNull_shouldReturnFalse() {
        // stringRedisTemplate.hasKey(K key) returns Boolean
        String key = "testKeyExistsReturnsNull";
        when(stringRedisTemplate.hasKey(key)).thenReturn(null); // Mockito default for Boolean is null

        boolean result = redisCacheService.exists(key);

        assertFalse(result);
        verify(stringRedisTemplate).hasKey(key);
    }

    @Test
    void exists_whenRedisThrowsException_shouldThrowExecutionException() {
        String key = "testKeyExistsException";
        when(stringRedisTemplate.hasKey(key)).thenThrow(new RuntimeException("Redis error"));

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            redisCacheService.exists(key);
        });

        assertTrue(exception.getMessage().contains("Error checking if key exists in Redis"));
        assertNotNull(exception.getCause());
    }
}
