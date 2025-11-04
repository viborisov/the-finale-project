package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Http;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.exceptions.ReadingException;
import searchengine.exceptions.ThreadException;
import searchengine.model.*;
import searchengine.parser.HtmlParser;
import searchengine.parser.LemmaFinder;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
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
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private final Http http;
    private final LemmaFinder lemmaFinder;
    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    public static AtomicBoolean stopRequested = new AtomicBoolean(false);
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

        shutdownExecutors();
        stopRequested.set(false);
        response.setResult(true);
        log.info("Запуск индексации");

        List<Site> sites = sitesList.getSites();
        List<String> urls = sites.stream().map(Site::getUrl).toList();

        pageRepository.deletePageBySiteId(siteRepository.findByUrlSiteId(urls));
        siteRepository.deleteByUrls(urls);

        executorService = Executors.newFixedThreadPool(4);
        forkJoinPool = new ForkJoinPool();

        executorService.execute(() -> {
            try {
                List<CompletableFuture<Void>> futures = sites.stream()
                        .map(site -> CompletableFuture.runAsync(() -> {
                            log.info("Началась индексация сайта {}", site.getUrl());
                            createSite(site.getUrl(), site.getName());
                            indexingPage(site.getUrl());
                        }, executorService))
                                .toList();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                log.info("Индексация завершена");
            } catch (Exception e) {
                log.error("Ошибка во время индексации");
            } finally {
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
        log.warn("Запрошена остановка индексации");
        shutdownExecutors();

        siteRepository.findAll().forEach(site -> {
            if (site.getStatus() == Status.INDEXING) {
                site.setStatus(Status.FAILED);
                site.setLastError("Индексация прервана пользователем");
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
            }
        });
        response.setResult(true);
        isIndexing.set(false);
        log.info("Индексация успешно остановлена");
        return response;
    }

    @Override
    public IndexingResponse indexPage(String url) {
        IndexingResponse indexingResponse = new IndexingResponse();

        if (!checkSiteUrl(url)) {
            indexingResponse.setResult(false);
            indexingResponse.setError("\"Данная страница находится за пределами сайтов,\n" +
                    " указанных в конфигурационном файле");
            return indexingResponse;
        }

        log.info("индексация и сбор лемм страницы {} началась", url);
        SiteEntity currentSite = findOrCreateSiteByUrl(url);
        PageEntity currentPage = getPageByUrl(url, currentSite);
        if (currentPage != null) {
            deletePageInfo(currentPage);
            log.info("Отчистили таблицы lemma, index, page");
        }

        Connection.Response response = lemmaFinder.getResponse(url);
        String currentHtml = response.body();
        Integer statusCode = response.statusCode();

        PageEntity pageEntity = new PageEntity();
        pageEntity.setSite(currentSite);
        pageEntity.setPath(getUrl(url).getPath());
        pageEntity.setCode(statusCode);
        pageEntity.setContent(currentHtml);
        pageRepository.save(pageEntity);

        HashMap<String, Float> lemmaOnePage = lemmaFinder.collectLemmas(url);
        for (Map.Entry<String, Float> entry : lemmaOnePage.entrySet()) {
            String textLemma = entry.getKey();
            Float rank = entry.getValue();

            LemmaEntity lemmaEntity = createLemma(textLemma, currentSite);
            createIndex(lemmaEntity, pageEntity, rank);
        }
        log.info("Индексация страницы {} завершена", url);
        indexingResponse.setResult(true);
        return indexingResponse;
    }

    @Override
    public IndexingResponse deleteAllDataInBD() {
        indexRepository.deleteAll();
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();
        stopRequested.set(false);
        isIndexing.set(false);
        IndexingResponse indexingResponse = new IndexingResponse();
        indexingResponse.setResult(true);
        return indexingResponse;
    }

    @Transactional
    private void deletePageInfo(PageEntity page) {
        List<IndexEntity> indexes = indexRepository.findAllIndexByPageId(page.getId());
        for (IndexEntity index : indexes) {
            LemmaEntity lemmaEntity = index.getLemma();
            lemmaEntity.setFrequency(lemmaEntity.getFrequency() - 1);
            if (lemmaEntity.getFrequency() <= 0) {
                lemmaRepository.delete(lemmaEntity);
            } else {
                lemmaRepository.save(lemmaEntity);
            }
        }
        indexRepository.deleteAll(indexes);
        pageRepository.delete(page);
    }

    private SiteEntity findOrCreateSiteByUrl(String url) {
        String domain = getUrl(url).getHost();
        String protocol = getUrl(url).getProtocol();
        return siteRepository.findSiteByUrl(protocol + "://" + domain)
                .orElseGet(() -> {
                    SiteEntity site = new SiteEntity();
                    site.setUrl(protocol + "://" + domain);
                    site.setName(domain);
                    site.setStatus(Status.INDEXED);
                    site.setLastError("");
                    site.setStatusTime(LocalDateTime.now());
                    siteRepository.save(site);
                    return site;
                });
    }

    private LemmaEntity createLemma(String textLemma, SiteEntity currentSite) {
        LemmaEntity lemmaEntity = lemmaRepository.findLemmaByLemmaAndSite(textLemma, currentSite)
                .orElseGet(() -> {
                    LemmaEntity newLemma = new LemmaEntity();
                    newLemma.setSite(currentSite);
                    newLemma.setLemma(textLemma);
                    newLemma.setFrequency(0);
                    lemmaRepository.save(newLemma);
                    return newLemma;
                });
        lemmaEntity.setFrequency(lemmaEntity.getFrequency() + 1);
        lemmaRepository.save(lemmaEntity);
        return lemmaEntity;
    }

    private void createIndex(LemmaEntity lemma, PageEntity page, Float rank) {
        IndexEntity indexEntity = new IndexEntity();
        indexEntity.setLemma(lemma);
        indexEntity.setPage(page);
        indexEntity.setRank(rank);
        indexRepository.save(indexEntity);
    }

    private URL getUrl(String url) {
        URL currentUrl = null;
        try {
            currentUrl = new URL(url);
        } catch (MalformedURLException e) {
            System.err.println("Некорректный URL: " + e.getMessage());
        }
        return currentUrl;
    }

    private boolean checkSiteUrl(String url) {
        String domain = getUrl(url).getProtocol();
        return sitesList.getSites().stream()
                .map(Site::getUrl)
                .anyMatch(urls ->urls.contains(domain));
    }

    private PageEntity getPageByUrl(String url,SiteEntity site) {
        String path = getUrl(url).getPath();
        return pageRepository.findPageByPathAndSite(path, site);
    }

    private SiteEntity createSite(String url, String name) {
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setStatus(Status.INDEXING);
        siteEntity.setLastError("");
        siteEntity.setUrl(url);
        siteEntity.setName(name);
        siteRepository.save(siteEntity);
        return siteEntity;
    }

    private void indexingPage(String url) {
        SiteEntity site = siteRepository.findSiteByUrl(url).get();
        Set<String> visitedUrl = ConcurrentHashMap.newKeySet();
        try {
            checkStopped();
            Set<PageEntity> pages = forkJoinPool.invoke(new HtmlParser(url, pageRepository, siteRepository, visitedUrl, http));
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

    private void checkStopped() {
        if (stopRequested.get() || Thread.currentThread().isInterrupted()) {
            throw new ThreadException("Индексация прервана пользователем");
        }
    }

    private void savePagesBatch(List<PageEntity> pageEntities, int batchSize) {
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
            checkStopped();
        }
    }

    private void shutdownExecutors() {
        try {
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.warn("executorService не завершился вовремя");
                }
            }
            if (forkJoinPool != null && !forkJoinPool.isShutdown()) {
                forkJoinPool.shutdownNow();
                if (!forkJoinPool.awaitTermination(3, TimeUnit.SECONDS)) {
                    log.warn("forkJoinPool не завершился вовремя");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Ожидание завершения потоков прервано");
        }
    }
}
