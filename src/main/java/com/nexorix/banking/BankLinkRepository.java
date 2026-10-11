package com.nexorix.banking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankLinkRepository extends JpaRepository<BankLink, Long> {

    Optional<BankLink> findByExternalRef(String externalRef);

    List<BankLink> findByUserIdOrderByIdAsc(Long userId);

    Optional<BankLink> findByAccountId(Long accountId);

    boolean existsByAccountId(Long accountId);
}
