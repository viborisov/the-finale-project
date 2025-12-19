package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.Item;
import searchengine.dto.statistics.SearchResponse;
import searchengine.exceptions.ReadingException;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.parser.LemmaFinder;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {
    private static final int MIN_LENGTH_TOKEN = 3;
    private static final int SNIPPET_RADIUS = 120;
    private static final double MAX_LEMMA_FRACTION = 0.8;

    private final LemmaFinder lemmaFinder;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SiteRepository siteRepository;
    private final IndexRepository indexRepository;

    public SearchResponse search(String query, String site, int offset, int limit) {
        SearchResponse searchResponse = new SearchResponse();
        List<Item> items = new ArrayList<>();
        if (query.isBlank()) {
            searchResponse.setResult(false);
            searchResponse.setError("Задан пустой поисковый запрос");
            searchResponse.setData(List.of());
            searchResponse.setCount(0);
            return searchResponse;
        }

        if (site != null && !site.isBlank()) {
            SiteEntity siteEntity = siteRepository.findSiteByUrl(site).orElseThrow(() -> new ReadingException("Такой сайт еще не проиндексирован"));
            items.addAll(getListItems(query,siteEntity));
        } else {
            List<SiteEntity> entityList = siteRepository.findAll();
            for (SiteEntity s : entityList) {
                items.addAll(getListItems(query, s));
            }
        }
        if (items.isEmpty()) {
            searchResponse.setCount(0);
            searchResponse.setData(List.of());
            searchResponse.setResult(true);
            searchResponse.setError("Ничего не найдено");
            return searchResponse;
        }
        items.sort(Comparator.comparing(Item::getRelevance).reversed());
        return makeLimitOrOffset(offset, limit, items);
    }

    public List<Item> getListItems(String query, SiteEntity site) {
        List<LemmaEntity> descSortedLemmas = getFilteredLemmasOnSite(query, site);
        if (descSortedLemmas.isEmpty()) {
            return List.of();
        }
        List<PageEntity> crossPageList = getCrossPageList(descSortedLemmas);
        if (crossPageList.isEmpty()) {
            return List.of();
        }
        Map<PageEntity, Float> sortedMap = getSortedRelativeRelevance(crossPageList);
        List<Item> items = new ArrayList<>();

        for (Map.Entry<PageEntity, Float> entrySet : sortedMap.entrySet()) {
            PageEntity page = entrySet.getKey();
            float relevance = entrySet.getValue();
            Item item = new Item();
            item.setSite(site.getUrl());
            item.setSiteName(site.getName());
            item.setUri(page.getPath());
            item.setTitle(getTitle(page.getContent()));
            item.setSnippet(buildSnippet(page.getContent(), query));
            item.setRelevance(relevance);
            items.add(item);
        }
        return items;
    }

    public SearchResponse makeLimitOrOffset(int offset, int limit, List<Item> items) {
        SearchResponse searchResponse = new SearchResponse();
        int total = items.size();

        if (offset < 0 || limit <= 0) {
            searchResponse.setResult(false);
            searchResponse.setData(List.of());
            searchResponse.setError("Некорректные значения offset или limit");
            searchResponse.setCount(total);
            return searchResponse;
        }

        if (offset >= total) {
            searchResponse.setCount(total);
            searchResponse.setResult(true);
            searchResponse.setData(List.of());
            return searchResponse;
        }

        int toIndex = Math.min(total, offset + limit);
        List<Item> resultList = items.subList(offset, toIndex);

        searchResponse.setResult(true);
        searchResponse.setData(resultList);
        searchResponse.setCount(total);
        return searchResponse;
    }

    public String buildSnippet(String html, String query) {
        String text = Jsoup.parse(html).text();
        if (text.isBlank()) {
            return "";
        }

        Set<String> queryWords = Arrays.stream(query.split("\\s+"))
                .map(String::strip)
                .filter(token -> token.length() >= MIN_LENGTH_TOKEN)
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (queryWords.isEmpty()) {
            return cutRawSnippet(text, 0);
        }

        String lowerText = text.toLowerCase();
        int centerIndex = Integer.MAX_VALUE;

        for (String word : queryWords) {
            int idx = lowerText.indexOf(word);
            if (idx >= 0 && idx < centerIndex) {
                centerIndex = idx;
            }
        }

        if (centerIndex == Integer.MAX_VALUE) {
            return cutRawSnippet(text, 0);
        }
        String rawSnippet = cutRawSnippet(text, centerIndex);

        return highlightWords(rawSnippet, queryWords);
    }

    private String highlightWords(String rawSnippet, Set<String> queryWords) {
        String result = rawSnippet;

        Set<String> sortedSet = queryWords.stream()
                .sorted(Comparator.comparing(String::length).reversed())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (String word : sortedSet) {
            String pattern = "(?iu)\\b" + Pattern.quote(word) + "\\b";
            result = result.replaceAll(pattern, "<b>$0</b>");
        }
        return result;
    }

    public String cutRawSnippet(String text, int centerIndex) {
        int length = text.length();
        int start;

        if (centerIndex == 0) {
            start = 0;
        } else {
            start = Math.max(0, centerIndex - SNIPPET_RADIUS);
        }
        int end = Math.min(length, centerIndex + SNIPPET_RADIUS);

        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }
        while (end < length && !Character.isWhitespace(text.charAt(end - 1))) {
            end++;
        }

        String rawSnippet = text.substring(start, end).trim();
        StringBuilder result = new StringBuilder();

        if (start > 0) {
            result.append("...");
        }
        result.append(rawSnippet);
        if (end < length) {
            result.append("...");
        }

        return result.toString();
    }

    public String getTitle(String html) {
        return Jsoup.parse(html).select("title").text();
    }

    public Map<PageEntity, Float> getSortedRelativeRelevance(List<PageEntity> crossPageList) {
        Map<PageEntity, Float> absRelMap = new HashMap<>();
        for (PageEntity page : crossPageList) {
            float absRel = indexRepository.findSumAllFrequenciesByPage(page);
            absRelMap.put(page, absRel);
        }
        float maxRel = Collections.max(absRelMap.values());
        Map<PageEntity, Float> relativeRelevance = new LinkedHashMap<>();
        absRelMap.forEach((page, rel) -> {
            float rr = (maxRel == 0) ? 0 : rel / maxRel;
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

        for (int i = 1; i < descSortedLemmas.size() && !crossPageList.isEmpty(); i++) {
            List<PageEntity> tempListPages = indexRepository.findPagesByLemma(descSortedLemmas.get(i));
            crossPageList.retainAll(tempListPages);
        }
        return crossPageList;
    }

    public List<LemmaEntity> getFilteredLemmasOnSite(String query, SiteEntity site) {

        Set<String> queryLemmas = lemmaFinder.collectLemmas(query).keySet();

        if (queryLemmas.isEmpty()) {
            return List.of();
        }
        long totalCountPages = pageRepository.countBySite(site);
        long maxAllowedFrequency = Math.max(1, Math.round(totalCountPages * MAX_LEMMA_FRACTION));

        if (totalCountPages == 0) {
            return List.of();
        }
        return queryLemmas.stream()
                .map(lemmaText -> lemmaRepository
                        .findLemmaByLemmaAndSite(lemmaText, site.getId())
                        .orElse(null)
                )
                .filter(Objects::nonNull)
                .filter(lemma -> lemma.getFrequency() <= maxAllowedFrequency)
                .sorted(Comparator.comparingInt(LemmaEntity::getFrequency))
                .toList();
    }
}


