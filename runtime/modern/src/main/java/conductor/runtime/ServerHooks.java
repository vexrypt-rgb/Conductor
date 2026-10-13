package conductor.runtime;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import conductor.contract.OnlinePlayer;
import conductor.contract.ServerCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Server modules on 1.21.x and 26.3: what the server hooks call, and the live server as {@link ServerModules.Server}.
 * Every entry point catches everything: a server module can never stop the server.
 */
public final class ServerHooks {

    private static MinecraftServer current;
    private static Access access;
    private static boolean broken;

    private ServerHooks() {}

    /** End of {@code MinecraftServer.tickServer}. */
    public static void tick(MinecraftServer server) {
        try {
            ServerModules m = ServerModules.get();
            if (broken || m.moduleIds().isEmpty()) {
                return;
            }
            m.tick(access(server));
        } catch (Throwable t) {
            broken = true; // once, not every tick
            org.apache.logging.log4j.LogManager.getLogger("conductor").error(
                    "Conductor server modules failed in a tick; they are off until this server stops", t);
        }
    }

    /** Start of {@code MinecraftServer.stopServer}. */
    public static void stop(MinecraftServer server) {
        try {
            ServerModules.get().stop();
        } catch (Throwable t) {
            org.apache.logging.log4j.LogManager.getLogger("conductor").error("Conductor server modules failed while stopping", t);
        } finally {
            current = null;
            access = null;
            broken = false;
        }
    }

    /** A player's chat message is being sent to the others. */
    public static void chat(ServerPlayer player, String message) {
        try {
            ServerModules.get().chat(player.getStringUUID(), message);
        } catch (Throwable t) {
            org.apache.logging.log4j.LogManager.getLogger("conductor").warn("Conductor could not pass on a chat message", t);
        }
    }

    /** A new command dispatcher was built (server start and every {@code /reload}): add the modules' commands. */
    public static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
        try {
            for (ServerModules.Registered r : ServerModules.get().commands()) {
                ServerCommand c = r.command();
                if (dispatcher.getRoot().getChild(c.name()) != null) {
                    org.apache.logging.log4j.LogManager.getLogger("conductor").warn(
                            "Conductor server module {}: /{} is already a command; left out", r.moduleId(), c.name());
                    continue;
                }
                dispatcher.register(Commands.literal(c.name())
                        .requires(src -> !c.operator() || VersionServer.operator(src))
                        .executes(ctx -> run(r, ctx, ""))
                        .then(Commands.argument("args", StringArgumentType.greedyString())
                                .executes(ctx -> run(r, ctx, StringArgumentType.getString(ctx, "args")))));
            }
        } catch (Throwable t) {
            org.apache.logging.log4j.LogManager.getLogger("conductor").error("Conductor could not add server module commands", t);
        }
    }

    private static int run(ServerModules.Registered r, CommandContext<CommandSourceStack> ctx, String args) {
        CommandSourceStack src = ctx.getSource();
        try {
            Access a = access(src.getServer());
            Optional<OnlinePlayer> player = src.getEntity() instanceof ServerPlayer p ? Optional.of(a.info(p)) : Optional.empty();
            List<String> list = args.isBlank() ? List.of() : Arrays.asList(args.trim().split("\\s+"));
            String reply = ServerModules.get().run(r, a, new ServerCommand.Call(r.command().name(), list, src.getTextName(), player));
            if (reply != null) {
                Component text = Component.literal(reply);
                src.sendSuccess(() -> text, false);
            }
            return 1;
        } catch (ServerCommand.Failure f) {
            src.sendFailure(Component.literal(String.valueOf(f.getMessage())));
            return 0;
        } catch (Throwable t) {
            org.apache.logging.log4j.LogManager.getLogger("conductor").error("Conductor server command /{} failed", r.command().name(), t);
            src.sendFailure(Component.literal("/" + r.command().name() + " failed; see the server log"));
            return 0;
        }
    }

    private static Access access(MinecraftServer server) {
        if (current != server || access == null) {
            current = server;
            access = new Access(server);
        }
        return access;
    }

    private record Access(MinecraftServer server) implements ServerModules.Server {

        OnlinePlayer info(ServerPlayer p) {
            return new OnlinePlayer(p.getName().getString(), p.getStringUUID(), VersionIds.id(p.level().dimension()),
                    p.getX(), p.getY(), p.getZ(), p.getHealth(), p.isSpectator());
        }

        @Override
        public String minecraftVersion() {
            return VersionIds.MINECRAFT;
        }

        @Override
        public boolean dedicated() {
            return server.isDedicatedServer();
        }

        @Override
        public long tick() {
            return server.getTickCount();
        }

        @Override
        public List<OnlinePlayer> players() {
            List<OnlinePlayer> out = new ArrayList<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                out.add(info(p));
            }
            return out;
        }

        @Override
        public boolean tell(String player, String message) {
            ServerPlayer p = server.getPlayerList().getPlayerByName(player);
            if (p == null) {
                return false;
            }
            p.sendSystemMessage(Component.literal(message));
            return true;
        }

        @Override
        public void broadcast(String message) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
        }

        @Override
        public void runCommand(String command) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        }

        @Override
        public Optional<String> blockAt(String dimension, int x, int y, int z) {
            for (ServerLevel level : server.getAllLevels()) {
                if (VersionIds.id(level.dimension()).equals(dimension)) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.isLoaded(pos)) {
                        return Optional.empty();
                    }
                    return BuiltInRegistries.BLOCK.getResourceKey(level.getBlockState(pos).getBlock()).map(VersionIds::id);
                }
            }
            return Optional.empty();
        }
    }
}
