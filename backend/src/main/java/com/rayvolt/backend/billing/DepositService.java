package com.rayvolt.backend.billing;

import com.rayvolt.backend.errors.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;

@Service
public class DepositService {
    private static final BigDecimal DEPOSIT = new BigDecimal("20.00");
    private final DepositAccountRepository accounts;
    private final DepositHoldRepository holds;
    private final Clock clock;

    public DepositService(DepositAccountRepository accounts, DepositHoldRepository holds, Clock clock) {
        this.accounts = accounts;
        this.holds = holds;
        this.clock = clock;
    }

    @Transactional
    public DepositHold hold(Long riderId) {
        DepositAccount account = accounts.findByRiderIdForUpdate(riderId)
                .orElseThrow(() -> new ConflictException("Deposit hold failed: sandbox account not found"));
        account.hold(DEPOSIT);
        accounts.save(account);
        return holds.save(new DepositHold(riderId, DEPOSIT, clock.instant()));
    }

    @Transactional
    public void attach(DepositHold hold, Long tripId) {
        hold.attachTo(tripId);
        holds.save(hold);
    }
}
