package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private String name;
    private final static String key_prefix = "lock:";
    private final static String id_prefix = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeOutSec) {
        //1. 获取当前线程标识
        String threadId = id_prefix + Thread.currentThread().getId();
        //2. 获取锁
        Boolean ifAbsent = stringRedisTemplate.opsForValue().setIfAbsent(key_prefix + name, threadId, timeOutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ifAbsent);//Boolean.TRUE.equals():避免拆箱结果如果为false造成空指针
    }

    @Override
    public void unLock() {

        //方法一：
//        //获取当前线程标识
//        String threadId = id_prefix + Thread.currentThread().getId();
//        //获取锁总的线程标识
//        String id = stringRedisTemplate.opsForValue().get(key_prefix + name);
//        //判断上述是否相等
//        if(threadId.equals(id)){
//            //释放锁
//            stringRedisTemplate.delete(key_prefix + name);
//        }

        //方法二：调用lua脚本，保证判断和释放的原子性
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(key_prefix + name),
                id_prefix + Thread.currentThread().getId());
    }
}
