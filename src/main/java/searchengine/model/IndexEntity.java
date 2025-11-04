package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "search_index")
public class IndexEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    private PageEntity page;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lemma_id", nullable = false)
    private LemmaEntity lemma;

    @Column(name = "field_rank", nullable = false)
    private Float rank;

}
