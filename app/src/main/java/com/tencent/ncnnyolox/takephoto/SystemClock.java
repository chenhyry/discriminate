package com.tencent.ncnnyolox.takephoto;


import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * * 系统时钟<br>
 *  * 高并发场景下System.currentTimeMillis()的性能问题的优化
 *  * System.currentTimeMillis()的调用比new一个普通对象要耗时的多（具体耗时高出多少我还没测试过，有人说是100倍左右）
 *  * System.currentTimeMillis()之所以慢是因为去跟系统打了一次交道
 *  * 后台定时更新时钟，JVM退出时，线程自动回收
 */
public class SystemClock {

   // 保证其原子性
   private final AtomicLong now;

   // 时间间隔
   private final int period;

   private SystemClock(int period) {
      this.now = new AtomicLong(System.currentTimeMillis());
      this.period = period;
      clockUpdate();
   }

   private static class InstanceHolder {
      private static SystemClock INSTANCE = new SystemClock(1);
   }

   private void clockUpdate() {
      ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(r -> {
         Thread t = new Thread(r, "SystemClock");
         // 主线程退出后，该线程也退出
         t.setDaemon(true);
         return t;
      });
      service.scheduleAtFixedRate(() -> now.set(System.currentTimeMillis()), period, period, TimeUnit.MILLISECONDS);
   }

   public static long now() {
      return InstanceHolder.INSTANCE.now.get();
   }

}