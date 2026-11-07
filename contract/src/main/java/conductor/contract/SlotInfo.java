package conductor.contract;

/**
 * One non-empty inventory slot. Slot numbers: 0-8 hotbar, 9-35 main inventory, 36 feet, 37 legs, 38 chest,
 * 39 head, 40 offhand (the same numbering on every supported version).
 *
 * @param maxDamage 0 for items without durability
 */
public final class SlotInfo {
    private final int slot;
    private final String item;
    private final int count;
    private final int damage;
    private final int maxDamage;

    public SlotInfo(int slot, String item, int count, int damage, int maxDamage) {
        this.slot = slot;
        this.item = item;
        this.count = count;
        this.damage = damage;
        this.maxDamage = maxDamage;
    }

    public int slot() { return slot; }

    public String item() { return item; }

    public int count() { return count; }

    public int damage() { return damage; }

    public int maxDamage() { return maxDamage; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SlotInfo)) return false;
        SlotInfo other = (SlotInfo) o;
        return java.util.Arrays.deepEquals(new Object[] {this.slot, this.item, this.count, this.damage, this.maxDamage}, new Object[] {other.slot, other.item, other.count, other.damage, other.maxDamage});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.slot, this.item, this.count, this.damage, this.maxDamage}); }

    @Override public String toString() { return "SlotInfo[" + "slot=" + slot + ", " + "item=" + item + ", " + "count=" + count + ", " + "damage=" + damage + ", " + "maxDamage=" + maxDamage + "]"; }
}
