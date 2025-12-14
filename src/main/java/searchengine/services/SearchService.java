package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.SearchResponse;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.parser.LemmaFinder;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private static final double MAX_LEMMA_FRACTION = 0.8;

    private final LemmaFinder lemmaFinder;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SiteRepository siteRepository;
    private final IndexRepository indexRepository;

    public SearchResponse search(String query, String site, int offset, int limit) {
        SiteEntity siteEntity = siteRepository.findSiteByUrl(site).orElseThrow();
        List<LemmaEntity> descSortedLemmas = getFilteredLemmas(query, siteEntity);
        List<PageEntity> crossPageList = getCrossPageList(descSortedLemmas);
        Map<PageEntity, Float> sortedMap = getSortedRelativeRelevance(crossPageList);

        return null;
    }

    public Map<PageEntity, Float> getSortedRelativeRelevance(List<PageEntity> crossPageList) {
        Map<PageEntity, Float> absRelMap = new HashMap<>();
        for (PageEntity page : crossPageList) {
            float absRel = indexRepository.findSumAllFrequenciesByPage(page);
            absRelMap.put(page, absRel);
        }
        float maxRel = Collections.max(absRelMap.values());
        Map<PageEntity, Float> relativeRelevance = new HashMap<>();
        absRelMap.forEach((page, rel) -> {
            float rr = rel / maxRel;
            relativeRelevance.put(page, rr);
        });
        return relativeRelevance.entrySet().stream()
                .sorted(Map.Entry.<PageEntity, Float>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue,
                        LinkedHashMap::new));
    }

    public List<PageEntity> getCrossPageList(List<LemmaEntity> descSortedLemmas) {
        if (descSortedLemmas.isEmpty()) {
            return List.of();
        }
        List<PageEntity> crossPageList = new ArrayList<>(indexRepository.findPagesByLemma(descSortedLemmas.get(0)));

        for (int i = 1; i < descSortedLemmas.size() & !crossPageList.isEmpty(); i++) {
            List<PageEntity> tempListPages = indexRepository.findPagesByLemma(descSortedLemmas.get(i));
            crossPageList.retainAll(tempListPages);
        }
        return crossPageList;
    }

    public List<LemmaEntity> getFilteredLemmas(String query, SiteEntity site) {

        Set<String> queryLemmas = lemmaFinder.collectLemmas(query).keySet();

        if (queryLemmas.isEmpty()) {
            return List.of();
        }
        long totalCountPages = (site != null) ? pageRepository.countBySite(site) : pageRepository.count();
        long maxAllowedFrequency = (long) (totalCountPages * MAX_LEMMA_FRACTION);

        if (totalCountPages == 0) {
            return List.of();
        }

        if (site != null) {
            return queryLemmas.stream()
                    .map(lemmaText -> lemmaRepository
                            .findLemmaByLemmaAndSite(lemmaText, site.getId())
                            .orElse(null)
                    )
                    .filter(Objects::nonNull)
                    .filter(lemma -> lemma.getFrequency() <= maxAllowedFrequency)
                    .sorted(Comparator.comparingInt(LemmaEntity::getFrequency))
                    .toList();
        } else {
            return queryLemmas.stream()
                    .map(lemmaRepository::findLemmaByLemma)
                    .filter(lemma -> lemma.getFrequency() <= maxAllowedFrequency)
                    .sorted(Comparator.comparingInt(LemmaEntity::getFrequency))
                    .toList();
        }

    }
}


