package ru.ifmo.ctddev.kichigin.concurrent;

import info.kgeorgiy.java.advanced.concurrent.ListIP;
import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.*;

/**
 * Provides implementation for {@link ListIP} interface.
 */
public class IterativeParallelism implements ListIP {
    private ParallelMapper pMapper = null;

    /**
     * Default constructor, does nothing
     */
    public IterativeParallelism() {
    }

    /**
     * Creates an instance of {@link IterativeParallelism}
     * @param parallelMapper {@link ParallelMapper} which will be used for concurrency
     */
    public IterativeParallelism(ParallelMapper parallelMapper) {
        pMapper = parallelMapper;
    }

    /**
     * Concatenates elements in the list into a string
     * @param threads number of threads to be used
     * @param list a list to be used
     * @return a string with each elements being concatenated
     * @throws InterruptedException if any of threads was interrupted
     */
    @Override
    public String join(int threads, List<?> list) throws InterruptedException {
        return parWork(threads, list, StringBuilder::new,
                (acc, elem) -> {
                    acc.append(elem.toString());
                    return acc;
                }, (acc, elem) -> {
                    acc.append(elem);
                    return acc;
                }).toString();
    }

    /**
     * Filters elements in the list, using given predicate, returning a list with elements at which predicate was true
     * @param threads number of threads to be used
     * @param list a list to be used
     * @param predicate a predicate to test each element
     * @param <T> determines the type of elements in the given list
     * @return list of results with elements at which predicate was true
     * @throws InterruptedException if any of threads was interrupted
     */
    @Override
    public <T> List<T> filter(int threads, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return parWork(threads, list, ArrayList<T>::new,
                (acc, elem) -> {
                    if (predicate.test(elem)) {
                        acc.add(elem);
                    }
                    return acc;
                }, listFold());
    }

    /**
     * Maps a function to all elements in the list, returning list with that function results
     * @param threads number of threads to be used
     * @param list a list to be used
     * @param function a function to be applied to each element
     * @param <T> determines the type of elements in the given list
     * @param <U> determines the type of elements in the result list (result of a function)
     * @return list of results after function has been applied to all elements
     * @throws InterruptedException if any of threads was interrupted
     */
    @Override
    public <T, U> List<U> map(int threads, List<? extends T> list, Function<? super T, ? extends U> function) throws InterruptedException {
        return parWork(threads, list, ArrayList<U>::new,
                (acc, elem) -> {
                    acc.add(function.apply(elem));
                    return acc;
                }, listFold());
    }

    /**
     * Finds maximum element in the list
     * @param threads number of threads to be used
     * @param list a list to be used
     * @param comparator a comparator to test maximum element
     * @param <T> determines the type of elements in the list
     * @return maximum value in the list (or {@code null} if list was empty})
     * @throws InterruptedException if any of threads was interrupted
     */
    @Override
    public <T> T maximum(int threads, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        if (list.isEmpty()) {
            return null;
        } else {
            BinaryOperator<T> maxOp = (a, b) -> comparator.compare(a, b) < 0 ? b : a;
            return parWork(threads, list, () -> list.get(0), maxOp, maxOp);
        }
    }

    /**
     * Finds minimum element in the list
     * @param threads number of threads to be used
     * @param list a list to be used
     * @param comparator a comparator to test maximum element
     * @param <T> determines the type of elements in the list
     * @return minimum value in the list (or {@code null} if list was empty})
     * @throws InterruptedException if any of threads was interrupted
     */
    @Override
    public <T> T minimum(int threads, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        return maximum(threads, list, comparator.reversed());
    }

    /**
     * Tests all elements in the list, using specified predicate and tells if all of them satisfy that predicate
     * @param threads number of threads to be used
     * @param list a list to be used
     * @param predicate a predicate to be tested with
     * @param <T> determines the type of elements in the list
     * @return {@code true} if all elements satisfy predicate or {@code false} otherwise
     * @throws InterruptedException if any of threads was interrupted
     */
    @Override
    public <T> boolean all(int threads, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return parWork(threads, list, () -> true, (acc, elem) -> acc && predicate.test(elem), (a, b) -> a && b);
    }

    /**
     * Tests all elements in the list, using specified predicate and tells if any of them satisfies that predicate
     * @param threads number of threads to be used
     * @param list a list to be used
     * @param predicate a predicate to be tested with
     * @param <T> determines the type of elements in the list
     * @return {@code true} if any element satisfy predicate or {@code false} otherwise
     * @throws InterruptedException if any of threads was interrupted
     */
    @Override
    public <T> boolean any(int threads, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return !all(threads, list, predicate.negate());
    }

    private <T, U> U parWork(int threads, List<? extends T> list,
                             Supplier<U> firstElem, BiFunction<U, ? super T, U> fold,
                             BinaryOperator<U> ansFold)  throws InterruptedException {
        List<U> ansList;
        if (pMapper == null) {
            ansList = new ArrayList<>(Collections.nCopies(threads, null));
            Thread[] parThreads = new Thread[threads - 1];  // One for current thread
            Function<Integer, Runnable> parFold = i -> (() -> ansList.set(i,
                    chunkList(list, i, threads).stream().reduce(firstElem.get(), fold, ansFold)));

            for (int i = 1; i < threads; ++i) {
                parThreads[i - 1] = new Thread(parFold.apply(i));
                parThreads[i - 1].start();
            }

            parFold.apply(0).run();
            for (int i = 1; i < threads; ++i) {
                parThreads[i - 1].join();
            }
        } else {
            List<List<? extends T>> argList = new ArrayList<>(Collections.nCopies(threads, null));
            for (int i = 0; i < threads; ++i) {
                argList.set(i, chunkList(list, i, threads));
            }
            ansList = pMapper.map((l) -> l.stream().reduce(firstElem.get(), fold, ansFold), argList);
        }

        return ansList.stream().reduce(firstElem.get(), ansFold);
    }

    private static <T> List<T> chunkList(List<T> list, int chunkNum, int threads) {
        int chunkStart = (int) ((long) list.size() * chunkNum / threads);
        int chunkEnd = (int) ((long) list.size() * (chunkNum + 1) / threads);
        return list.subList(chunkStart, chunkEnd);
    }

    private static <T> BinaryOperator<List<T>> listFold() {
        return (a, b) -> {
            a.addAll(b);
            return a;
        };
    }
}
