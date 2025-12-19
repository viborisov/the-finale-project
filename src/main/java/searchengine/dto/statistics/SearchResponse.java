package searchengine.dto.statistics;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class SearchResponse {
    private boolean result;
    private Integer count;
    private List<Item> data;
    private String error;
}
