package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠卷信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        //2.判断秒杀是否开始和结束
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀未开始");
        }
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已结束");
        }

        //3.判断库存是否充足
        if(voucher.getStock() < 1) return Result.fail("库存不足");


        Long userId = UserHolder.getUser().getId();
        //方法一：
        //为什么锁要放在外面？
        //因为锁如果在里面，等锁结束后，事务是交给spring管理的后执行，在这期间会造成线程安全问题
        //intern():toString底层会new String()创建不同对象，造成同一个用户会产生不同的锁，intern会取出对象相等的值来解决
//        synchronized (userId.toString().intern()){ //悲观锁 解决一人一单问题
        //获取代理对象(事务)
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId,userId);
//        }

        //方法二：（分布式锁）
        //1. 创建锁对象
        SimpleRedisLock redisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        //2. 获取锁
        boolean tryLock = redisLock.tryLock(500);
        //3. 判断是否获取锁
        if(!tryLock) return Result.fail("不允许重复下单");
        //4.创建订单
        try {
            //获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId,userId);
        } finally {
            redisLock.unLock();
        }
    }
    @Transactional
    public  Result createVoucherOrder(Long voucherId,Long userId) {
        synchronized(userId.toString().intern()){
            // 4.查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                return Result.fail("用户已经购买过一次！");
            }

            // 6.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    // where id = ? and stock > 0
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0) //乐观锁 解决下单超卖问题
                    .update();
            if (!success) {
                // 扣减失败
                return Result.fail("库存不足！");
            }

            // 7.创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            // 7.1.订单id
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 7.2.用户id
            voucherOrder.setUserId(userId);
            // 7.3.代金券id
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);

            // 8.返回订单id
            return Result.ok(orderId);
        }
    }
}
