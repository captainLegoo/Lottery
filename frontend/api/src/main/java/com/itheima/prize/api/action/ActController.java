package com.itheima.prize.api.action;

import com.itheima.prize.api.config.LuaScript;
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
        //TODO
        return null;
    }

    @GetMapping("/info/{gameid}")
    @ApiOperation(value = "缓存信息")
    @ApiImplicitParams({
            @ApiImplicitParam(name="gameid",value = "活动id",example = "1",required = true)
    })
    public ApiResult info(@PathVariable int gameid){
        log.info("info -> 获取活动相关的缓存信息");
        // 每次对map进行重置
        cacheWarmUPGameInfo = new HashMap<>();

        // (暂定不显示:单个活动的具体奖品信息)
        // 1.活动基本信息 k-v key:活动id value:活动对象
        cacheWarmUPGameInfo.put(RedisKeys.INFO + gameid, redisUtil.get(RedisKeys.INFO + gameid));

        // 2.每个令牌桶的奖品信息 k-v key:活动id value:奖品信息
        Map<Object, CardProduct> cardProductMap = new ConcurrentSkipListMap<>();
        List<Object> tokenList = redisUtil.lrange(RedisKeys.TOKENS + gameid, 0L, -1L);
        for (Object token : tokenList) {
            CardProduct cardProduct = (CardProduct) redisUtil.get(RedisKeys.TOKEN + gameid + "_" + token);
            String dateTimeString = tokenConvertToDate((Long) token);
            // 通过redis获取该时间错对应的奖品信息，并存入map
            cardProductMap.put(dateTimeString, cardProduct);
        }
        cacheWarmUPGameInfo.put(RedisKeys.TOKENS + gameid, cardProductMap);

        // 3.活动策略 hset group:活动id key:用户等级 value:策略值
        // 3.1.获取该会员最大中奖次数
        cacheWarmUPGameInfo.put(RedisKeys.MAXGOAL + gameid, redisUtil.hmget(RedisKeys.MAXGOAL + gameid));
        // 3.2.获取该会员可抽奖次数
        cacheWarmUPGameInfo.put(RedisKeys.MAXENTER + gameid, redisUtil.hmget(RedisKeys.MAXENTER + gameid));

        // 4.返回缓存信息
        return new ApiResult(200, "缓存信息", cacheWarmUPGameInfo);
    }

    private String tokenConvertToDate(Long token){
        return DATE_FORMAT.format(new Date(token / 1000));
    }
}
