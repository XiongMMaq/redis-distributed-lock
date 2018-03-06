package cn.zbj.cq.xmm.cache.lock;

import cn.zbj.cq.xmm.cache.redis.RedisStringUtil;
import cn.zbj.cq.xmm.cache.util.CachePrefix;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.*;

/**
 * Created by XiongMM on 2017/12/6.
 */
public class TestRedisLock {

    private static final int THREAD_NUM=10;
    public static CountDownLatch latch = new CountDownLatch(THREAD_NUM);
    public static void main(String[] args){
        //先设置一个共享资源
        RedisStringUtil.set(CachePrefix.REDIS_DISTRIBUTED_LOCK,"COUNT","10");
        //阿里推荐使用的线程池框架方式，不建议使用Excutors框架
        ThreadFactory nameThreadFactory = new ThreadFactoryBuilder().setNameFormat("demo-pool-%d").build();
        ExecutorService pool = new ThreadPoolExecutor(THREAD_NUM,200,0L, TimeUnit.MILLISECONDS
                ,new LinkedBlockingDeque<Runnable>(1024),nameThreadFactory,new ThreadPoolExecutor.AbortPolicy());
        for(int i=0;i<THREAD_NUM;i++) {
            //线程安全
            pool.execute(new Task(latch));
            //非线程安全
            //pool.execute(new Task1(latch));
        }
        try {
            latch.await();
            System.out.println("所有线程执行结束～");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            pool.shutdown();
        }
    }

    static class Task implements Runnable{

        private CountDownLatch latch;

        public Task(CountDownLatch latch){
            this.latch = latch;
        }
        @Override
        public void run() {
            RedisLock redisLock = new RedisLock("TIMEOUT");
            try {
               if (redisLock.hasLock()) {
                    String count = RedisStringUtil.get(CachePrefix.REDIS_DISTRIBUTED_LOCK,"COUNT");
                    int tmpCount = Integer.parseInt(count);
                    tmpCount = tmpCount - 1;
                    RedisStringUtil.set(CachePrefix.REDIS_DISTRIBUTED_LOCK,"COUNT",tmpCount+"");
                    System.out.println("当前线程："+Thread.currentThread().getName() + " 处理业务！tmpCount:"+tmpCount);
                }
            }catch (InterruptedException ex){
                ex.printStackTrace();
            }finally {
                redisLock.releaseLock();
                this.latch.countDown();
            }
        }
    }



    static class Task1 implements Runnable{

        private CountDownLatch latch;

        public Task1(CountDownLatch latch){
            this.latch = latch;
        }
        @Override
        public void run() {
            //多线程并发
            try {
                if (true) {
                    String count = RedisStringUtil.get(CachePrefix.REDIS_DISTRIBUTED_LOCK,"COUNT");
                    int tmpCount = Integer.parseInt(count);
                    tmpCount = tmpCount - 1;
                    RedisStringUtil.set(CachePrefix.REDIS_DISTRIBUTED_LOCK,"COUNT",tmpCount+"");
                    System.out.println("当前线程："+Thread.currentThread().getName() + " 处理业务！tmpCount:"+tmpCount);
                }
            } finally {
                this.latch.countDown();
            }
        }
    }
}

