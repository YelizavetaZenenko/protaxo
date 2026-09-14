package com.example.protaxo.catalog.repository;

import com.example.protaxo.catalog.entity.CatalogItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, Long> {
    List<CatalogItem> findAllByOrderByIdAsc();

    /** Bypasses the deleted_at @SQLRestriction, see VehicleRepository#findRegistrationNumberByIdIncludingDeleted. */
    @Query(value = "SELECT name FROM catalog_items WHERE id = :id", nativeQuery = true)
    Optional<String> findNameByIdIncludingDeleted(@Param("id") Long id);
}
