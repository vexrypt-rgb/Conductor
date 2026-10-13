package conductor.host.mixin;

import conductor.runtime.ContentInstaller;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Adds module jars as packs. The game's own repositories (client resources, server data) each hold one built-in
 * source that says which kind of pack it is; other repositories are left alone.
 */
@Mixin(PackRepository.class)
abstract class PackRepositoryMixin {

    @Shadow
    @Final
    @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void conductor$addModulePacks(RepositorySource[] given, CallbackInfo ci) {
        try {
            PackType type = null;
            for (RepositorySource source : sources) {
                if (source instanceof BuiltInPackSource builtIn) {
                    type = ((BuiltInPackSourceAccessor) builtIn).conductor$packType();
                }
            }
            if (type == null) {
                return;
            }
            Set<RepositorySource> with = new LinkedHashSet<>(sources);
            with.add(ContentInstaller.packSource(type));
            sources = Set.copyOf(with);
        } catch (RuntimeException | LinkageError e) {
            org.apache.logging.log4j.LogManager.getLogger("conductor").warn("Conductor could not add module packs: {}", e.toString());
        }
    }
}
