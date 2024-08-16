package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @Author: WengYiyang
 * @Description: TODO
 * @Date: 2024/8/16 14:41
 * @Version: 1.0
 */
@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 设置TTL过期时间，设置时间和单位
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    // 设置逻辑过期时间
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期时间，利用redisData
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 缓存穿透
    // 返回类型不确定，使用泛型，Class是传入具体的类型
    // 要在redis中查询需要key的前缀＋id
    // 并不知道数据库查询逻辑，让调用者传入一个有参有返回值的函数,Function中ID是入参，R是返回值
    // 传入过期时间
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1、从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2、判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3、存在，直接返回
            // 返回需要转成java对象返回,进行反序列化
            return JSONUtil.toBean(json, type);
        }
        // 判断命中的是否是空值（空字符串）
        if (json != null) {
            // 返回一个错误信息
            return null;
        }
        // 4、不存在，根据id查询数据库
        // 并不知道数据库查询逻辑，让调用者传入一个有参有返回值的函数
        R r = dbFallback.apply(id);
        // 5、数据库中不存在，返回错误
        if (r == null) {
            // 将空值写入redis,空值的有效期应该给短一点
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 再返回错误信息
            return null;
        }
        // 6、数据库中存在，将商铺数据写入redis
        this.set(key, r, time, unit);
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // 不能直接返回flag，如果直接返回 flag（即 return flag;），虽然并不会引起空指针异常，因为 flag 可以是 Boolean.TRUE、Boolean.FALSE 或 null，但是需要排除null的情况
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1、从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2、判断是否存在
        if (StrUtil.isBlank(json)) {
            // 3、存在，直接返回
            return null;
        }
        // 4、命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5、判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1 未过期，直接返回店铺信息
            return r;
        }
        // 5.2 已过期，需要缓存重建

        // 6、缓存重建
        // 6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2 判断是否获取锁成功
        if (isLock) {
            // 6.3 成功，开启独立线程，实现缓存重建
            // 用线程池
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                // 重建缓存
                try {
                    // 重建缓存分两步
                    // 先查询数据库
                    R r1 = dbFallback.apply(id);
                    // 再写入redis
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 6.4 失败，返回过期的商铺信息
        return r;
    }
}
