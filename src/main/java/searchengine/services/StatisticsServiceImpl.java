package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteEntity;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;


    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        int siteCount = (int) siteRepository.count();
        total.setSites(siteCount);
        boolean isIndexing = siteRepository.findAll().stream()
                .map(SiteEntity::getStatus)
                .map(Enum::toString)
                .anyMatch(s -> s.equalsIgnoreCase("INDEXING"));
        total.setIndexing(isIndexing);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<SiteEntity> sitesList = siteRepository.findAll();

        for(int i = 0; i < sitesList.size(); i++) {
            SiteEntity site = sitesList.get(i);
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());

            int pages = pageRepository.findPagesById(i + 1).intValue();
            int lemmas = lemmaRepository.findLemmasBySiteId(i + 1).intValue();

            item.setPages(pages);
            item.setLemmas(lemmas);
            item.setStatus(sitesList.get(i).getStatus().toString());
            item.setError(sitesList.get(i).getLastError());
            LocalDateTime now = LocalDateTime.now();
            item.setStatusTime(Duration.between(sitesList.get(i).getStatusTime(), now).toMillis());

            total.setPages((int) pageRepository.count());
            total.setLemmas((int) lemmaRepository.count());
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}
