package com.ultimate.inventory.Repository.Accounts;

import com.ultimate.inventory.Model.Accounts.Account;
import com.ultimate.inventory.Model.Accounts.Journal;
import com.ultimate.inventory.Model.Accounts.JournalEntry;
import com.ultimate.inventory.Model.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    List<JournalEntry> findByJournal(Journal journal);

    /** Lines of several journals in one query (with their account). */
    @Query("SELECT e FROM JournalEntry e JOIN FETCH e.account WHERE e.journal IN :journals ORDER BY e.id")
    List<JournalEntry> findByJournalIn(@Param("journals") Collection<Journal> journals);

    /** Per account: [accountId, total debit, total credit] of posted journals dated in [from, to). Nulls = open-ended. */
    @Query("SELECT e.account.id, COALESCE(SUM(e.debit), 0), COALESCE(SUM(e.credit), 0) FROM JournalEntry e"
            + " WHERE e.journal.business = :business"
            + " AND e.journal.status = com.ultimate.inventory.Constants.JournalStatus.POSTED"
            + " AND e.journal.transactionDate >= :from AND e.journal.transactionDate < :to"
            + " GROUP BY e.account.id")
    List<Object[]> totalsByAccount(@Param("business") Business business,
                                   @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** [total debit, total credit] of one account's posted lines dated before {@code before}. */
    @Query("SELECT COALESCE(SUM(e.debit), 0), COALESCE(SUM(e.credit), 0) FROM JournalEntry e"
            + " WHERE e.account = :account"
            + " AND e.journal.status = com.ultimate.inventory.Constants.JournalStatus.POSTED"
            + " AND e.journal.transactionDate < :before")
    List<Object[]> totalsBefore(@Param("account") Account account, @Param("before") LocalDateTime before);

    /** One account's posted lines dated in [from, to), oldest first, with their journal. */
    @Query("SELECT e FROM JournalEntry e JOIN FETCH e.journal j WHERE e.account = :account"
            + " AND j.status = com.ultimate.inventory.Constants.JournalStatus.POSTED"
            + " AND j.transactionDate >= :from AND j.transactionDate < :to"
            + " ORDER BY j.transactionDate, j.id, e.id")
    List<JournalEntry> ledger(@Param("account") Account account,
                              @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    default BigDecimal[] totalsBeforeOf(Account account, LocalDateTime before) {
        List<Object[]> rows = totalsBefore(account, before);
        Object[] r = rows.isEmpty() ? new Object[]{BigDecimal.ZERO, BigDecimal.ZERO} : rows.get(0);
        return new BigDecimal[]{(BigDecimal) r[0], (BigDecimal) r[1]};
    }
}
