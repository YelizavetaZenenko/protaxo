package com.example.protaxo.catalog.repository;

import com.example.protaxo.catalog.entity.CatalogItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, Long> {
    List<CatalogItem> findAllByOrderByIdAsc();

    /** Bypasses the deleted_at @SQLRestriction, see VehicleRepository#findRegistrationNumberByIdIncludingDeleted. */
    @Query(value = "SELECT name FROM catalog_items WHERE id = :id", nativeQuery = true)
    Optional<String> findNameByIdIncludingDeleted(@Param("id") Long id);

    /**
     * Bypasses the deleted_at @SQLRestriction like {@link #findNameByIdIncludingDeleted} - used to
     * give stock back to a catalog item that may have been soft-deleted since the sale it's being
     * restored from (see InvoiceService#restoreStock). No-ops on a SERVICE item (stock_quantity is
     * already null there) via the WHERE guard, matching the old "skip if null" behavior.
     */
    @Modifying
    @Query(value = "UPDATE catalog_items SET stock_quantity = stock_quantity + :delta "
            + "WHERE id = :id AND stock_quantity IS NOT NULL", nativeQuery = true)
    void restoreStockQuantity(@Param("id") Long id, @Param("delta") BigDecimal delta);
}
