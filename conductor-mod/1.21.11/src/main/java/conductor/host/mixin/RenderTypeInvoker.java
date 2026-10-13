package conductor.host.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 1.21.11: RenderType.create is package-private; Conductor needs one render type of its own (lines without depth). */
@Mixin(RenderType.class)
public interface RenderTypeInvoker {

    @Invoker("create")
    static RenderType conductor$create(String name, RenderSetup setup) {
        throw new AssertionError("mixin not applied");
    }
}
