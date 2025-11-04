package searchengine.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;


@Setter
@Getter
@NoArgsConstructor
@Entity
@Table(name = "sites")
public class SiteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')", nullable = false)
    private Status status;

    @CreationTimestamp
    @Column(name = "status_time", nullable = false)
    private LocalDateTime statusTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "url", columnDefinition = "VARCHAR(255)", nullable = false)
    private String url;

    @Column(name = "name", columnDefinition = "VARCHAR(255)", nullable = false)
    private String name;

    @OneToMany(mappedBy = "site", fetch = FetchType.LAZY)
    private List<PageEntity> pages;

    @OneToMany(mappedBy = "site", fetch = FetchType.LAZY)
    private List<LemmaEntity> lemmas;
}
