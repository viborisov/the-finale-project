package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingPageService {
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final PageRepository pageRepository;
    private final LemmaFinder lemmaFinder;

    @Transactional
    public void indexPage(String url, String html, int statusCode, SiteEntity site) {
        log.info("индексация и сбор лемм страницы {} началась", url);

        PageEntity pageEntity = new PageEntity();
        pageEntity.setSite(site);
        pageEntity.setPath(getUrl(url).getPath());
        pageEntity.setCode(statusCode);
        pageEntity.setContent(html);
        pageRepository.save(pageEntity);

        HashMap<String, Float> lemmaOnePage = lemmaFinder.collectLemmas(html);

        for (var entry : lemmaOnePage.entrySet()) {
            String textLemma = entry.getKey();
            Float rank = entry.getValue();

            LemmaEntity lemmaEntity = createLemma(textLemma, site);
            createIndex(lemmaEntity, pageEntity, rank);
        }
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
    @Transactional
    public LemmaEntity createLemma(String textLemma, SiteEntity currentSite) {
        LemmaEntity lemmaEntity = lemmaRepository.findLemmaByLemmaAndSite(textLemma, currentSite.getId())
                .orElseGet(() -> {
                    LemmaEntity newLemma = new LemmaEntity();
                    newLemma.setSite(currentSite);
                    newLemma.setLemma(textLemma);
                    newLemma.setFrequency(0);
                    return newLemma;
                });
        log.info("Before increment lemma {} freq={}", lemmaEntity.getLemma(), lemmaEntity.getFrequency());
        lemmaEntity.setFrequency(lemmaEntity.getFrequency() + 1);
        lemmaRepository.save(lemmaEntity);
        log.info("After increment lemma {} freq={}", lemmaEntity.getLemma(), lemmaEntity.getFrequency());
        return lemmaEntity;
    }
    @Transactional
    public void createIndex(LemmaEntity lemma, PageEntity page, Float rank) {
        IndexEntity indexEntity = new IndexEntity();
        indexEntity.setLemma(lemma);
        indexEntity.setPage(page);
        indexEntity.setRank(rank);
        indexRepository.save(indexEntity);
    }
}
