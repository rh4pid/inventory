package com.ultimate.inventory.Repository.Accounts;

import com.ultimate.inventory.Model.Accounts.Journal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JournalRepository extends JpaRepository<Journal, Long> {
}
