package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingServiceImpl;
import searchengine.services.StatisticsServiceImpl;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsServiceImpl statisticsService;
    private final IndexingServiceImpl indexingService;

    public ApiController(StatisticsServiceImpl statisticsService,
                         IndexingServiceImpl indexingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
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
    public ResponseEntity<IndexingResponse> indexPage(@RequestBody String url) {
        return ResponseEntity.status(HttpStatus.OK).body(indexingService.indexPage(url));
    }

    @DeleteMapping()
    public ResponseEntity<IndexingResponse> deleteAllDataInBD() {
        return ResponseEntity.status(HttpStatus.OK).body(indexingService.deleteAllDataInBD());
    }
}
