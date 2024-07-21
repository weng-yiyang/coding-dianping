package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1、前端提交手机号、后端校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2、如果不符合，返回错误信息
            return Result.fail("手机号格式错误，请输入正确的手机号！");
        }
        //3、符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4、保存至session
        //session.setAttribute("code", code);
        //4、保存至redis，phone加业务功能前缀，code加有效期 //set key value ex(有效期) 120秒
        //phone加业务功能前缀，code加有效期，前缀和有效期最好定义成常量
        //stringRedisTemplate.opsForValue().set("login:code:" + phone, code, 2, TimeUnit.MINUTES);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //5、发送验证码，需要第三方平台
        log.debug("发送短信验证码成功，验证码：{}", code);
        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //登录注册合二为一

        //1、校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        //2、校验验证码
        // TODO 从Redis获取验证码
        Object cacheCode = session.getAttribute("code");//取出验证码
        //code应该放入常量值，不应该像现在这样作为魔法值使用
        String code = loginForm.getCode();//前端提的code
        if (cacheCode == null || cacheCode.toString().equals(code)) {
            //3、不一致，报错
            return Result.fail("验证码错误！");
        }
        //反向校验，不用像正向校验那样if嵌套
        //4、一致，根据手机号查询用户是否存在 select * from tb_user where phone = ?;
        User user = query().eq("phone", phone).one();
        //知道是tb_user表，因为User中有注解
        //5、判断用户是否存在
        if (user == null) {
            //6、不存在，创建新用户
            user = createUserWithPhone(phone);
        }
        //7、保存用户信息到session
        //保存至Redis(hash存储，key用token，token还要返回前端)
        //7.1、随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        //7.2、将User对象转为hashMap去存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldname, fielfvalue) -> fielfvalue.toString()));
        //7.3、存储，要给token设置有效期
        //String tokenKey = "login:token:" + token;
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        //7.4、设置token有效期
        //stringRedisTemplate.expire(tokenKey, 30, TimeUnit.MINUTES);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //session.setAttribute("user", user);
        //session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        //8、返回token到前端
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //1、创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //2、保存用户
        save(user);
        return user;
    }
}
