package com.rayvolt.backend.billing;

import com.rayvolt.backend.errors.ConflictException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("PT-16")
class DepositServiceTest {
    private final DepositAccountRepository accounts = mock(DepositAccountRepository.class);
    private final DepositHoldRepository holds = mock(DepositHoldRepository.class);
    private final Instant now = Instant.parse("2026-11-25T10:00:00Z");
    private final DepositService service = new DepositService(accounts, holds, Clock.fixed(now, ZoneOffset.UTC));

    @ParameterizedTest
    @CsvSource({"19.99,false", "20.00,true", "20.01,true"})
    void holdsTwentyEuroOnlyWhenTheSandboxBalanceCoversIt(String balance, boolean succeeds) {
        DepositAccount account = new DepositAccount(1L, new BigDecimal(balance));
        when(accounts.findByRiderIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(holds.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        if (succeeds) {
            DepositHold hold = service.hold(1L);
            assertThat(hold.getAmount()).isEqualByComparingTo("20.00");
            assertThat(hold.getHeldAt()).isEqualTo(now);
            assertThat(account.getAvailableBalance()).isEqualByComparingTo(new BigDecimal(balance).subtract(new BigDecimal("20")));
            service.attach(hold, 3L);
            assertThat(hold.getTripId()).isEqualTo(3L);
        } else {
            assertThatThrownBy(() -> service.hold(1L)).isInstanceOf(ConflictException.class);
            assertThat(account.getAvailableBalance()).isEqualByComparingTo(balance);
            verifyNoInteractions(holds);
        }
    }

    @Test
    void missingSandboxAccountRejectsTheHold() {
        assertThatThrownBy(() -> service.hold(1L)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(holds);
    }
}
