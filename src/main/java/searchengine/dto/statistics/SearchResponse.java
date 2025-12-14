package searchengine.dto.statistics;

import lombok.Data;

import java.util.List;

@Data
public class SearchResponse {
    private boolean result;
    private Integer count;
    private List<Item> data;
}
