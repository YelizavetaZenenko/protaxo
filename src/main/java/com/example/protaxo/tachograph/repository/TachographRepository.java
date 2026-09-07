package com.example.protaxo.tachograph.repository;

import com.example.protaxo.tachograph.entity.Tachograph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TachographRepository extends JpaRepository<Tachograph, Long> {
}
