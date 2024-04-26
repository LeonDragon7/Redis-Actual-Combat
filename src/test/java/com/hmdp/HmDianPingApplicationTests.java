package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;

    /**
     * 缓存击穿-逻辑时间
     */
    @Test
    public void queryWithLogicalTimeTest() throws InterruptedException {
        shopService.saveShopRedis(1L,10L);
    }
}
