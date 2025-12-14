package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.LemmaEntity;

import java.util.List;
import java.util.Optional;

public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {

    @Modifying
    @Query("DELETE FROM LemmaEntity l WHERE l.site.id IN :siteIds")
    void deleteLemmaBySiteId(@Param("siteIds") List<Integer> siteIds);

    @Query("SELECT l FROM LemmaEntity l WHERE l.lemma = :lemma AND l.site.id = :siteId")
    Optional<LemmaEntity> findLemmaByLemmaAndSite(@Param("lemma") String lemma, @Param("siteId") Integer siteId);

    @Query(value = "SELECT COUNT(*) FROM lemmas WHERE site_id = :id", nativeQuery = true)
    Long findLemmasBySiteId(@Param("id") Integer id);

    @Query(value = "SELECT * FROM lemmas WHERE lemma = :lemma ORDER BY frequency DESC LIMIT 1", nativeQuery = true)
    LemmaEntity findLemmaByLemma(@Param("lemma") String lemma);
}
