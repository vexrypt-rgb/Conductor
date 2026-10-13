package conductor.contract;

/**
 * The kind of block, which decides its states, collision and model wiring. Each maps to the vanilla block class of
 * the same kind, so it behaves like vanilla's (stairs connect, doors open, slabs stack). The module supplies the
 * blockstate and model JSON for its block, as a resource pack would.
 */
public enum BlockShape {
    /** A full cube (the default). */
    CUBE,
    /** A cube with an axis, like logs and pillars ({@code axis=x|y|z}). */
    PILLAR,
    /** Stairs; see {@link BlockDeclaration.Builder#stairsOf}. */
    STAIRS,
    SLAB,
    WALL,
    FENCE,
    /** Uses the material's wood type for its sounds. */
    FENCE_GATE,
    /** Two blocks tall; its item places both halves. Opened by hand unless the material is IRON. */
    DOOR,
    TRAPDOOR,
    BUTTON,
    PRESSURE_PLATE,
    /** Glass panes and bars. */
    PANE,
    LEAVES,
    CARPET,
    /** A see-through full cube that doesn't hide its neighbours' faces, like glass. */
    GLASS,
    /**
     * A full cube that turns to face the player who places it, in all six directions
     * ({@code facing=north|east|south|west|up|down}), like observers.
     */
    FACING,
    /** A full cube that turns to face the player who places it ({@code facing=north|east|south|west}), like furnaces. */
    HORIZONTAL_FACING,
    /**
     * A block smaller than a cube, shaped by boxes: furniture and decorations. Its outline and collision are the
     * declared {@link BlockDeclaration.Builder#box boxes} (at least one), given as seen when it faces north; Conductor
     * turns them with the block. It faces the player who places it ({@code facing=north|east|south|west}), can hold
     * water ({@code waterlogged=true|false}) and lets light through.
     */
    MODEL
}
