package com.aurelith.kubecurios;

import com.mojang.brigadier.context.ContextChain;
import java.util.function.Consumer;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import org.jetbrains.annotations.Nullable;

final class CurioScriptCommands {
    record Outcome(boolean accepted, @Nullable Integer result) {
        private static final Outcome REJECTED = new Outcome(false, null);
    }

    static Outcome run(LivingEntity wearer, String input, Consumer<String> diagnostic) {
        if (input == null || input.indexOf('\n') >= 0 || input.indexOf('\r') >= 0
                || !(wearer.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            return Outcome.REJECTED;
        }
        String stripped = input.strip();
        String command = stripped.startsWith("/") ? stripped.substring(1).stripLeading() : stripped;
        if (command.isEmpty()) {
            return Outcome.REJECTED;
        }
        var source = wearer.createCommandSourceStackForNameResolution(level)
                .withPermission(LevelBasedPermissionSet.GAMEMASTER).withSuppressedOutput();
        var probe = new ResultProbe();
        try {
            var dispatcher = level.getServer().getCommands().getDispatcher();
            var event = NeoForge.EVENT_BUS.post(new CommandEvent(dispatcher.parse(command, source)));
            if (event.isCanceled()) {
                if (event.getException() != null) {
                    diagnostic.accept("Command submission failed: " + event.getException());
                }
                return Outcome.REJECTED;
            }
            var parsed = event.getParseResults();
            Commands.validateParseResults(parsed);
            parsed = Commands.mapSource(parsed, stack -> stack.withCallback(probe, CommandResultCallback::chain));
            var chain = ContextChain.tryFlatten(parsed.getContext().build(command));
            if (chain.isEmpty()) {
                diagnostic.accept("Command is incomplete or unknown: " + command);
                return Outcome.REJECTED;
            }
            var executionSource = parsed.getContext().getSource();
            Commands.executeCommandInContext(executionSource, context ->
                    ExecutionContext.queueInitialCommandExecution(context, command, chain.get(), executionSource, probe));
            return probe.snapshot();
        } catch (Exception exception) {
            diagnostic.accept("Command submission failed: " + exception);
            return Outcome.REJECTED;
        }
    }

    private static final class ResultProbe implements CommandResultCallback {
        private boolean success;
        private boolean failure;
        private boolean conflicting;
        private int value;

        @Override
        public void onResult(boolean success, int result) {
            if (success) {
                if (!this.success) {
                    value = result;
                } else if (value != result) {
                    conflicting = true;
                }
                this.success = true;
            } else {
                failure = true;
            }
        }

        Outcome snapshot() {
            return new Outcome(success || !failure, success && !conflicting ? value : null);
        }
    }
}
