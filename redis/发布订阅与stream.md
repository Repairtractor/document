# 发布订阅与stream
> redis自带的Pub/sub模式和改进之后的stream模型都可以是先消息队列的形式

## Pus/Sub发布订阅模式
> 发送者通过 PUBLISH发布消息，订阅者通过 SUBSCRIBE 订阅接收消息或通过UNSUBSCRIBE 取消订阅。

发布订阅模式并没有利用某个数据结构，而是redis自己底层代码单独实现的，自身保持了一个字典map，key为topic，value是一个链表，存储订阅客户端的通信地址，当有一个消息被传递时，redis服务端从map中查找所有订阅的客户端，通知改客户端

```java
    public void publish(String topic, String message) {
        //发布消息
        long count = redisson.getTopic(topic).publish(message);
        System.out.println("向topic为" + topic + "的频道发布了一条消息，消息内容为：" + message + "，该消息被" + count + "个订阅者接收到");
    }
    //接收消息，
    redissonClient.getTopic(topic)
                .addListener(String.class, (channel, msg) -> {
                    System.out.println("收到来自"+channel+"的消息：" + msg);
                });
```
