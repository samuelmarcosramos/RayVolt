package com.rayvolt.backend.fleet.service;

import com.rayvolt.backend.common.exception.BadRequestException;
import com.rayvolt.backend.common.exception.NotFoundException;
import com.rayvolt.backend.fleet.model.*;
import com.rayvolt.backend.fleet.repository.FleetRepository;
import com.rayvolt.backend.fleet.repository.ScooterStateHistoryRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import static com.rayvolt.backend.fleet.support.FixedFleetClock.NOW;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("PT-34")
class FleetLifecycleServiceTest {
    private final FleetRepository repository = mock(FleetRepository.class);
    private final ScooterStateHistoryRepository history = mock(ScooterStateHistoryRepository.class);
    private final FleetService service = new FleetService(repository, history, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void missingCauseIsRejectedBeforeChangingState() {
        assertThatThrownBy(() -> service.changeStatus(7L, ScooterStatus.RESERVED, null))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(repository, history);
    }

    @Test
    void missingScooterCannotProduceHistory() {
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        when(repository.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.changeStatus(7L, ScooterStatus.RESERVED, StateChangeCause.USER))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.history(7L)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(history);
    }

    @Test
    void creationRecordsTheInitialAssignmentAsAnOperatorAction() {
        when(repository.hasSpace(1L)).thenReturn(true);
        Scooter created = new Scooter(7L, "Standard", 1L, ScooterStatus.AVAILABLE);
        when(repository.save(any())).thenReturn(created);
        assertThat(service.add("Standard", 1L)).isEqualTo(created);
        verify(history).append(new ScooterStateChange(null, 7L, null, ScooterStatus.AVAILABLE, NOW,
                StateChangeCause.OPERATOR));
    }
}
