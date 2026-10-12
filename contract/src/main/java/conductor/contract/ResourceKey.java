package conductor.contract;

import java.util.Objects;

/**
 * A shared thing that at most one module may hold at a time.
 *
 * @param evidenceBacked whether Conductor can observe that the thing is no longer in use (only MOVEMENT today). For
 *                       other resources, hand-over relies on the holder acknowledging revocation.
 */
public record ResourceKey(String name, boolean evidenceBacked) {

    public ResourceKey {
        Objects.requireNonNull(name, "name");
    }

    @Override
    public String toString() {
        return name;
    }
}
