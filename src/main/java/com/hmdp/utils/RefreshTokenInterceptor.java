package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @Author: WengYiyang
 * @Description: TODO
 * @Date: 2024/7/6 18:17
 * @Version: 1.0
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;
    //用构造函数注入，因为不是spring注入的，是手动注入
    //联动MvcConfig.java
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //前置拦截器
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //客户端请求信息，服务器响应信息，请求的处理器
        //1、获取session
//        HttpSession session = request.getSession();
        //2、获取session中的用户
//        Object user = session.getAttribute("user");
        //3、判断用户是否存在
//        if (user == null) {
            //4、不存在，拦截，返回401状态码
//            response.setStatus(401);
//            return false;
//        }
        //5、存在，保存用户信息到ThreadLocal
//        UserHolder.saveUser((UserDTO) user);
        //6、放行
//        return HandlerInterceptor.super.preHandle(request, response, handler);

        // 1、获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            response.setStatus(401);
            return true;
        }
        // 2、基于token获取redis中的用户
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if (userMap.isEmpty()) {
            return true;
        }
        // 5、将查询到的Hash数据转为UserDTO对象
        // UserDTO = User Data Transfer Object
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 6、存在，则保存用户信息到ThereadLocal
        UserHolder.saveUser(userDTO);
        // 7、刷新token有效期
        stringRedisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 8、放行
        return HandlerInterceptor.super.preHandle(request, response, handler);

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
        //移除用户
        UserHolder.removeUser();
    }
}
