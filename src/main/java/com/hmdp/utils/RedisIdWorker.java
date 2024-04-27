package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    //开始时间戳
    private static final long BEGIN_TIMESTAMP =  1704067200;

    //序列号位数
    private static final long COUNT_BITS = 32;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public long nextId(String keyPrefix){
        LocalDateTime now = LocalDateTime.now();
        //1.获取时间戳
        long nowTimestamp = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowTimestamp - BEGIN_TIMESTAMP;
        //2.获取序号
        //2.1 获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = redisTemplate.opsForValue().increment("icr:" + keyPrefix + date);
        //2.2  自增长
        //3.返回唯一Id
        return timeStamp << COUNT_BITS | count;
    }

    public static void main(String[] args) {
        LocalDateTime localDateTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
        System.out.println(localDateTime.toEpochSecond(ZoneOffset.UTC));
    }
}
