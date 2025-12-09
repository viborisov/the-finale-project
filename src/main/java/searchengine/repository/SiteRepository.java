package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Optional;


@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {

    @Modifying
    @Query("DELETE FROM SiteEntity s WHERE s.url IN :urls")
    void deleteSiteByUrls(@Param("urls")List<String> urls);

    @Query("SELECT s.id FROM SiteEntity s WHERE s.url IN :urls")
    List<Integer> findSiteIdByUrl(@Param("urls") List<String> urls);

    Optional<SiteEntity> findSiteByUrl(String url);

    @Query("SELECT s FROM SiteEntity s WHERE s.url IN :urls")
    List<SiteEntity> findSiteByUrl(@Param("urls") List<String> urls);

}
