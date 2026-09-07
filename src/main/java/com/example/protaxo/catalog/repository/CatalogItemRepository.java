package com.example.protaxo.catalog.repository;

import com.example.protaxo.catalog.entity.CatalogItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, Long> {
}
