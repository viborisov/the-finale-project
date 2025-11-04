package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {
    @Modifying
    @Query("DELETE FROM PageEntity p WHERE p.site.id IN :siteIds")
    void deletePageBySiteId(@Param("siteIds")List<Integer> siteIds);

    PageEntity findPageByPathAndSite(String path, SiteEntity site);
    void deletePageById(Integer id);
}
