package com.itheima.prize.msg;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itheima.prize.commons.config.RedisKeys;
import com.itheima.prize.commons.db.entity.*;
import com.itheima.prize.commons.db.service.*;
import com.itheima.prize.commons.utils.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 活动信息预热，每隔1分钟执行一次
 * 查找未来1分钟内（含），要开始的活动
 */
@Component
public class GameTask {
    private final static Logger log = LoggerFactory.getLogger(GameTask.class);
    @Autowired
    private CardGameService gameService;
    @Autowired
    private CardGameProductService gameProductService;
    @Autowired
    private CardGameRulesService gameRulesService;
    @Autowired
    private GameLoadService gameLoadService;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private CardProductService cardProductService;

    @Scheduled(cron = "0 * * * * ?")
    public void execute() {
        System.out.printf("scheduled!"+new Date());
        log.info("scheduled! -> {}", new Date());
        Map<Integer, CardProduct> productMap = new HashMap<>();

        // 1.查询1分钟内的活动
        // 1.1.获取当前时间
        long currentTimeStamp = new Date().getTime();
        // 1.2.获取全部的game
        List<CardGame> cardGameList = gameService.list();
        // 1.3.过滤哪些game是在下一个1分钟
        cardGameList = cardGameList.stream().filter(cardGame -> {
            long gameStartTimeStamp = cardGame.getStarttime().getTime();
            // 1.4.返回在1分钟之内的game
            return gameStartTimeStamp > currentTimeStamp && Math.abs(gameStartTimeStamp - currentTimeStamp) <= 60 * 1000;
        }).collect(Collectors.toList());

        // 2.循环遍历活动列表，挨个处理
        for (CardGame cardGame : cardGameList) {
            List<Integer> productIdList = new ArrayList<>();
            Integer gameId = cardGame.getId();
            // 2.1.查询该活动相关的奖品列表及数量
            // 根据 gameId 查询奖品数量
            LambdaQueryWrapper<CardGameProduct> cardGameProductLambdaQueryWrapper = new LambdaQueryWrapper<>();
            cardGameProductLambdaQueryWrapper.eq(CardGameProduct::getGameid, gameId);
            List<CardGameProduct> cardGameProductList = gameProductService.list(cardGameProductLambdaQueryWrapper);
            // 2.2.遍历奖品列表
            for (CardGameProduct cardGameProduct : cardGameProductList) {
                // 奖品数量
                Integer productAmount = cardGameProduct.getAmount();
                // 奖品Id
                Integer productId = cardGameProduct.getProductid();
                // 查询具体奖品的信息
                CardProduct cardProduct = cardProductService.getById(productId);
                productMap.put(productId, cardProduct);

                // 将奖品存入list中
                for (int i = 0; i < productAmount; i++) {
                    productIdList.add(productId);
                }
            }
            // 打乱奖品顺序
            Collections.shuffle(productIdList);

            // 3、根据总数量生成奖品相关的令牌桶(时间戳)
            List<Long> tokenList = new ArrayList<>();
            // 3.1.获取活动开始时间
            Date gameStartTime = cardGame.getStarttime();
            long gameStartTimeStamp = gameStartTime.getTime();
            // 3.2.获取活动结束时间
            Date cardGameEndTime = cardGame.getEndtime();
            long gameEndTimeStamp = cardGameEndTime.getTime();
            // 3.3.计算出每个奖品的令牌桶-时间戳
            long duration = gameEndTimeStamp - gameStartTimeStamp;
            int totalProductSize = productIdList.size();
            for (int i = 0; i < totalProductSize; i++) {
                // 3.3.1.随机时间戳
                long rnd = gameStartTimeStamp + new Random().nextInt((int) duration);
                long token = rnd * 1000 + new Random().nextInt(999);
                // 3.3.2.将时间戳存入token的list中
                tokenList.add(token);
                // 3.3.3.将 token与奖品的关系 存入redis
                Integer productId = productIdList.get(i);
                redisUtil.set(RedisKeys.TOKEN + gameId + "_" + token, productMap.get(productId));
            }

            // 4.活动基本信息/活动策略/抽奖令牌桶/奖品信息等，放⼊Redis
            // 4.1.活动基本信息 k-v key:活动id value:活动对象
            redisUtil.set(RedisKeys.INFO + gameId, cardGame, -1);
            // 4.2.活动策略 hset group:活动id key:用户等级 value:策略值
            LambdaQueryWrapper<CardGameRules> cardGameRulesLambdaQueryWrapper = new LambdaQueryWrapper<>();
            cardGameRulesLambdaQueryWrapper.eq(CardGameRules::getGameid, gameId);
            List<CardGameRules> gameRulesList = gameRulesService.list(cardGameRulesLambdaQueryWrapper);
            for (CardGameRules cardGameRules : gameRulesList) {
                // 获取会员等级
                Integer userlevel = cardGameRules.getUserlevel();
                // 获取该会员可抽奖次数
                Integer enterTimes = cardGameRules.getEnterTimes();
                // 获取该会员最大中奖次数
                Integer goalTimes = cardGameRules.getGoalTimes();
                // 将会员信息存入redis
                redisUtil.hset(RedisKeys.MAXGOAL + gameId, userlevel.toString(), goalTimes);
                redisUtil.hset(RedisKeys.MAXENTER + gameId, userlevel.toString(), enterTimes);
            }
            // 4.3.抽奖令牌桶 双端队列 key:活动id Collection:从小到大右侧入列
            redisUtil.rightPushAll(RedisKeys.TOKENS + gameId, tokenList);
            // 4.4.奖品信息 k-v key:活动id value:奖品信息 (已在3.3.3.完成)
            //redisUtil.set(RedisKeys.TOKEN + gameId + "_" + token, productMap.get(productId), expire);
        }
    }
}
