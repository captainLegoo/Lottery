package com.itheima.prize.api.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itheima.prize.commons.config.RedisKeys;
import com.itheima.prize.commons.db.entity.CardUser;
import com.itheima.prize.commons.db.service.CardUserService;
import com.itheima.prize.commons.utils.ApiResult;
import com.itheima.prize.commons.utils.PasswordUtil;
import com.itheima.prize.commons.utils.RedisUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping(value = "/api")
@Api(tags = {"登录模块"})
public class LoginController {
    @Autowired
    private CardUserService userService;

    @Autowired
    private RedisUtil redisUtil;

    private final AtomicInteger loginErrorCount = new AtomicInteger(5);

    @PostMapping("/login")
    @ApiOperation(value = "登录")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "account", value = "用户名", required = true),
            @ApiImplicitParam(name = "password", value = "密码", required = true)
    })
    public ApiResult login(HttpServletRequest request, @RequestParam String account, @RequestParam String password) {
        // 检查用户是否已经达到登录尝试上限
        boolean isExistAccountInRedis = redisUtil.hasKey(RedisKeys.USERLOGINTIMES + account);
        if (isExistAccountInRedis) {
            return new ApiResult(0, "密码错误5次，请5分钟后再登录", null);
        }

        LambdaQueryWrapper<CardUser> userQueryWrapper = new LambdaQueryWrapper<>();
        userQueryWrapper.eq(CardUser::getUname, account);
        CardUser cardUser = userService.getOne(userQueryWrapper);

        // 判断用户是否存在
        if (!Objects.isNull(cardUser)) {
            // 用户存在
            // 密码校验
            String passwordFromUser = PasswordUtil.encodePassword(password);
            String passwordFromDatabase = cardUser.getPasswd();
            if (passwordFromUser.equals(passwordFromDatabase)) {
                // 密码正确
                // 清除用户敏感信息
                cardUser.setPasswd(null);
                cardUser.setIdcard(null);
                // 将用户信息存储在会话中
                request.getSession().setAttribute("user", cardUser);
                // 重置登录次数
                loginErrorCount.set(5);
                // 返回成功登录用户信息
                return new ApiResult(1, "登录成功", cardUser);
            } else {
                // 密码错误
                if (loginErrorCount.decrementAndGet() <= 0) {
                    // 超过五次，在redis中设置该用户禁止登录五分钟
                    redisUtil.set(RedisKeys.USERLOGINTIMES + account, "", 5 * 60);
                    // 重置登录次数
                    loginErrorCount.set(5);
                    return new ApiResult(0, "密码错误5次，请5分钟后再登录", null);
                }
            }
        }
        // 用户不存在
        return new ApiResult(0, "账户名或密码错误", null);
    }

    @GetMapping("/logout")
    @ApiOperation(value = "退出")
    public ApiResult logout(HttpServletRequest request) {
        request.getSession().removeAttribute("user");
        return new ApiResult(1, "退出成功", null);
    }
}