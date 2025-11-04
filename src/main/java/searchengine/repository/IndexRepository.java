package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.IndexEntity;

import java.util.List;

public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {
    List<IndexEntity> findAllIndexByPageId(Integer pageId);
}
