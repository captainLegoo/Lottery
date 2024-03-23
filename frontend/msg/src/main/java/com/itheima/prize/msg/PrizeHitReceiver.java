package com.itheima.prize.msg;

import com.alibaba.fastjson.JSON;
import com.itheima.prize.commons.config.RabbitKeys;
import com.itheima.prize.commons.db.entity.CardUserHit;
import com.itheima.prize.commons.db.service.CardUserHitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@RabbitListener(queues = RabbitKeys.QUEUE_HIT)
public class PrizeHitReceiver {
    private final static Logger logger = LoggerFactory.getLogger(PrizeHitReceiver.class);

    @Autowired
    private CardUserHitService hitService;

    @RabbitHandler
    public void processMessage(String message) {
        logger.info("user hit : message={}", message);
        //解析消息
        CardUserHit cardUserHit = JSON.parseObject(message, CardUserHit.class);
        logger.info("user hit : cardUserHit={}", cardUserHit);
        // 保存用户中奖信息到数据库
        hitService.save(cardUserHit);
    }
}