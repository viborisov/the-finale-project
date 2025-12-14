package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.dto.statistics.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingServiceImpl;
import searchengine.services.SearchService;
import searchengine.services.StatisticsServiceImpl;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final StatisticsServiceImpl statisticsService;
    private final IndexingServiceImpl indexingService;
    private final SearchService searchService;

    public ApiController(StatisticsServiceImpl statisticsService,
                         IndexingServiceImpl indexingService,
                         SearchService searchService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.searchService = searchService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponse> startIndexing() {
        return ResponseEntity.status(HttpStatus.OK).body(indexingService.startIndexing());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() {
        return ResponseEntity.status(HttpStatus.OK).body(indexingService.stopIndexing());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<IndexingResponse> indexPage(@RequestParam("url") String url) {
        return ResponseEntity.status(HttpStatus.OK).body(indexingService.indexPage(url));
    }

    @DeleteMapping()
    public ResponseEntity<IndexingResponse> deleteAllDataInBD() {
        return ResponseEntity.status(HttpStatus.OK).body(indexingService.deleteAllDataInBD());
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestParam("query") String query,
                                                 @RequestParam(value = "site", required = false) String site,
                                                 @RequestParam(value = "offset", defaultValue = "0") int offset,
                                                 @RequestParam(value = "limit", defaultValue = "20") int limit) {
        SearchResponse response = searchService.search(query, site, offset, limit);
        return ResponseEntity.ok(response);
    }
}
