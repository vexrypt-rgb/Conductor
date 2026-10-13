package conductor.contract;

/**
 * A module that adds content to the game: items and blocks it declares, plus the {@code assets/} and {@code data/}
 * in its own jar (models, textures, names, loot, recipes, tags, dimensions, structures, worldgen). Listed in
 * {@code META-INF/services/conductor.contract.ContentModule}. It needs no Minecraft or loader classes, so one jar
 * serves every supported Minecraft version (version-specific files go in a {@code mc_<version>/} overlay).
 * <p>
 * Content is registered once, while the game starts, before Minecraft locks its registries. Client and server must
 * have the same content modules: items and blocks travel between them as numbers in registration order. A module
 * with data only (no declarations) is needed on the server only.
 */
public interface ContentModule {

    /** The namespace of everything this module adds, e.g. {@code "ruby"} for {@code ruby:ruby_block}. */
    String namespace();

    /** Declare items and blocks. Called once, at start-up, before the game exists; must not touch the game. */
    default void declare(ContentDeclarations content) {
    }
}
