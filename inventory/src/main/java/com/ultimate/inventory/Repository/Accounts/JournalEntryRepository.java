package com.ultimate.inventory.Repository.Accounts;

import com.ultimate.inventory.Model.Accounts.Journal;
import com.ultimate.inventory.Model.Accounts.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    List<JournalEntry> findByJournal(Journal journal);
}
