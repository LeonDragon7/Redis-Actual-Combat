package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONString;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private CacheClient cacheClient;

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result queryShopById(Long id) {
        // 解决缓存穿透
        Shop shop = cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿
        // Shop shop = cacheClient
        //         .queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 逻辑过期解决缓存击穿
        // Shop shop = cacheClient
        //         .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);

        if(shop == null) return Result.fail("商铺不存在");
        return Result.ok(shop);
    }
//
//    /**
//     * 缓存穿透
//     * @param id
//     * @return
//     */
//    public Shop queryWithPassThrough(Long id) {
//        //1.查询redis是否存在商品详情数据 - 是否命中
//        String key = CACHE_SHOP_KEY + id;
//        String value = redisTemplate.opsForValue().get(key);
//
//        //2.判断redis缓存是否存在
//        if(StringUtils.isNotBlank(value)){
//            //存在，将数据返回
//            Shop shop = JSONUtil.toBean(value, Shop.class);
//            return shop;
//        }
//
//        //(2).判断是否为空值(空字符串)
//        if(value != null) return null;
//
//        //3.如果为null，查询数据库
//        Shop shop = getById(id);
//
//        //4.如果数据不存在，返回提示
//        if(shop == null) {
//            //解决缓存穿透
//            //(1).将空值(空字符串)写入redis
//            redisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//            return null;
//        }
//
//        //5.将数据保存到redis
//        String shopValue = JSONUtil.toJsonStr(shop);
//        redisTemplate.opsForValue().set(key,shopValue,CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//        //6.返回商铺信息
//        return shop;
//    }
//
//    /**
//     * 缓存击穿 - 互斥锁
//     * @param id
//     * @return
//     */
//    public Shop queryWithMutex(Long id) {
//        //1.查询redis是否存在商品详情数据 - 是否命中
//        String key = CACHE_SHOP_KEY + id;
//        String value = redisTemplate.opsForValue().get(key);
//
//        //2.判断redis缓存是否存在
//        if(StringUtils.isNotBlank(value)){
//            //存在，将数据返回
//            Shop shop = JSONUtil.toBean(value, Shop.class);
//            return shop;
//        }
//
//        //(2).判断是否为空值(空字符串)
//        if(value != null) return null;
//
//        // 3. 实现缓存重构
//
//        //3.1 获取互斥锁
//        String keyLock = LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            boolean tryLock = tryLock(keyLock);
//            //3.2 判断是否存在锁
//            if(!tryLock){
//                //失败
//                Thread.sleep(10);
//                return queryWithMutex(id);
//            }
//
//            //3.3 成功，如果为null，查询数据库
//            shop = getById(id);
//
//            //4.如果数据不存在，返回提示
//            if(shop == null) {
//                //解决缓存穿透
//                //(1).将空值(空字符串)写入redis
//                redisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//                return null;
//            }
//
//            //5.将数据保存到redis
//            String shopValue = JSONUtil.toJsonStr(shop);
//            redisTemplate.opsForValue().set(key,shopValue,CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            //释放互斥锁
//            unlock(keyLock);
//        }
//        //6.返回商铺信息
//        return shop;
//    }
//
//    /**
//     * 缓存击穿 - 逻辑时间
//     * @param id
//     * @return
//     */
//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
//    public Shop queryWithLogicalExpire(Long id) {
//        //1.查询redis是否存在商品详情数据 - 是否命中
//        String key = CACHE_SHOP_KEY + id;
//        String value = redisTemplate.opsForValue().get(key);
//
//        //2.判断redis缓存是否存在
//        if(StringUtils.isBlank(value))
//            //不存在，将数据返回
//            return null;
//
//        //4. 命中，序列化value的值
//        RedisData redisData = JSONUtil.toBean(value, RedisData.class);
//        Shop shop= JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        //5 判断逻辑时间是否过期
//        LocalDateTime expireTime = redisData.getExpireTime();
//        if(expireTime.isAfter(LocalDateTime.now())){
//            //未过期，返回商铺信息
//            return shop;
//        }
//
//        // 6.获取互斥锁
//        String keyLock = LOCK_SHOP_KEY + id;
//        boolean tryLock = tryLock(keyLock);
//        if(tryLock){
//            CACHE_REBUILD_EXECUTOR.submit(() ->{
//                try {
//                    //重建缓存
//                    saveShopRedis(id,30L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }finally {
//                    //释放锁
//                    unlock(keyLock);
//                }
//            });
//        }
//        //7.未获取，返回商铺信息
//        return shop;
//    }
//
//    /**
//     * 设置互斥锁
//     * @param key
//     * @return
//     */
//    private boolean tryLock(String key){
//        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    /**
//     * 删除互斥锁
//     * @param key
//     */
//    private void unlock(String key){
//        redisTemplate.delete(key);
//    }
//
//    /**
//     * 缓存预热
//     */
//    public void saveShopRedis(Long id,Long expireTime) throws InterruptedException {
//        //1.根据id获取商铺信息
//        Shop shop = getById(id);
//        Thread.sleep(200);
//        //2.封装RedisData数据
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
//
//        //3.将封装数据存储到redis
//        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
//    }
    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @Transactional
    @Override
    public Result update(Shop shop) {
        //判断更新的商铺信息id是否为空
        if(shop.getId() == null) return Result.fail("商铺不存在");
        //更新数据库
        updateById(shop);

        //删除redis的商铺信息的缓存数据
        redisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
