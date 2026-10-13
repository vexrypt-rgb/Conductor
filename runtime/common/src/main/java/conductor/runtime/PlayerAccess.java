package conductor.runtime;

import conductor.contract.ActionPort;
import conductor.contract.EffectInfo;
import conductor.contract.EntityInfo;
import conductor.contract.PlayerMotion;
import conductor.contract.PlayerStatus;
import conductor.contract.SlotInfo;
import conductor.contract.WorldPort;

import java.util.List;

/**
 * Player and world reads plus one-shot actions, behind an interface so the runtime can be tested without Minecraft.
 * Game thread only. Reads return null when there is no player or world.
 */
interface PlayerAccess extends WorldPort, ActionPort {

    PlayerStatus status();

    PlayerMotion motion();

    List<EntityInfo> entities(double radius, int max);

    List<SlotInfo> slots();

    Integer selectedSlot();

    List<EffectInfo> effects();
}
