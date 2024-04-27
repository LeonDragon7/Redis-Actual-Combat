package com.hmdp.service.impl;

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

    @Transactional
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

        //4.扣减库存
        boolean isUpdate = seckillVoucherService
                .update()
                .setSql("stock = stock - 1")
                .eq("voucher_id",voucherId)
                .gt("stock",0)
                .update();

        if(!isUpdate) return Result.fail("库存不足");

        //6.创建订单
        VoucherOrder order = new VoucherOrder();
        order.setVoucherId(voucherId);
        order.setId(redisIdWorker.nextId("order"));
        order.setUserId(UserHolder.getUser().getId());
        orderService.save(order);

        return Result.ok(order.getId());
    }
}
