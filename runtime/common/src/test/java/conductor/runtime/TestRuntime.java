package conductor.runtime;

import conductor.contract.DiagnosticSink;
import conductor.contract.ReleasePolicy;

import java.util.function.Predicate;

/** Installs the real runtime wiring over a {@link FakeGameAccess}, for runtime and engine-layer tests. */
public final class TestRuntime {

    private TestRuntime() {}

    /** No player, event or overlay access; {@code engine} may be null; engine features from {@code engineClasses}. */
    public static ConductorRuntime.Installed install(DiagnosticSink sink, ReleasePolicy policy, FakeGameAccess game,
                                                     EngineLayer engine, Predicate<String> engineClasses) {
        return ConductorRuntime.install(sink, policy, game, null, null, null, engine, new EngineFeatures(engineClasses));
    }

    public static ConductorRuntime.Installed install(DiagnosticSink sink, ReleasePolicy policy, FakeGameAccess game,
                                                     EngineLayer engine) {
        return install(sink, policy, game, engine, n -> false);
    }

    /** The real engine-feature probe over {@code loader}. */
    public static conductor.contract.ContentResult.Presence engineFeature(ClassLoader loader, String id) {
        return EngineFeatures.ofLoader(loader).check(conductor.contract.ContentId.engine(id)).presence();
    }
}
