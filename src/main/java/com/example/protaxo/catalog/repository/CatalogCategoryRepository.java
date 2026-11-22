package com.example.protaxo.catalog.repository;

import com.example.protaxo.catalog.entity.CatalogCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CatalogCategoryRepository extends JpaRepository<CatalogCategory, Long> {

    List<CatalogCategory> findAllByOrderBySortOrderAscNameAsc();

    Optional<CatalogCategory> findFirstByNameIgnoreCase(String name);

    @Query("SELECT COALESCE(MAX(c.sortOrder), 0) FROM CatalogCategory c")
    int maxSortOrder();
}
