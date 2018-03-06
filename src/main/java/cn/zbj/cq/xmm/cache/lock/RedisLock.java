package cn.zbj.cq.xmm.cache.lock;

import cn.zbj.cq.xmm.cache.redis.RedisStringUtil;
import cn.zbj.cq.xmm.cache.util.CachePrefix;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by XiongMM on 2017/12/6.
 */
public class RedisLock {

    private static final int DEFAULT_ACQUIRY_RESOLUTION_MILLIS = 100;

    private String lockKey;

    /**
     * 锁超时时间，防止线程在入锁以后，无限的执行等待
     */
    private int expireMsecs = 60 * 1000;

    /**
     * 锁等待时间，防止线程饥饿
     */
    private int timeoutMsecs = 10 * 1000;

    /**
     * 当前锁的状态，所有线程可见
     */
    private volatile boolean locked = false;


    public RedisLock(String lockKey){
        this.lockKey = lockKey;
    }

    public RedisLock(String lockKey,int expireMsecs){
        this(lockKey);
        this.expireMsecs = expireMsecs;
    }

    public RedisLock(String lockKey,int expireMsecs,int timeoutMsecs){
        this(lockKey,expireMsecs);
        this.timeoutMsecs = timeoutMsecs;
    }

    public String getLockKey(){
        return lockKey;
    }

    public void releaseLock(){
        if(locked){
            RedisStringUtil.delete(CachePrefix.REDIS_DISTRIBUTED_LOCK,lockKey);
            locked = false;
        }
    }


    /**
     * 获取锁，成功则返回true，否则继续获取或者返回false
     * @return
     */
    public synchronized boolean hasLock() throws InterruptedException {
        int timeout = timeoutMsecs;
        while (timeout > 0){
            //获取当前的时间+失效时间，作为value
            long expires = System.currentTimeMillis() + expireMsecs + 1;
            //锁到期时间
            String expiresStr = String.valueOf(expires);
            //尝试写入，如果写入成功，则返回true，否则返回false
            if(RedisStringUtil.setnx(CachePrefix.REDIS_DISTRIBUTED_LOCK,lockKey,expiresStr)){
                //当前线程获取到了锁，并返回继续处理业务
                locked = true;
                return locked;
            }
            //否则，继续获取
            //获取redis里面当前时间
            String currentExpires = RedisStringUtil.get(CachePrefix.REDIS_DISTRIBUTED_LOCK,lockKey);
            //如果当前redis里面的锁的值小于当前时间，说明锁已经过期了
            if(StringUtils.isNoneBlank(currentExpires) && Long.parseLong(currentExpires) < System.currentTimeMillis()){
                //把新值放进去，并取出旧值.,只有一个线程才能获取上一个线上的设置时间，因为jedis.getSet是同步的
                //这个地方注意，假如有两个线程都进入了，那就会出现一个线程把另外一个线程的值给替换掉，但是他们的这个先后微乎其微，所以可以忽略不计
                String oldExpires = RedisStringUtil.getSet(CachePrefix.REDIS_DISTRIBUTED_LOCK,lockKey,expiresStr);
                //虽然会有替换，某一个线程肯定会获取到oldExpires并且与currentExpires相等。
                if(StringUtils.isNoneBlank(oldExpires) && StringUtils.equals(oldExpires,currentExpires)){
                    //防止误删（覆盖，因为key是相同的）了他人的锁——这里达不到效果，这里值会被覆盖，但是因为什么相差了很少的时间，所以可以接受
                    //[分布式的情况下]:如过这个时候，多个线程恰好都到了这里，但是只有一个线程的设置值和当前值相同，他才有权利获取锁
                    locked = true;
                    return locked;
                }
            }

            //走到这的线程都是没有获取到锁，进行下一轮的循环获取
            timeout -= DEFAULT_ACQUIRY_RESOLUTION_MILLIS;

            /**
             延迟100 毫秒,  这里使用随机时间可能会好一点,可以防止饥饿进程的出现,即,当同时到达多个进程,
             只会有一个进程获得锁,其他的都用同样的频率进行尝试,后面有来了一些进行,也以同样的频率申请锁,这将可能导致前面来的锁得不到满足.
             使用随机的等待时间可以一定程度上保证公平性
             */
            Thread.sleep(DEFAULT_ACQUIRY_RESOLUTION_MILLIS);


        }
        return false;
    }
}
