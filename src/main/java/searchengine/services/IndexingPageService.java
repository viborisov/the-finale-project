package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.parser.LemmaFinder;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class IndexingPageService {
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final PageRepository pageRepository;
    private final LemmaFinder lemmaFinder;
    private final SitesList sitesList;


    public void indexPage(String url, String html, int statusCode, SiteEntity site) {
        if (!checkSiteUrl(url)) {
            log.error("Данная страница {} находится за пределами сайтов, указанных в конфигурационном файле", url);
        }

        log.info("индексация и сбор лемм страницы {} началась", url);

        PageEntity currentPage = getPageByUrl(url, site);
        if (currentPage != null) {
            deletePageInfo(currentPage);
            log.info("Отчистили таблицы lemma, index, page");
        }

        PageEntity pageEntity = new PageEntity();
        pageEntity.setSite(site);
        pageEntity.setPath(getUrl(url).getPath());
        pageEntity.setCode(statusCode);
        pageEntity.setContent(html);
        pageRepository.save(pageEntity);

        HashMap<String, Float> lemmaOnePage = lemmaFinder.collectLemmas(url, html);

        for (var entry : lemmaOnePage.entrySet()) {
            String textLemma = entry.getKey();
            Float rank = entry.getValue();

            LemmaEntity lemmaEntity = createLemma(textLemma, site);
            createIndex(lemmaEntity, pageEntity, rank);
        }
    }

    public PageEntity getPageByUrl(String url,SiteEntity site) {
        String path = getUrl(url).getPath();
        return pageRepository.findPageByPathAndSite(path, site);
    }

    public void deletePageInfo(PageEntity page) {
        List<IndexEntity> indexes = indexRepository.findAllIndexByPageId(page.getId());
        for (IndexEntity index : indexes) {
            LemmaEntity lemmaEntity = index.getLemma();

            if (lemmaEntity == null) continue;

            int newFreq = lemmaEntity.getFrequency() - 1;
            if (newFreq <= 0) {
                lemmaRepository.delete(lemmaEntity);
            } else {
                lemmaEntity.setFrequency(newFreq);
                lemmaRepository.save(lemmaEntity);
            }
        }
        indexRepository.deleteAll(indexes);
        pageRepository.delete(page);
    }

//    public SiteEntity findOrCreateSiteByUrl(String url) {
//        String domain = getUrl(url).getHost();
//        String protocol = getUrl(url).getProtocol();
//        return siteRepository.findSiteByUrl(protocol + "://" + domain + "/")
//                .orElseGet(() -> {
//                    SiteEntity site = new SiteEntity();
//                    site.setUrl(protocol + "://" + domain + "/");
//                    site.setName(domain);
//                    site.setStatus(Status.INDEXED);
//                    site.setLastError("");
//                    site.setStatusTime(LocalDateTime.now());
//                    siteRepository.save(site);
//                    return site;
//                });
//    }

    public boolean checkSiteUrl(String url) {
        String domain = getUrl(url).getHost();
        return sitesList.getSites().stream()
                .map(Site::getUrl)
                .anyMatch(urls ->urls.contains(domain));
    }

    public URL getUrl(String url) {
        URL currentUrl = null;
        try {
            currentUrl = new URL(url);
        } catch (MalformedURLException e) {
            System.err.println("Некорректный URL: " + e.getMessage());
        }
        return currentUrl;
    }

    public LemmaEntity createLemma(String textLemma, SiteEntity currentSite) {
        LemmaEntity lemmaEntity = lemmaRepository.findLemmaByLemmaAndSite(textLemma, currentSite.getId())
                .orElseGet(() -> {
                    LemmaEntity newLemma = new LemmaEntity();
                    newLemma.setSite(currentSite);
                    newLemma.setLemma(textLemma);
                    newLemma.setFrequency(0);
                    lemmaRepository.save(newLemma);
                    return newLemma;
                });
        log.info("Before increment lemma {} freq={}", lemmaEntity.getLemma(), lemmaEntity.getFrequency());
        lemmaEntity.setFrequency(lemmaEntity.getFrequency() + 1);
        lemmaRepository.saveAndFlush(lemmaEntity);
        log.info("After increment lemma {} freq={}", lemmaEntity.getLemma(), lemmaEntity.getFrequency());
        return lemmaEntity;
    }

    public void createIndex(LemmaEntity lemma, PageEntity page, Float rank) {
        IndexEntity indexEntity = new IndexEntity();
        indexEntity.setLemma(lemma);
        indexEntity.setPage(page);
        indexEntity.setRank(rank);
        indexRepository.save(indexEntity);
    }
}
