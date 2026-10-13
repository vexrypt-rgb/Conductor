package conductor.contract;

/**
 * One non-empty inventory slot. Slot numbers: 0-8 hotbar, 9-35 main inventory, 36 feet, 37 legs, 38 chest,
 * 39 head, 40 offhand (the same numbering on every supported version).
 *
 * @param maxDamage 0 for items without durability
 */
public record SlotInfo(int slot, String item, int count, int damage, int maxDamage) {
}
