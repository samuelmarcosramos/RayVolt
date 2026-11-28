package com.rayvolt.backend.billing;

import com.rayvolt.backend.errors.ConflictException;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "deposit_accounts")
public class DepositAccount {
    @Id private Long riderId;
    private BigDecimal availableBalance;

    protected DepositAccount() { }

    public DepositAccount(Long riderId, BigDecimal availableBalance) {
        if (riderId == null || riderId <= 0 || availableBalance == null || availableBalance.signum() < 0) {
            throw new IllegalArgumentException("A valid sandbox account and balance are required");
        }
        this.riderId = riderId;
        this.availableBalance = availableBalance;
    }

    public void hold(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        if (availableBalance.compareTo(amount) < 0) {
            throw new ConflictException("Deposit hold failed: insufficient sandbox balance");
        }
        availableBalance = availableBalance.subtract(amount);
    }

    public BigDecimal getAvailableBalance() { return availableBalance; }
}
