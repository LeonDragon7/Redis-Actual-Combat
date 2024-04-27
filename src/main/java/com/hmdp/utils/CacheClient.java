package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
public class CacheClient {

    private final StringRedisTemplate redisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private void set(String key,Object value,Long time,TimeUnit unit){
       redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    private void setLoginExpire(String key,Object value,Long expireTime,TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(expireTime)));

        //3.将封装数据存储到redis
        redisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 缓存穿透
     * @param id
     * @return
     */
    public <R,ID> R queryWithPassThrough(String key_prefix, ID id, Class<R> type, Function<ID,R> dbfunction,Long time,TimeUnit unit) {
        //1.查询redis是否存在商品详情数据 - 是否命中
        String key = key_prefix + id;
        String value = redisTemplate.opsForValue().get(key);

        //2.判断redis缓存是否存在
        if(StringUtils.isNotBlank(value)){
            //存在，将数据返回
            R r = JSONUtil.toBean(value, type);
            return r;
        }

        //(2).判断是否为空值(空字符串)
        if(value != null) return null;

        //3.如果为null，查询数据库
        R r = dbfunction.apply(id);

        //4.如果数据不存在，返回提示
        if(r == null) {
            //解决缓存穿透
            //(1).将空值(空字符串)写入redis
            redisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        //5.将数据保存到redis
        this.set(key,r,time, unit);

        //6.返回商铺信息
        return r;
    }

    /**
     * 缓存击穿 - 互斥锁
     * @param id
     * @return
     */
    public <R,ID> R queryWithMutex(String key_prefix, ID id, Class<R> type, Function<ID,R> dbfunction,Long time,TimeUnit unit) {
        //1.查询redis是否存在商品详情数据 - 是否命中
        String key = key_prefix + id;
        String value = redisTemplate.opsForValue().get(key);

        //2.判断redis缓存是否存在
        if(StringUtils.isNotBlank(value)){
            //存在，将数据返回
            R r = JSONUtil.toBean(value, type);
            return r;
        }

        //(2).判断是否为空值(空字符串)
        if(value != null) return null;

        // 3. 实现缓存重构

        //3.1 获取互斥锁
        String keyLock = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            boolean tryLock = tryLock(keyLock);
            //3.2 判断是否存在锁
            if(!tryLock){
                //失败
                Thread.sleep(10);
                return queryWithMutex(key_prefix,id,type,dbfunction,time,unit);
            }

            //3.3 成功，如果为null，查询数据库
            r = dbfunction.apply(id);

            //4.如果数据不存在，返回提示
            if(r == null) {
                //解决缓存穿透
                //(1).将空值(空字符串)写入redis
                redisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }

            //5.将数据保存到redis
           this.set(key,r,time,unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unlock(keyLock);
        }
        //6.返回商铺信息
        return r;
    }

    /**
     * 缓存击穿 - 逻辑时间
     * @param id
     * @return
     */
    public <R,ID> R queryWithLogicalExpire(String key_prefix, ID id, Class<R> type, Function<ID,R> dbfunction,Long time,TimeUnit unit) {
        //1.查询redis是否存在商品详情数据 - 是否命中
        String key = key_prefix + id;
        String value = redisTemplate.opsForValue().get(key);

        //2.判断redis缓存是否存在
        if(StringUtils.isBlank(value))
            //不存在，将数据返回
            return null;

        //4. 命中，序列化value的值
        RedisData redisData = JSONUtil.toBean(value, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //5 判断逻辑时间是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            //未过期，返回商铺信息
            return r;
        }

        // 6.获取互斥锁
        String keyLock = LOCK_SHOP_KEY + id;
        boolean tryLock = tryLock(keyLock);
        if(tryLock){
            CACHE_REBUILD_EXECUTOR.submit(() ->{
                try {
                    //查询数据库
                    R newR = dbfunction.apply(id);
                    //重建缓存
                    this.setLoginExpire(key,newR,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(keyLock);
                }
            });
        }
        //7.未获取，返回商铺信息
        return r;
    }

    /**
     * 设置互斥锁
     * @param key
     * @return
     */
    private boolean tryLock(String key){
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 删除互斥锁
     * @param key
     */
    private void unlock(String key){
        redisTemplate.delete(key);
    }
}
