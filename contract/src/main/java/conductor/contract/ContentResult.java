package conductor.contract;

import java.util.Objects;

/**
 * Whether a {@link ContentId} exists in the running game or engine.
 * <p>
 * {@link Presence#ABSENT} means the runtime looked and it is not there. When the runtime cannot tell (no world yet,
 * malformed id, lookup failed), the answer is {@link Presence#UNKNOWN}, never ABSENT, and a feature that needs it
 * stays off.
 */
public final class ContentResult {
    private final ContentId content;
    private final Presence presence;
    private final String detail;

    public ContentResult(ContentId content, Presence presence, String detail) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(presence, "presence");
        detail = detail == null ? "" : detail;

        this.content = content;
        this.presence = presence;
        this.detail = detail;
    }

    public ContentId content() { return content; }

    public Presence presence() { return presence; }

    public String detail() { return detail; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContentResult)) return false;
        ContentResult other = (ContentResult) o;
        return java.util.Arrays.deepEquals(new Object[] {this.content, this.presence, this.detail}, new Object[] {other.content, other.presence, other.detail});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.content, this.presence, this.detail}); }

    @Override public String toString() { return "ContentResult[" + "content=" + content + ", " + "presence=" + presence + ", " + "detail=" + detail + "]"; }


    public enum Presence { PRESENT, ABSENT, UNKNOWN }

    public boolean isPresent() {
        return presence == Presence.PRESENT;
    }
}
