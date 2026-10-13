package conductor.contract;

/** A block position, as plain integers, so modules never need Minecraft's own position classes. */
public record BlockPoint(int x, int y, int z) {

    public BlockPoint offset(int dx, int dy, int dz) {
        return new BlockPoint(x + dx, y + dy, z + dz);
    }
}
