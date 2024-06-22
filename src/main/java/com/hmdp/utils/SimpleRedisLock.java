package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private String name;
    private final static String key_prefix = "lock1:";
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeOutSec) {
        //1. 获取当前线程标识
        long threadId = Thread.currentThread().getId();
        //2. 获取锁
        Boolean ifAbsent = stringRedisTemplate.opsForValue().setIfAbsent(key_prefix + name, threadId + "", timeOutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ifAbsent);//Boolean.TRUE.equals():避免拆箱结果如果为false造成空指针
    }

    @Override
    public void unLock() {
        //通过key删除锁
        stringRedisTemplate.delete(key_prefix + name);
    }
}
