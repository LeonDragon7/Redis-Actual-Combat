package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.util.BeanUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.PasswordEncoder;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.Random;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

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

    /**
     * 发送手机验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号码
        if(RegexUtils.isPhoneInvalid(phone))
            //手机号码不合法
           return Result.fail("手机号码格式不正确");

        //2.生成验证码
        String code = RandomUtil.randomNumbers(6);

        //3.将验证码存储到session
        session.setAttribute("code",code);
        session.setAttribute("phone",phone);
        //4.模拟发送验证码
        log.info("验证码：{}",code);

        //5.返回
        return Result.ok();
    }

    /**
     * 登录
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号码
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone) || !phone.equals(session.getAttribute("phone").toString()))
            //手机号码不合法
            return Result.fail("手机号码格式不正确");
        //2.校验验证码
        Object sessionCode = session.getAttribute("code");

        if(sessionCode == null || !loginForm.getCode().equals(sessionCode.toString()))
            return Result.fail("验证码不正确");

        //2.根据手机号判断是否存在用户
        User user = query().eq("phone", phone).one();
        if(user == null){
            //不存在用户
            user = createWithPhone(phone);
        }

        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        //将用户数据保存到session
        session.setAttribute("userDTO",userDTO);

        //返回
        return Result.ok();
    }

    /**
     * 保存用户
     * @param phone
     * @return
     */
    private User createWithPhone(String phone) {
        User user = new User().setPhone(phone).setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(8));
        save(user);
        return user;
    }
}
