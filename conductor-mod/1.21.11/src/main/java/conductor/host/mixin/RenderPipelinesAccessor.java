package conductor.host.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.21.11: the vanilla line pipeline settings, to build the same lines without a depth test. */
@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {

    @Accessor("LINES_SNIPPET")
    static RenderPipeline.Snippet conductor$linesSnippet() {
        throw new AssertionError("mixin not applied");
    }
}
