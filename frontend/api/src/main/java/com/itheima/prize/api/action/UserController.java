package com.itheima.prize.api.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.prize.commons.db.entity.CardUser;
import com.itheima.prize.commons.db.entity.CardUserDto;
import com.itheima.prize.commons.db.entity.ViewCardUserHit;
import com.itheima.prize.commons.db.service.GameLoadService;
import com.itheima.prize.commons.db.service.ViewCardUserHitService;
import com.itheima.prize.commons.utils.ApiResult;
import com.itheima.prize.commons.utils.PageBean;
import com.itheima.prize.commons.utils.RedisUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

@RestController
@RequestMapping(value = "/api/user")
@Api(tags = {"用户模块"})
public class UserController {

    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private ViewCardUserHitService hitService;
    @Autowired
    private GameLoadService loadService;

    @GetMapping("/info")
    @ApiOperation(value = "用户信息")
    public ApiResult info(HttpServletRequest request) {
        // 通过session获取用户信息
        CardUser cardUser = (CardUser) request.getSession().getAttribute("user");
        if (Objects.isNull(cardUser)) {
            return new ApiResult(0, "登录超时",null);
        }
        // 获取user id
        Integer userId = cardUser.getId();
        // 构建用户dto
        CardUserDto cardUserDto = new CardUserDto(cardUser);
        // 获取用户的参与的活动数
        Integer gamesNum = loadService.getGamesNumByUserId(userId);
        cardUserDto.setGames(gamesNum);
        // 获取用户的中奖数
        Integer prizesNum = loadService.getPrizesNumByUserId(userId);
        cardUserDto.setProducts(prizesNum);
        // 返回数据
        return new ApiResult(1, "成功", cardUserDto);
    }

    @GetMapping("/hit/{gameid}/{curpage}/{limit}")
    @ApiOperation(value = "我的奖品")
    @ApiImplicitParams({
            @ApiImplicitParam(name="gameid",value = "活动id（-1=全部）",dataType = "int",example = "1",required = true),
            @ApiImplicitParam(name = "curpage",value = "第几页",defaultValue = "1",dataType = "int", example = "1"),
            @ApiImplicitParam(name = "limit",value = "每页条数",defaultValue = "10",dataType = "int",example = "3")
    })
    public ApiResult hit(@PathVariable int gameid,@PathVariable int curpage,@PathVariable int limit,HttpServletRequest request) {
        CardUser cardUser = (CardUser) request.getSession().getAttribute("user");
        Integer userId = cardUser.getId();

        Page<ViewCardUserHit> page = new Page<>(curpage, limit);
        LambdaQueryWrapper<ViewCardUserHit> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ViewCardUserHit::getUserid, userId);
        if (gameid != -1) {
            // -1 查询所有，非-1 查询
            lqw.eq(ViewCardUserHit::getGameid, gameid);
        }
        lqw.orderByDesc(ViewCardUserHit::getHittime);
        hitService.page(page, lqw);

        return new ApiResult(1, "成功", new PageBean<ViewCardUserHit>(page));
    }


}