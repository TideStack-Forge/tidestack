package com.ouroboros.lock;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁接口，表示一把具体的锁实例。
 */
public interface DistributedLock {

    /**
     * 阻塞获取锁，默认 30 秒超时自动释放
     */
    void lock();

    /**
     * 阻塞获取锁，指定持有超时后自动释放
     *
     * @param leaseTime 持有超时时间
     * @param unit      时间单位
     */
    void lock(long leaseTime, TimeUnit unit);

    /**
     * 尝试获取锁，立即返回
     *
     * @return 是否获取成功
     */
    boolean tryLock();

    /**
     * 尝试获取锁，等待指定时间
     *
     * @param waitTime  最大等待时间
     * @param leaseTime 持有超时（自动释放），-1 表示使用默认值
     * @param unit      时间单位
     * @return 是否获取成功
     * @throws InterruptedException 等待过程中被中断
     */
    boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;

    /**
     * 释放锁
     */
    void unlock();

    /**
     * 是否被任意线程持有
     *
     * @return 是否被持有
     */
    boolean isLocked();
}
