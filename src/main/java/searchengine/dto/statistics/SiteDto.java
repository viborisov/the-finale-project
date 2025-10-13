package searchengine.dto.statistics;



import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import searchengine.model.PageEntity;
import searchengine.model.Status;

import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
@NoArgsConstructor
public class SiteDto {
    private Integer id;
    private Status status;
    private LocalDateTime statusTime;
    private String lastError;
    private String url;
    private String name;
    private List<PageEntity> pages;
}
