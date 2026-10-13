package conductor.contract;

/**
 * The one-shot actions, the resource lease each needs, and whether the engine would overwrite it while it steers.
 */
public enum ActionKind {
    SELECT_HOTBAR(Resources.HOTBAR, true),
    LOOK(Resources.LOOK, true),
    ATTACK(Resources.INTERACT, true),
    USE_ITEM(Resources.INTERACT, true),
    SEND_CHAT(Resources.CHAT, false);

    private final ResourceKey resource;
    private final boolean engineConflict;

    ActionKind(ResourceKey resource, boolean engineConflict) {
        this.resource = resource;
        this.engineConflict = engineConflict;
    }

    /** The lease a module must hold to perform this action. */
    public ResourceKey resource() {
        return resource;
    }

    /** True when the engine writes the same thing while it steers, so the action is refused until the bot is free. */
    public boolean engineConflict() {
        return engineConflict;
    }
}
