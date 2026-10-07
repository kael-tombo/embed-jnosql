package org.embeddedjnosql.db.quarkus.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import org.embeddedjnosql.db.quarkus.JembedConfig;
import org.embeddedjnosql.db.quarkus.EmbedDBProducer;
import org.embeddedjnosql.db.quarkus.JembedRecorder;

import java.util.List;

/**
 * Quarkus build-time extension processor for EmbedJNoSQL.
 *
 * <p>Registers {@link EmbedDBProducer} as an unremovable CDI bean, records the
 * database initialization for the runtime-init phase, and configures native image resources.
 */
class JembedExtensionProcessor {

    private static final String FEATURE = "embedjnosql-embed";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem registerBeans() {
        return AdditionalBeanBuildItem.unremovableOf(EmbedDBProducer.class);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void initialize(JembedRecorder recorder, JembedConfig config) {
        recorder.createDatabase(config);
    }

    @BuildStep
    NativeImageResourceBuildItem nativeResources() {
        return new NativeImageResourceBuildItem(List.of(
                "org/embeddedjnosql/db/core/util/JsonSerde.class"
        ));
    }
}
