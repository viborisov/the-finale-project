package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteEntity;

import java.util.List;


@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {

    @Modifying
    @Query("DELETE FROM SiteEntity s WHERE s.url IN :urls")
    void deleteByUrls (@Param("urls")List<String> urls);

    @Query("SELECT s.id FROM SiteEntity s WHERE s.url IN :urls")
    List<Integer> findByUrlSiteId(@Param("urls") List<String> urls);


    SiteEntity findSiteByUrl(String url);
}
