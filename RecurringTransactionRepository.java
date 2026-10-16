package com.msme.cashflowguard.repository;

import com.msme.cashflowguard.entity.RecurringTransaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {

    List<RecurringTransaction> findByStatementIdOrderByExpectedDateAsc(Long statementId);

    @Modifying
    @Query("delete from RecurringTransaction r where r.statement.id = :statementId")
    int deleteByStatementId(@Param("statementId") Long statementId);
}
