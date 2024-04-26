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
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result queryShopById(Long id) {
        //缓存穿透
        //Shop shop = queryWithPass(id);

        //缓存击穿-互斥锁
        Shop shop = queryWithExclLock(id);
        if(shop == null) return Result.fail("商铺不存在");
        return Result.ok(shop);
    }

    public Shop queryWithPass(Long id) {
        //1.查询redis是否存在商品详情数据 - 是否命中
        String key = CACHE_SHOP_KEY + id;
        String value = redisTemplate.opsForValue().get(key);

        //2.判断redis缓存是否存在
        if(StringUtils.isNotBlank(value)){
            //存在，将数据返回
            Shop shop = JSONUtil.toBean(value, Shop.class);
            return shop;
        }

        //(2).判断是否为空值(空字符串)
        if(value != null) return null;

        //3.如果为null，查询数据库
        Shop shop = getById(id);

        //4.如果数据不存在，返回提示
        if(shop == null) {
            //解决缓存穿透
            //(1).将空值(空字符串)写入redis
            redisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }

        //5.将数据保存到redis
        String shopValue = JSONUtil.toJsonStr(shop);
        redisTemplate.opsForValue().set(key,shopValue,CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //6.返回商铺信息
        return shop;
    }

    public Shop queryWithExclLock(Long id) {
        //1.查询redis是否存在商品详情数据 - 是否命中
        String key = CACHE_SHOP_KEY + id;
        String value = redisTemplate.opsForValue().get(key);

        //2.判断redis缓存是否存在
        if(StringUtils.isNotBlank(value)){
            //存在，将数据返回
            Shop shop = JSONUtil.toBean(value, Shop.class);
            return shop;
        }

        //(2).判断是否为空值(空字符串)
        if(value != null) return null;

        // 3. 实现缓存重构

        //3.1 获取互斥锁
        String keyLock = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean tryLock = tryLock(keyLock);
            //3.2 判断是否存在锁
            if(!tryLock){
                //失败
                Thread.sleep(10);
                return queryWithExclLock(id);
            }

            //3.3 成功，如果为null，查询数据库
            shop = getById(id);

            //4.如果数据不存在，返回提示
            if(shop == null) {
                //解决缓存穿透
                //(1).将空值(空字符串)写入redis
                redisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }

            //5.将数据保存到redis
            String shopValue = JSONUtil.toJsonStr(shop);
            redisTemplate.opsForValue().set(key,shopValue,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unlock(keyLock);
        }
        //6.返回商铺信息
        return shop;
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
