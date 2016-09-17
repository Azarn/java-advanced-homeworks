package ru.ifmo.ctddev.kichigin.concurrent;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;

/**
 * Class to be used for concurrent programming
 */
public class ParallelMapperImpl implements ParallelMapper {
    private final Thread[] threads;

    private final Queue<Runnable> queue = new LinkedList<>();

    private volatile int freeThreads = 0;
    private final Object freeThreadsSync = new Object();

    /**
     * Creates instance if {@link info.kgeorgiy.java.advanced.mapper.ParallelMapper}
     * @param threadNum a number of threads which will be used at {@link #map(Function, List)}
     */
    public ParallelMapperImpl(int threadNum) {
        threads = new Thread[threadNum];

        for (int i = 0; i < threads.length; ++i) {
            threads[i] = new Thread(() -> {
                try {
                    while (!Thread.interrupted()) {
                        Runnable r;

                        synchronized (freeThreadsSync) {
                            ++freeThreads;
                            freeThreadsSync.notify();
                        }

                        synchronized (queue) {
                            while (queue.isEmpty()) {
                                queue.wait();
                            }
                            r = queue.remove();
                        }
                        r.run();
                    }
                } catch (InterruptedException e) {
                    // Thread interrupted
                } finally {
                    Thread.currentThread().interrupt();
                }
            });
            threads[i].start();
        }
    }

    /**
     * Closes all created threads
     * @throws InterruptedException if main thread was interrupted during waiting for other threads interruption
     */
    @Override
    public void close() throws InterruptedException {
        Arrays.stream(threads).forEach(Thread::interrupt);
        for (Thread t: threads) {
            t.join();
        }
    }

    /**
     * Calculates function on each element in the list parallelic
     * @param function a function to be applied to each element
     * @param list a list to be used
     * @param <T> determines the type of elements in the given list
     * @param <R> determines the type of elements in the resulting list
     * @return list of results after function has been applied to all elements
     * @throws InterruptedException if {@link #close()} was used during execution or any thread was interrupted
     */
    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> function, List<? extends T> list)
            throws InterruptedException {
        ArrayList<R> resList = new ArrayList<>(Collections.nCopies(list.size(), null));
        final TaskInfo taskInfo = new TaskInfo();

        for (int i = 0; i < list.size(); ++i) {
            final int elementId = i;

            synchronized (freeThreadsSync) {
                while (freeThreads == 0) {
                    freeThreadsSync.wait();
                }
                --freeThreads;
            }

            synchronized (queue) {
                queue.add(() -> {
                    resList.set(elementId, function.apply(list.get(elementId)));

                    // Increase counter after each element proceeded
                    synchronized (taskInfo) {
                        if (++taskInfo.readyCount == list.size()) {
                            taskInfo.notify();
                        }
                    }
                });
                queue.notify();
            }
        }

        // Waiting for all objects to be finished
        synchronized (taskInfo) {
            while (taskInfo.readyCount < list.size()) {
                taskInfo.wait();
            }
        }

        return resList;
    }

    private static class TaskInfo {
        private volatile int readyCount = 0;
    }
}
