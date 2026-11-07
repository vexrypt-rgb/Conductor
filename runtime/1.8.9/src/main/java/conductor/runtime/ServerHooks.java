package conductor.runtime;

import conductor.contract.OnlinePlayer;
import conductor.contract.ServerCommand;
import net.minecraft.block.Block;
import net.minecraft.command.Command;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandRegistry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Server modules on Minecraft 1.8.9: what the server hooks call, and the live server as {@link ServerModules.Server}.
 * 1.8.9 has no command tree like 1.13's: a module's {@code /} command is a {@link Command} registered with the
 * server's command registry. Every entry point catches everything: a server module can never stop the server.
 */
public final class ServerHooks {

    private static MinecraftServer current;
    private static Access access;
    private static boolean broken;

    private ServerHooks() {}

    /** End of {@code MinecraftServer.tick}. */
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

    /** A player's chat message as the server receives it (before vanilla sends it on). */
    public static void chat(ServerPlayerEntity player, String message) {
        try {
            ServerModules.get().chat(player.getUuid().toString(), message);
        } catch (Throwable t) {
            org.apache.logging.log4j.LogManager.getLogger("conductor").warn("Conductor could not pass on a chat message", t);
        }
    }

    /** The server's command registry is built (server start): add the modules' commands. */
    public static void commands(CommandRegistry registry) {
        try {
            for (ServerModules.Registered r : ServerModules.get().commands()) {
                if (registry.getCommandMap().containsKey(r.command().name())) {
                    org.apache.logging.log4j.LogManager.getLogger("conductor").warn(
                            "Conductor server module {}: /{} is already a command; left out", r.moduleId(), r.command().name());
                    continue;
                }
                registry.registerCommand(new ModuleCommand(r));
            }
        } catch (Throwable t) {
            org.apache.logging.log4j.LogManager.getLogger("conductor").error("Conductor could not add server module commands", t);
        }
    }

    /** A server module's {@code /} command, as the 1.8.9 command registry runs it. */
    private static final class ModuleCommand implements Command {
        private final ServerModules.Registered r;

        ModuleCommand(ServerModules.Registered r) {
            this.r = r;
        }

        @Override
        public String getCommandName() {
            return r.command().name();
        }

        @Override
        public String getUsageTranslationKey(CommandSource source) {
            return "/" + r.command().name();
        }

        @Override
        public List<String> getAliases() {
            return Jdk8.listOf();
        }

        @Override
        public void execute(CommandSource src, String[] args) throws CommandException {
            ServerCommand c = r.command();
            try {
                Access a = access(MinecraftServer.getServer());
                Optional<OnlinePlayer> player = src instanceof ServerPlayerEntity ? Optional.of(a.info((ServerPlayerEntity) src)) : Optional.empty();
                List<String> list = args.length == 0 ? Jdk8.listOf() : Arrays.asList(args);
                String reply = ServerModules.get().run(r, a, new ServerCommand.Call(c.name(), list, src.getName().asUnformattedString(), player));
                if (reply != null) {
                    src.sendMessage(new LiteralText(reply));
                }
            } catch (ServerCommand.Failure f) {
                src.sendMessage(new LiteralText(String.valueOf(f.getMessage())));
            } catch (Throwable t) {
                org.apache.logging.log4j.LogManager.getLogger("conductor").error("Conductor server command /{} failed", c.name(), t);
                src.sendMessage(new LiteralText("/" + c.name() + " failed; see the server log"));
            }
        }

        @Override
        public boolean isAccessible(CommandSource src) {
            return !r.command().operator() || src.canUseCommand(2, r.command().name());
        }

        @Override
        public List<String> getAutoCompleteHints(CommandSource src, String[] args, BlockPos pos) {
            return Jdk8.listOf();
        }

        @Override
        public boolean isUsernameAtIndex(String[] args, int index) {
            return false;
        }

        @Override
        public int compareTo(Command other) {
            return getCommandName().compareTo(other.getCommandName());
        }
    }

    private static Access access(MinecraftServer server) {
        if (current != server || access == null) {
            current = server;
            access = new Access(server);
        }
        return access;
    }

    private static final class Access implements ServerModules.Server {
        private final MinecraftServer server;

        public Access(MinecraftServer server) {
            this.server = server;
        }

        public MinecraftServer server() {
            return server;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Access)) return false;
            Access other = (Access) o;
            return java.util.Objects.equals(this.server, other.server);
        }

        @Override public int hashCode() {
            return java.util.Objects.hash(server);
        }

        @Override public String toString() {
            return "Access[" + "server=" + server + "]";
        }

        OnlinePlayer info(ServerPlayerEntity p) {
            return new OnlinePlayer(p.getName().asUnformattedString(), p.getUuid().toString(),
                    MinecraftGameAccess.dimensionName(p.world.dimension), p.x, p.y, p.z, p.getHealth(), false);
        }

        @Override
        public String minecraftVersion() {
            return ContentInstaller.MINECRAFT;
        }

        @Override
        public boolean dedicated() {
            return server.isDedicated();
        }

        @Override
        public long tick() {
            return server.getTicks();
        }

        @Override
        public List<OnlinePlayer> players() {
            List<OnlinePlayer> out = new ArrayList<>();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayers()) {
                out.add(info(p));
            }
            return out;
        }

        @Override
        public boolean tell(String player, String message) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(player);
            if (p == null) {
                return false;
            }
            p.sendMessage(new LiteralText(message));
            return true;
        }

        @Override
        public void broadcast(String message) {
            server.getPlayerManager().sendToAll(new LiteralText(message));
        }

        @Override
        public void runCommand(String command) {
            server.getCommandManager().execute(server, command);
        }

        @Override
        public Optional<String> blockAt(String dimension, int x, int y, int z) {
            for (int id = 0; id < 3; id++) {
                ServerWorld level = server.getWorld(id);
                if (level != null && MinecraftGameAccess.dimensionName(level.dimension).equals(dimension)) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.isRegionLoaded(pos, pos)) {
                        return Optional.empty();
                    }
                    return Optional.of(String.valueOf(Block.REGISTRY.getIdentifier(level.getBlockState(pos).getBlock())));
                }
            }
            return Optional.empty();
        }
    }
}
