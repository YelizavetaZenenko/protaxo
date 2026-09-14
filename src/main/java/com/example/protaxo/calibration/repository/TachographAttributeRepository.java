package com.example.protaxo.calibration.repository;

import com.example.protaxo.calibration.entity.TachographAttribute;
import com.example.protaxo.calibration.entity.TachographAttributeCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TachographAttributeRepository extends JpaRepository<TachographAttribute, Long> {

    List<TachographAttribute> findByCategoryOrderByNameAsc(TachographAttributeCategory category);

    boolean existsByCategoryAndNameIgnoreCase(TachographAttributeCategory category, String name);
}
