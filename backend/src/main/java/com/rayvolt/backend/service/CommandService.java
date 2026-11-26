package com.rayvolt.backend.service;

import com.rayvolt.backend.model.CommandReply;
import com.rayvolt.backend.model.ScooterCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class CommandService {
    private static final Logger LOG = LoggerFactory.getLogger(CommandService.class);
    private final ScooterGateway gateway;
    private final Duration timeout;
    private final Map<String, CompletableFuture<CommandReply>> waiting = new ConcurrentHashMap<>();

    public CommandService(ScooterGateway gateway, @Value("${rayvolt.commands.timeout}") Duration timeout) {
        this.gateway = gateway;
        this.timeout = timeout;
        gateway.onReply(this::receive);
    }

    public CompletableFuture<CommandReply> send(Long scooterId, ScooterCommand command) {
        String commandId = UUID.randomUUID().toString();
        CompletableFuture<CommandReply> reply = new CompletableFuture<>();
        waiting.put(commandId, reply);
        CompletableFuture<CommandReply> caller = reply.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((result, error) -> {
                    if (error instanceof TimeoutException && waiting.remove(commandId, reply)) {
                        LOG.warn("No reply from scooter {} to {} after {}", scooterId, command, timeout);
                    }
                });
        AfterCommit.run(() -> publish(scooterId, commandId, command));
        return caller;
    }

    int waitingCount() {
        return waiting.size();
    }

    private void publish(Long scooterId, String commandId, ScooterCommand command) {
        try {
            gateway.sendCommand(scooterId, commandId, command);
        } catch (RuntimeException exception) {
            LOG.warn("Could not send {} to scooter {}: {}", command, scooterId, exception.getMessage());
            CompletableFuture<CommandReply> caller = waiting.remove(commandId);
            if (caller != null) {
                caller.completeExceptionally(exception);
            }
        }
    }

    private void receive(CommandReply reply) {
        CompletableFuture<CommandReply> caller = waiting.remove(reply.commandId());
        if (caller == null) {
            LOG.warn("Ignored reply for unknown command {}", reply.commandId());
            return;
        }
        if (reply.rejected()) {
            LOG.info("Scooter {} rejected {}: {}", reply.scooterId(), reply.command(), reply.reason());
        }
        caller.complete(reply);
    }
}
