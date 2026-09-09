package com.banklab.fdservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.banklab.fdservice.entity.FdAccount;

public interface FdAccountRepository extends JpaRepository<FdAccount, UUID> {

    Optional<FdAccount> findByAcctNum(String acctNum);
}
