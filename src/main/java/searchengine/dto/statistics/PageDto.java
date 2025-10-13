package searchengine.dto.statistics;



import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import searchengine.model.SiteEntity;

@Setter
@Getter
@NoArgsConstructor
public class PageDto {
    private Integer id;
    private SiteEntity site;
    private String path;
    private String content;
}
