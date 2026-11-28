package com.rayvolt.backend.billing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface DepositAccountRepository extends JpaRepository<DepositAccount, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM DepositAccount a WHERE a.riderId = :riderId")
    Optional<DepositAccount> findByRiderIdForUpdate(Long riderId);
}
