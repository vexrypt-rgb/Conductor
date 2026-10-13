package conductor.contract;

import java.util.List;

/** Draws overlay shapes every frame until replaced. Implemented per Minecraft version by a runtime adapter. */
public interface OverlayPort {

    /** Replace everything drawn with {@code shapes} (all modules' shapes together). Game thread. */
    void show(List<OverlayShape> shapes);
}
