package com.hmdp.filter;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取session
        HttpSession session = request.getSession();
        //2.从session中获取用户信息
        UserDTO userDTO = (UserDTO)session.getAttribute("userDTO");

        //3.判断用户是否为空
        if(userDTO == null) {
            //不存在，返回错误码，并拦截
            response.setStatus(401);
            return false;
        }
        //4.将用户信息保存到ThreadLocal
        UserHolder.saveUser(userDTO);
        //放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
