package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.exceptions.ReadingException;
import searchengine.exceptions.ThreadException;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.parser.HtmlParser;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sitesList;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private volatile ExecutorService executorService;
    private volatile ForkJoinPool forkJoinPool;


    @Override
    public IndexingResponse startIndexing() {

        IndexingResponse response = new IndexingResponse();

        if (!isIndexing.compareAndSet(false, true)) {
            response.setResult(false);
            response.setError("Индексация уже запущена");
            return response;
        }

        stopRequested.set(false);
        response.setResult(true);

        List<Site> sites = sitesList.getSites();
        List<String> urls = sites.stream().map(Site::getUrl).toList();

        pageRepository.deletePageBySiteId(siteRepository.findByUrlSiteId(urls));
        siteRepository.deleteByUrls(urls);

        executorService = Executors.newFixedThreadPool(3);
        forkJoinPool = new ForkJoinPool();

        executorService.execute(() -> {
            try {
                List<CompletableFuture<Void>> futures = sites.stream()
                        .map(site -> CompletableFuture.runAsync(() -> {
                            log.info("Началась индексация сайта {}", site.getUrl());
                            createSite(site.getUrl(), site.getName());
                            indexPage(site.getUrl());
                        }, executorService))
                                .toList();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                isIndexing.set(false);
            } catch (Exception e) {
                shutdownExecutors();
                isIndexing.set(false);
            }
        });
        return response;
    }

    @Override
    public IndexingResponse stopIndexing() {
        IndexingResponse response = new IndexingResponse();
        if (!isIndexing.get()) {
            response.setResult(false);
            response.setError("Индексация не запущена");
            return response;
        }
        stopRequested.set(true);
        response.setResult(true);
        shutdownExecutors();
        isIndexing.set(false);
        return response;
    }


    public void createSite(String url, String name) {
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setStatus(Status.INDEXING);
        siteEntity.setLastError("");
        siteEntity.setUrl(url);
        siteEntity.setName(name);
        siteRepository.save(siteEntity);
    }

    private void indexPage(String url) {
        SiteEntity site = siteRepository.findSiteByUrl(url);
        Set<String> visitedUrl = ConcurrentHashMap.newKeySet();
        try {
            Set<PageEntity> pages = forkJoinPool.invoke(new HtmlParser(url, pageRepository, siteRepository, visitedUrl, stopRequested));
            savePagesBatch(pages.stream().toList(), 50);
            site.setStatus(Status.INDEXED);
        } catch (ReadingException | ThreadException e) {
            site.setStatus(Status.FAILED);
            site.setLastError(e.getMessage());
            log.info("Индексация для сайта {} прекратилась, статусом {}", url, site.getStatus());
        } finally {
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
    }


    public void savePagesBatch(List<PageEntity> pageEntities, int batchSize) {
        List<PageEntity> batchList = new ArrayList<>(pageEntities);
        int total = batchList.size();

        for (int i = 0; i < total; i += batchSize) {
            int end = Math.min(i + batchSize, total);
            List<PageEntity> subList = batchList.subList(i, end);
            try {
                pageRepository.saveAll(subList);
            } catch (DataIntegrityViolationException e) {
                log.error("Одна или несколько страниц в батче уже сохранены");
            }
            pageRepository.flush();
        }
    }

    public void shutdownExecutors() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
        if (forkJoinPool != null && !forkJoinPool.isShutdown()) {
            forkJoinPool.shutdownNow();
        }
    }
}
