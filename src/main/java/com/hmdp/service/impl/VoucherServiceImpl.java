package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private IVoucherOrderService orderService;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
    }


    @Override
    public Result seckillVouche(Long voucherId) {
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

        //4.根据优惠卷id和用户id查询订单 - 一人一单
        //为啥锁需要加在方法体外面？
        //如果锁在方法体内先释放，且事务是交给Spring管理的，后执行，会导致并发时锁先释放完还没结束事务，又有线程来操作数据库
        //保证线程安全
        Long userId = UserHolder.getUser().getId();
        //intern():toString底层是new String,会造成同一个用户不同对象，所以需要保证当前用户相同时，持同一把锁
        synchronized(userId.toString().intern()) { // 悲观锁解决一人一单
            //获取代理对象（事务）
            IVoucherService proxy = (IVoucherService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId, userId);
        }
    }
    @Transactional
    public Result createVoucherOrder(Long voucherId,Long userId){

        int count = query()
                .eq("user_id", userId)
                .eq("voucherId", voucherId)
                .count();
        if (count > 0) return Result.fail("该优惠卷已使用");

        //5.扣减库存
        boolean isUpdate = seckillVoucherService
                .update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0) //乐观锁 解决超卖问题
                .update();

        if (!isUpdate) return Result.fail("库存不足");

        //6.创建订单
        VoucherOrder order = new VoucherOrder();
        order.setVoucherId(voucherId);
        long orderId = redisIdWorker.nextId("order");
        order.setId(orderId);
        order.setUserId(userId);
        orderService.save(order);

        return Result.ok(orderId );

    }
}
