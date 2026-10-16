package com.msme.cashflowguard.repository;

import com.msme.cashflowguard.entity.BusinessDetail;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessDetailRepository extends JpaRepository<BusinessDetail, Long> {

    Optional<BusinessDetail> findByUserId(Long userId);
}
