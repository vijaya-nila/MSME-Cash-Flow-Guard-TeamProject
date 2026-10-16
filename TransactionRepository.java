package com.msme.cashflowguard.repository;

import com.msme.cashflowguard.entity.Transaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByStatementIdOrderBySequenceNoAsc(Long statementId);

    @Modifying
    @Query("delete from Transaction t where t.statement.id = :statementId")
    int deleteByStatementId(@Param("statementId") Long statementId);
}
