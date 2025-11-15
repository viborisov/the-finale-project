package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.IndexEntity;
import searchengine.model.PageEntity;

import java.util.List;

public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {
    List<IndexEntity> findAllIndexByPageId(Integer pageId);
    @Modifying
    @Query("DELETE FROM IndexEntity i WHERE i.page IN :pages")
    void deleteIndexByPage(@Param("pages")List<PageEntity> pages);
}
