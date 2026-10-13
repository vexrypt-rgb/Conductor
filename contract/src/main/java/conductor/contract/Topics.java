package conductor.contract;

/** Topics Conductor itself publishes. */
public final class Topics {

    private Topics() {}

    public static final Topic<LeaseEvent> LEASES = new Topic<>("conductor:leases", LeaseEvent.class);
    public static final Topic<ModuleEvent> MODULES = new Topic<>("conductor:modules", ModuleEvent.class);

    /** A module setting changed (by the player, a settings UI or the module itself). */
    public static final Topic<SettingChanged> SETTINGS = new Topic<>("conductor:settings", SettingChanged.class);

    /** Blocks the server changed (one message per block). */
    public static final Topic<GameEvent.BlockChange> BLOCK_CHANGES = new Topic<>("conductor:block-changes", GameEvent.BlockChange.class);

    /** Chunks loaded and unloaded. */
    public static final Topic<GameEvent.ChunkChange> CHUNKS = new Topic<>("conductor:chunks", GameEvent.ChunkChange.class);

    /** The local player died. */
    public static final Topic<GameEvent.PlayerDeath> DEATHS = new Topic<>("conductor:deaths", GameEvent.PlayerDeath.class);

    /** Chat lines received (player chat and system messages; not action-bar text). */
    public static final Topic<GameEvent.ChatMessage> CHAT = new Topic<>("conductor:chat", GameEvent.ChatMessage.class);
}
