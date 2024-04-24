package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
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

    @Autowired
    private StringRedisTemplate redisTemplate;
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

        //3.将验证码存储到redis
        redisTemplate.opsForValue().set("code",code);
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
        if(RegexUtils.isPhoneInvalid(phone))
            //手机号码不合法
            return Result.fail("手机号码格式不正确");
        //2.校验验证码

        //从redis获取验证码
        String code = redisTemplate.opsForValue().get("code");

        if(code == null || !loginForm.getCode().equals(code))
            return Result.fail("验证码不正确");

        //2.根据手机号判断是否存在用户
        User user = query().eq("phone", phone).one();
        if(user == null){
            //不存在用户
            user = createWithPhone(phone);
        }

        UserDTO userDTO = new UserDTO();
        BeanUtil.copyProperties(user,userDTO);

        //3.将用户保存到redis

        //3.1 设置token的key
        String token = UUID.randomUUID().toString(true);
        String tokenKey = LOGIN_USER_KEY + token;

        //3.2 将用户信息转成map
        Map<String, Object> map = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,filedValue) -> filedValue.toString()));

        //3.3 存入信息
        redisTemplate.opsForHash().putAll(tokenKey,map);

        //4.设置key有效期
        redisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //返回token
        return Result.ok(token);
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
