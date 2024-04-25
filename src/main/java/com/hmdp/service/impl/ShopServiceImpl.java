package com.hmdp.service.impl;

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

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

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
        //1.查询redis是否存在商品详情数据
        String key = CACHE_SHOP_KEY + id;
        String value = redisTemplate.opsForValue().get(key);

        //2.判断redis缓存是否命中
        if(StringUtils.isNotBlank(value)){
            //存在，将数据返回
            Shop shop = JSONUtil.toBean(value, Shop.class);
            return Result.ok(shop);
        }

        //3.不存在，查询数据库
        Shop shop = getById(id);

        //4.如果数据不存在，返回提示
        if(shop == null) return Result.fail("商铺不存在");

        //5.将数据保存到redis
        String shopValue = JSONUtil.toJsonStr(shop);
        redisTemplate.opsForValue().set(key,shopValue,CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //6.返回商铺信息
        return Result.ok(shop);
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
