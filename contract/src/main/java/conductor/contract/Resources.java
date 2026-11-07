package conductor.contract;

import java.util.List;

/** The leasable resources. See docs/MODULE-PLATFORM.md §1 for who uses which. */
public final class Resources {

    private Resources() {}

    /** Pathing and movement keys. Hand-over is gated by release evidence (Stage 4). */
    public static final ResourceKey MOVEMENT = new ResourceKey("MOVEMENT", true);
    /** Player rotation. */
    public static final ResourceKey LOOK = new ResourceKey("LOOK", false);
    /** Selected hotbar slot and inventory moves. */
    public static final ResourceKey HOTBAR = new ResourceKey("HOTBAR", false);
    /** Attack and use clicks. */
    public static final ResourceKey INTERACT = new ResourceKey("INTERACT", false);
    /** Open screens and containers. */
    public static final ResourceKey SCREEN = new ResourceKey("SCREEN", false);
    /** Sending chat messages and commands. */
    public static final ResourceKey CHAT = new ResourceKey("CHAT", false);

    public static final List<ResourceKey> ALL = Jdk8.listOf(MOVEMENT, LOOK, HOTBAR, INTERACT, SCREEN, CHAT);
}
