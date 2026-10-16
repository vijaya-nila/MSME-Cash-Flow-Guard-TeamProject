package com.msme.cashflowguard.repository;

import com.msme.cashflowguard.entity.BankStatement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankStatementRepository extends JpaRepository<BankStatement, Long> {

    /** Only returns the statement if it belongs to the given user (data isolation). */
    Optional<BankStatement> findByIdAndOwnerId(Long id, Long ownerId);

    Optional<BankStatement> findFirstByOwnerIdOrderByUploadedAtDesc(Long ownerId);
}
