package conductor.runtime;

import conductor.contract.OnlinePlayer;
import conductor.contract.ServerCommand;
import net.minecraft.command.source.CommandSource;
import net.minecraft.network.packet.ChatMessagePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.entity.mob.player.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Server modules on the Minecraft 1.0.0 dedicated server (compiled against the server jar: 1.0's client and server are
 * separate programs). 1.0 has no command framework: a player's {@code /line} goes to
 * {@code ServerPlayNetworkHandler.runCommand} and the console's to {@code CommandHandler.run}, and both hooks ask
 * {@link #command} first. Every entry point catches everything: a server module can never stop the server.
 */
public final class ServerHooks {

    /** 1.0's own commands, which a module command may not replace. */
    static final Set<String> VANILLA = Set.of("help", "?", "list", "stop", "save-all", "save-off", "save-on", "op", "deop",
            "ban-ip", "pardon-ip", "ban", "pardon", "banlist", "kick", "tp", "give", "xp", "gamemode", "time", "say",
            "tell", "whitelist", "toggledownfall", "me", "kill");

    private static MinecraftServer current;
    private static Access access;
    private static boolean broken;
    private static boolean reportedClashes;

    private ServerHooks() {}

    /** End of {@code MinecraftServer.tick}. */
    public static void tick(MinecraftServer server) {
        try {
            ServerModules m = ServerModules.get();
            Access a = access(server);
            a.ticks++;
            if (broken || m.moduleIds().isEmpty()) {
                return;
            }
            if (!reportedClashes) {
                reportedClashes = true;
                ContentInstaller.reportUnsupported();
                for (ServerModules.Registered r : m.commands()) {
                    if (VANILLA.contains(r.command().name().toLowerCase(Locale.ROOT))) {
                        ConductorLog.warn("Conductor server module {}: /{} is already a command; left out", r.moduleId(), r.command().name());
                    }
                }
            }
            m.tick(a);
        } catch (Throwable t) {
            broken = true; // once, not every tick
            ConductorLog.error("Conductor server modules failed in a tick; they are off until this server stops", t);
        }
    }

    /** Start of {@code MinecraftServer.shutdown}. */
    public static void stop() {
        try {
            ServerModules.get().stop();
        } catch (Throwable t) {
            ConductorLog.error("Conductor server modules failed while stopping", t);
        } finally {
            current = null;
            access = null;
            broken = false;
            reportedClashes = false;
        }
    }

    /** A player's chat line, as vanilla is about to send it to everyone (commands never get here). */
    public static void chat(ServerPlayerEntity player, String message) {
        try {
            ServerModules.get().chat(LegacyIds.offlineId(player.name).toString(), message);
        } catch (Throwable t) {
            ConductorLog.warn("Conductor could not pass on a chat message", t);
        }
    }

    /**
     * A command line, without its {@code /}, from a player ({@code player} set) or the console. Returns true when it
     * was a module's command and has been answered; false leaves it to vanilla.
     */
    public static boolean command(MinecraftServer server, String line, ServerPlayerEntity player, CommandSource source) {
        try {
            String[] words = line.trim().split("\\s+", 2);
            String name = words[0].toLowerCase(Locale.ROOT);
            if (name.isEmpty() || VANILLA.contains(name)) {
                return false;
            }
            ServerModules m = ServerModules.get();
            ServerModules.Registered r = null;
            for (ServerModules.Registered c : m.commands()) {
                if (c.command().name().toLowerCase(Locale.ROOT).equals(name)) {
                    r = c;
                    break;
                }
            }
            if (r == null) {
                return false;
            }
            Access a = access(server);
            if (player != null && r.command().operator() && !server.playerManager.isOp(player.name)) {
                a.reply(player, source, "§cYou do not have permission to use /" + r.command().name());
                return true;
            }
            String args = words.length > 1 ? words[1].trim() : "";
            List<String> list = args.isEmpty() ? List.of() : Arrays.asList(args.split("\\s+"));
            String sender = player != null ? player.name : source.getCommandSourceName();
            Optional<OnlinePlayer> who = player != null ? Optional.of(a.info(player)) : Optional.empty();
            try {
                String reply = m.run(r, a, new ServerCommand.Call(r.command().name(), list, sender, who));
                if (reply != null) {
                    a.reply(player, source, reply);
                }
            } catch (ServerCommand.Failure f) {
                a.reply(player, source, "§c" + f.getMessage());
            }
            return true;
        } catch (Throwable t) {
            ConductorLog.error("Conductor server command failed: /{}", line, t);
            return false;
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
        long ticks;

        Access(MinecraftServer server) {
            this.server = server;
        }

        OnlinePlayer info(ServerPlayerEntity p) {
            return new OnlinePlayer(p.name, LegacyIds.offlineId(p.name).toString(), LegacyIds.dimension(p.dimension),
                    p.x, p.shape.minY, p.z, p.getHealth(), false);
        }

        void reply(ServerPlayerEntity player, CommandSource source, String message) {
            for (String line : LegacyEvents.chatLines(message)) {
                if (player != null) {
                    player.networkHandler.sendPacket(new ChatMessagePacket(line));
                } else {
                    source.sendMessage(line.replaceAll("§.", "")); // the console prints no colours
                }
            }
        }

        @Override
        public String minecraftVersion() {
            return ContentInstaller.MINECRAFT;
        }

        @Override
        public boolean dedicated() {
            return true; // 1.0's single-player runs no server
        }

        @Override
        public long tick() {
            return ticks;
        }

        @Override
        public List<OnlinePlayer> players() {
            List<OnlinePlayer> out = new ArrayList<>();
            for (Object o : server.playerManager.players) {
                out.add(info((ServerPlayerEntity) o));
            }
            return out;
        }

        @Override
        public boolean tell(String player, String message) {
            ServerPlayerEntity p = server.playerManager.get(player);
            if (p == null) {
                return false;
            }
            reply(p, null, message);
            return true;
        }

        @Override
        public void broadcast(String message) {
            for (String line : LegacyEvents.chatLines(message)) {
                server.playerManager.sendPacket(new ChatMessagePacket(line));
            }
        }

        @Override
        public void runCommand(String command) {
            server.queueCommand(LegacyIds.command(command), server); // run by vanilla's command handler at the start of the next tick
        }

        @Override
        public Optional<String> blockAt(String dimension, int x, int y, int z) {
            Integer number = LegacyIds.dimensionNumber(dimension);
            ServerWorld world = number == null ? null : server.getWorld(number);
            if (world == null || !world.isChunkLoaded(x, y, z)) {
                return Optional.empty();
            }
            return Optional.of(LegacyIds.block(world.getBlock(x, y, z), world.getBlockMetadata(x, y, z)));
        }
    }
}
