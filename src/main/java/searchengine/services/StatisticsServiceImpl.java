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

import java.time.LocalDateTime;
import java.time.ZoneId;
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

        for (SiteEntity site : sitesList) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());

            int pages = pageRepository.findPagesById(site.getId()).intValue();
            int lemmas = lemmaRepository.findLemmasBySiteId(site.getId()).intValue();

            item.setPages(pages);
            item.setLemmas(lemmas);
            item.setStatus(site.getStatus().toString());
            item.setError(site.getLastError());
            LocalDateTime localDateTime = site.getStatusTime();
            ZoneId zoneId = ZoneId.of("Europe/Moscow");
            long millis = localDateTime.atZone(zoneId).toInstant().toEpochMilli();
            item.setStatusTime(millis);

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
