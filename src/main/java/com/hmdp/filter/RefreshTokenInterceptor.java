package com.hmdp.filter;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate redisTemplate;
    public RefreshTokenInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.从请求头获取token - key
        String token = request.getHeader("authenticate");
        String tokenKey = LOGIN_USER_KEY + token;
        if(StringUtils.isBlank(tokenKey)) return true;
        //2.从redis获取用户信息
        Map<Object, Object> map = redisTemplate.opsForHash().entries(tokenKey);
        if(map.isEmpty())
            return true;

        //3.将map转换成用户对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(map, new UserDTO(), false);


        //4.将用户信息保存到ThreadLocal
        UserHolder.saveUser(userDTO);

        //5.刷新redis的key时间
        redisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES);
        //放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
