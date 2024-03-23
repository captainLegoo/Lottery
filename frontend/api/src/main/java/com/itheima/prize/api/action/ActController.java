package com.itheima.prize.api.action;

import com.alibaba.fastjson.JSON;
import com.itheima.prize.api.config.LuaScript;
import com.itheima.prize.commons.config.RabbitKeys;
import com.itheima.prize.commons.config.RedisKeys;
import com.itheima.prize.commons.db.entity.*;
import com.itheima.prize.commons.utils.ApiResult;
import com.itheima.prize.commons.utils.RedisUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

@RestController
@RequestMapping("/api/act")
@Api(tags = {"抽奖模块"})
public class ActController {

    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private LuaScript luaScript;
    private Map<String, Object> cacheWarmUPGameInfo;
    private final static Logger log = LoggerFactory.getLogger(ActController.class);
    // 创建 SimpleDateFormat 对象，指定日期时间格式
    private static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    @GetMapping("/go/{gameid}")
    @ApiOperation(value = "抽奖")
    @ApiImplicitParams({
            @ApiImplicitParam(name="gameid",value = "活动id",example = "1",required = true)
    })
    public ApiResult<Object> act(@PathVariable int gameid, HttpServletRequest request){
        // 用户未登录
        CardUser cardUser = (CardUser) request.getSession().getAttribute("user");
        //if (Objects.isNull(cardGame)) {
        //    return new ApiResult(-1, "活动不存在", null);
        //}
        if (Objects.isNull(cardUser)) {
            return new ApiResult(-1, "未登录", null);
        }
        Integer userId = cardUser.getId();
        Integer userLevel = cardUser.getLevel();
        // 获取活动信息-基本信息/会员可抽奖次数/会员最大中奖次数
        CardGame cardGame = (CardGame) redisUtil.get(RedisKeys.INFO + gameid);
        Integer enterTimes = (Integer) redisUtil.hget(RedisKeys.MAXENTER + gameid, userLevel.toString());
        Integer goalTimes = (Integer) redisUtil.hget(RedisKeys.MAXGOAL + gameid, userLevel.toString());
        // 获取用户已抽奖次数
        Integer userEnterCount = (Integer) redisUtil.get(RedisKeys.USERENTER + gameid + "_" + userId);
        // 获取用户已中奖次数
        Integer userGoalCount = (Integer) redisUtil.get(RedisKeys.USERHIT + gameid + "_" + userId);

        // 如果用户抽奖次数为空，则初始化为 0
        if (Objects.isNull(userEnterCount)) {
            redisUtil.set(RedisKeys.USERENTER + gameid + "_" + userId, 0);
            userEnterCount = 0;
        }
        // // 如果用户中奖次数为空，则初始化为 0
        if (Objects.isNull(userGoalCount)) {
            redisUtil.set(RedisKeys.USERHIT + gameid + "_" + userId, 0);
            userGoalCount = 0;
        }

        // 获取当前时间戳
        long currentTimeStamp = new Date().getTime();
        // 获取开始时间戳
        long startTimeStamp = cardGame.getStarttime().getTime();
        // 获取结束时间戳
        long endTimeStamp = cardGame.getEndtime().getTime();
        // 活动未开始
        if (startTimeStamp > currentTimeStamp) {
            return new ApiResult(-1, "活动未开始", null);
        }
        // 活动已结束
        if (endTimeStamp < currentTimeStamp) {
            return new ApiResult(-1, "活动已结束", null);
        }

        // 您的抽奖次数已用完
        if (enterTimes == null || userEnterCount >= enterTimes) {
            return new ApiResult(-1, "您的抽奖次数已用完", null);
        }

        // 您已达到最大中奖数
        if (goalTimes == null || userGoalCount >= goalTimes) {
            return new ApiResult(-1, "您已达到最大中奖数", null);
        }

        // 记录用户参与次数
        redisUtil.set(RedisKeys.USERENTER + gameid + "_" + userId, ++userEnterCount);

        // TODO 异步通知参与活动信息
        CardUserGame cardUserGame = new CardUserGame();
        cardUserGame.setUserid(userId);
        cardUserGame.setGameid(gameid);
        cardUserGame.setCreatetime(new Date(currentTimeStamp));
        String cardUserGameJson = JSON.toJSONString(cardUserGame);
        rabbitTemplate.convertAndSend(RabbitKeys.EXCHANGE_DIRECT, RabbitKeys.QUEUE_PLAY, cardUserGameJson);
        log.info("用户参与活动信息 cardUserGame -> {}", cardUserGame);

        // 抽令牌
        //Long token = luaScript.tokenCheck("game_" + gameid, String.valueOf(new Date().getTime()));
        Long token = luaScript.tokenCheck(RedisKeys.TOKENS + gameid, String.valueOf(new Date().getTime()));
        if (token == 0L) {
            return new ApiResult(-1,"奖品已抽光",null);
        } else if (token == 1L) {
            return new ApiResult(0,"未中奖",null);
        } else {
            //token有效，中奖！
            CardProduct cardProduct = (CardProduct) redisUtil.get(RedisKeys.TOKEN + gameid + "_" + token);
            // TODO cardProduct为空
            // 记录用户中奖数
            redisUtil.set(RedisKeys.USERHIT + gameid + "_" + userId, ++userGoalCount);
            log.info("token有效，中奖！ -> {}, {}", tokenConvertToOriginDateString(token) , cardProduct);

            // TODO 异步通知中奖信息
            CardUserHit cardUserHit = new CardUserHit();
            cardUserHit.setGameid(gameid);
            cardUserHit.setUserid(userId);
            cardUserHit.setProductid(cardProduct.getId());
            cardUserHit.setHittime(new Date(currentTimeStamp));
            String cardUserHitJson = JSON.toJSONString(cardUserHit);
            rabbitTemplate.convertAndSend(RabbitKeys.EXCHANGE_DIRECT, RabbitKeys.QUEUE_HIT, cardUserHitJson);
            log.info("已成功发送中奖信息 cardUserHit -> {}", cardUserHit);

            // 返回恭喜中奖与数据
            return new ApiResult<>(1,"恭喜中奖",cardProduct);
        }
    }

    @GetMapping("/info/{gameid}")
    @ApiOperation(value = "缓存信息")
    @ApiImplicitParams({
            @ApiImplicitParam(name="gameid",value = "活动id",example = "1",required = true)
    })
    public ApiResult info(@PathVariable int gameid){
        log.info("info -> 获取活动相关的缓存信息");
        // 每次对map进行重置
        cacheWarmUPGameInfo = new LinkedHashMap<>();

        // (暂定不显示:单个活动的具体奖品信息)
        // 1.活动基本信息 k-v key:活动id value:活动对象
        CardGame cardGame = (CardGame) redisUtil.get(RedisKeys.INFO + gameid);
        if (Objects.isNull(cardGame)) {
            return new ApiResult(200, "缓存信息", null);
        }
        cacheWarmUPGameInfo.put(RedisKeys.INFO + gameid, cardGame);

        // 2.活动策略 hset group:活动id key:用户等级 value:策略值
        // 2.1.获取该会员最大中奖次数
        cacheWarmUPGameInfo.put(RedisKeys.MAXGOAL + gameid, redisUtil.hmget(RedisKeys.MAXGOAL + gameid));
        // 2.2.获取该会员可抽奖次数
        cacheWarmUPGameInfo.put(RedisKeys.MAXENTER + gameid, redisUtil.hmget(RedisKeys.MAXENTER + gameid));

        // 3.每个令牌桶的奖品信息 k-v key:活动id value:奖品信息
        Map<Object, CardProduct> cardProductMap = new ConcurrentSkipListMap<>();
        List<Object> tokenList = redisUtil.lrange(RedisKeys.TOKENS + gameid, 0L, -1L);
        for (Object token : tokenList) {
            CardProduct cardProduct = (CardProduct) redisUtil.get(RedisKeys.TOKEN + gameid + "_" + token);
            String dateTimeString = tokenConvertToOriginDateString((Long) token);
            // 通过redis获取该时间错对应的奖品信息，并存入map
            cardProductMap.put(dateTimeString, cardProduct);
        }
        cacheWarmUPGameInfo.put(RedisKeys.TOKENS + gameid, cardProductMap);

        // 4.返回缓存信息
        return new ApiResult(200, "缓存信息", cacheWarmUPGameInfo);
    }

    private String tokenConvertToOriginDateString(Long token){
        return DATE_FORMAT.format(new Date(token / 1000));
    }

    private Date tokenConvertToOriginDate(Long token){
        return new Date(token / 1000);
    }
}
