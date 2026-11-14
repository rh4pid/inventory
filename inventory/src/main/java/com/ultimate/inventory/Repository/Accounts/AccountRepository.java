package com.ultimate.inventory.Repository.Accounts;

import com.ultimate.inventory.Constants.AccountPurpose;
import com.ultimate.inventory.Model.Accounts.Account;
import com.ultimate.inventory.Model.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByBusinessAndActiveTrue(Business business);
    Optional<Account> findFirstByBusinessAndAccountPurposeAndActiveTrueOrderByIdAsc(Business business, AccountPurpose purpose);
    List<Account> findByBusinessOrderByAccountCodeAsc(Business business);
    List<Account> findByBusinessAndActiveTrueOrderByAccountCodeAsc(Business business);
    Optional<Account> findByIdAndBusiness(Long id, Business business);
    boolean existsByBusinessAndAccountCode(Business business, String accountCode);
}
