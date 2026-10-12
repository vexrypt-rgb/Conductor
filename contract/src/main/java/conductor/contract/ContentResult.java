package conductor.contract;

import java.util.Objects;

/**
 * Whether a {@link ContentId} exists in the running game or engine.
 * <p>
 * {@link Presence#ABSENT} means the runtime looked and it is not there. When the runtime cannot tell (no world yet,
 * malformed id, lookup failed), the answer is {@link Presence#UNKNOWN}, never ABSENT, and a feature that needs it
 * stays off.
 */
public record ContentResult(ContentId content, Presence presence, String detail) {

    public enum Presence { PRESENT, ABSENT, UNKNOWN }

    public ContentResult {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(presence, "presence");
        detail = detail == null ? "" : detail;
    }

    public boolean isPresent() {
        return presence == Presence.PRESENT;
    }
}
