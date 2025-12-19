package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;

import java.util.List;

public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {
    List<IndexEntity> findAllIndexByPageId(Integer pageId);
    @Modifying
    @Query("DELETE FROM IndexEntity i WHERE i.page IN :pages")
    void deleteIndexByPage(@Param("pages")List<PageEntity> pages);

    @Query("SELECT i.page FROM IndexEntity i where i.lemma = :lemma")
    List<PageEntity> findPagesByLemma(@Param("lemma")LemmaEntity lemma);

    List<LemmaEntity> findLemmasByPage(PageEntity page);

    @Query("SELECT SUM(i.rank) FROM IndexEntity i WHERE i.page = :page")
    Float findSumAllFrequenciesByPage(@Param("page") PageEntity page);
}
