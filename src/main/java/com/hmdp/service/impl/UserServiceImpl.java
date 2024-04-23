package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Random;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号码
        if(!RegexUtils.isPhoneInvalid(phone))
            //手机号码不合法
           return Result.fail("手机号码格式不正确");

        //2.生成验证码
        String code = RandomUtil.randomNumbers(6);

        //3.将验证码存储到session
        session.setAttribute("code",code);

        //4.TODO 发送验证码
        log.debug("验证码：{}",code);

        //5.返回
        return Result.ok();
    }
}
