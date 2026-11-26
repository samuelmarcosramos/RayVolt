package com.rayvolt.backend.service;

import com.rayvolt.backend.model.CommandReply;
import com.rayvolt.backend.model.ScooterCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("PT-46")
class CommandServiceTest {
    private final ScooterGateway gateway = mock(ScooterGateway.class);
    private final CommandService service = new CommandService(gateway);

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @SuppressWarnings("unchecked")
    private Consumer<CommandReply> replyListener() {
        ArgumentCaptor<Consumer<CommandReply>> listener = ArgumentCaptor.forClass(Consumer.class);
        verify(gateway).onReply(listener.capture());
        return listener.getValue();
    }

    private String sentCommandId() {
        ArgumentCaptor<String> commandId = ArgumentCaptor.forClass(String.class);
        verify(gateway).sendCommand(eq(7L), commandId.capture(), eq(ScooterCommand.UNLOCK));
        return commandId.getValue();
    }

    private CommandReply reply(String commandId, CommandReply.Status status, String reason) {
        return new CommandReply(7L, Instant.parse("2026-11-24T10:00:00Z"), commandId, ScooterCommand.UNLOCK,
                status, reason);
    }

    @Test
    void sendPublishesTheCommandWithARandomUuidAndWaitsForTheReply() {
        CompletableFuture<CommandReply> reply = service.send(7L, ScooterCommand.UNLOCK);
        assertThat(UUID.fromString(sentCommandId())).isNotNull();
        assertThat(reply).isNotDone();
        assertThat(service.waitingCount()).isEqualTo(1);
    }

    @Test
    void twoCommandsGetDifferentIds() {
        service.send(7L, ScooterCommand.UNLOCK);
        service.send(7L, ScooterCommand.UNLOCK);
        ArgumentCaptor<String> ids = ArgumentCaptor.forClass(String.class);
        verify(gateway, times(2)).sendCommand(eq(7L), ids.capture(), eq(ScooterCommand.UNLOCK));
        assertThat(ids.getAllValues()).doesNotHaveDuplicates();
    }

    @Test
    void rejectedReplyReachesTheCallerAndIsForgotten() {
        CompletableFuture<CommandReply> reply = service.send(7L, ScooterCommand.UNLOCK);
        CommandReply rejected = reply(sentCommandId(), CommandReply.Status.REJECTED, "SCOOTER_FAULT");
        replyListener().accept(rejected);
        assertThat(reply).isCompletedWithValue(rejected);
        assertThat(rejected.rejected()).isTrue();
        assertThat(service.waitingCount()).isZero();
    }

    @Test
    void replyForAnUnknownCommandIsIgnored() {
        CompletableFuture<CommandReply> reply = service.send(7L, ScooterCommand.UNLOCK);
        replyListener().accept(reply("not-ours", CommandReply.Status.COMPLETED, null));
        assertThat(reply).isNotDone();
        assertThat(service.waitingCount()).isEqualTo(1);
    }

    @Test
    void secondReplyForTheSameCommandIsIgnored() {
        CompletableFuture<CommandReply> reply = service.send(7L, ScooterCommand.UNLOCK);
        String commandId = sentCommandId();
        CommandReply first = reply(commandId, CommandReply.Status.COMPLETED, null);
        replyListener().accept(first);
        replyListener().accept(reply(commandId, CommandReply.Status.REJECTED, "SCOOTER_FAULT"));
        assertThat(reply).isCompletedWithValue(first);
    }

    @Test
    void insideATransactionTheCommandIsSentOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        service.send(7L, ScooterCommand.UNLOCK);
        verify(gateway, never()).sendCommand(anyLong(), anyString(), any());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        sentCommandId();
    }

    @Test
    void brokerDownFailsTheCommandForTheCallerAndForgetsIt() {
        IllegalStateException brokerDown = new IllegalStateException("broker down");
        doThrow(brokerDown).when(gateway).sendCommand(anyLong(), anyString(), any());
        CompletableFuture<CommandReply>[] reply = new CompletableFuture[1];
        assertThatCode(() -> reply[0] = service.send(7L, ScooterCommand.UNLOCK)).doesNotThrowAnyException();
        assertThat(reply[0]).isCompletedExceptionally();
        assertThat(reply[0]).failsWithin(Duration.ZERO).withThrowableOfType(ExecutionException.class)
                .withCause(brokerDown);
        assertThat(service.waitingCount()).isZero();
    }

    @Test
    void replyAfterAFailedSendIsIgnored() {
        doThrow(new IllegalStateException("broker down")).when(gateway).sendCommand(anyLong(), anyString(), any());
        CompletableFuture<CommandReply> reply = service.send(7L, ScooterCommand.UNLOCK);
        replyListener().accept(reply(sentCommandId(), CommandReply.Status.COMPLETED, null));
        assertThat(reply).isCompletedExceptionally();
        assertThat(service.waitingCount()).isZero();
    }
}
