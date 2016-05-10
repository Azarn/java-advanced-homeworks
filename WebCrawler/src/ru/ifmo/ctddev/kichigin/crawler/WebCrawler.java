package ru.ifmo.ctddev.kichigin.crawler;

import info.kgeorgiy.java.advanced.crawler.*;

import javax.print.Doc;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;


class PhaserThreadPoolExecutor extends ThreadPoolExecutor {
    private final Phaser phaser;

    PhaserThreadPoolExecutor(Phaser phaser, int threadNumber) {
        super(threadNumber, threadNumber, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
        this.phaser = phaser;
    }

    public void execute(Runnable command) {
        phaser.register();
        super.execute(command);
    }

    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        phaser.arrive();
    }
}


/**
 * Simple web crawler, which supports parallel requests
 *
 * @author Created by azarn on 5/10/16.
 */
public class WebCrawler implements Crawler {
    private final Downloader downloader;
    private final int maxPerHost;
    private final ExecutorService downloadersPool;
    private final ExecutorService extractorsPool;
    private final Phaser phaser;
    private Map<String, Semaphore> hostLoad;
    private Set<String> urls;
    private Map<String, IOException> errors;

    /**
     * Creates a new instance of WebCrawler
     *
     * @param downloader {@link Downloader} to use for downloading pages
     * @param downloaders Maximum number of parallel downloads
     * @param extractors Maximum number of parallel extraction
     * @param perHost Maximum number of parallel downloads per host
     */
    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        this.downloader = downloader;
        maxPerHost = perHost;
        phaser = new Phaser(1);
        downloadersPool = new PhaserThreadPoolExecutor(phaser, downloaders);
        extractorsPool = new PhaserThreadPoolExecutor(phaser, extractors);
    }

    /**
     * Specific private function to download url and recursively download all its links while depth > 1
     *
     * @param url Specifies resource to download
     * @param currentDepth Stores the current depth of crawling
     */
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    private void downloadPart(String url, int currentDepth) {
        if (!urls.add(url)) {
            return;
        }

        String host;
        try {
            host = URLUtils.getHost(url);
        } catch (MalformedURLException e) {
            errors.put(url, e);
            return;
        }

        Semaphore hostSemaphore = hostLoad.computeIfAbsent(host, (s) -> new Semaphore(maxPerHost));
        try {
            hostSemaphore.acquire();
        } catch (InterruptedException ignored) {

        }

        Document doc;
        try {
            doc = downloader.download(url);
        } catch (IOException e) {
            errors.put(url, e);
            return;
        } finally {
            hostSemaphore.release();
        }

        if (currentDepth == 1) {
            return;
        }

        extractorsPool.submit(() -> {
            List<String> links;
            try {
                links = doc.extractLinks();
            } catch (IOException e) {
                errors.put(url, e);
                return;
            }

            links.forEach(link -> downloadersPool.submit(() -> downloadPart(link, currentDepth - 1)));
        });
    }

    /**
     * Download given site parallelly
     *
     * @param url Starting url of the site
     * @param depth Crawling depth [1 - only the given page, 2 - each link on the page, etc]
     * @return {@link Result} of crawling, containing loaded urls and errors
     */
    @Override
    public Result download(String url, int depth) {
        urls = ConcurrentHashMap.newKeySet();
        errors = new ConcurrentHashMap<>();
        hostLoad = new ConcurrentHashMap<>();

        downloadersPool.submit(() -> downloadPart(url, depth));
        phaser.arriveAndAwaitAdvance();

        urls.removeAll(errors.keySet());
        return new Result(new ArrayList<>(urls), errors);
    }

    /**
     * Stop crawling and closes all crawler's threads
     */
    @Override
    public void close() {
        downloadersPool.shutdownNow();
        extractorsPool.shutdownNow();
    }

    /**
     * Function to start web crawler from console, using {@link CachingDownloader} as downloader
     * This implementation explicitly sets depth to 2
     * Default number for downloads and extractors is {@link Runtime#availableProcessors()}
     * Default number for requests per host is {@link Runtime#availableProcessors()} * 2
     * @param args url [downloads [extractors [perHost]]]
     */
    public static void main(String[] args) {
        int procNumber = Runtime.getRuntime().availableProcessors();
        Integer downloads = null, extractors = null, perHost = null;
        switch (args.length) {
            case 4:
                try {
                    perHost = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    System.err.println("Incorrect perHost number");
                    return;
                }
            case 3:
                try {
                    extractors = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    System.err.println("Incorrect extractors number");
                    return;
                }
            case 2:
                try {
                    downloads = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    System.err.println("Incorrect downloads number");
                    return;
                }
            case 1:
                if (perHost == null) {
                    perHost = procNumber  * 2;
                }

                if (extractors == null) {
                    extractors = procNumber;
                }

                if (downloads == null) {
                    downloads = procNumber;
                }
                break;
            default:
                System.out.println("Invalid number of arguments");
                System.out.println("Usage: WebCrawler <url> [downloads [extractors [perHost]]]");
                return;
        }

        try (WebCrawler wc = new WebCrawler(new CachingDownloader(), downloads, extractors, perHost)) {
            wc.download(args[0], 2);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }
}