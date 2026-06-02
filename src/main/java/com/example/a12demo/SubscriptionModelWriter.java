package com.example.a12demo;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import com.mgmtp.a12.kernel.core.tool.a12internal.api.error.IProblemReporter;
import com.mgmtp.a12.kernel.md.serializer.model.a12internal.services.DocumentModelSerializer;

/**
 * Build helper (not a demo): serializes {@link SubscriptionModel} to
 * {@code src/main/resources/models/subscription.dm.json} — the committed input the build-time typed-accessor
 * generator reads. Re-run via {@code gradle genSubscriptionModel} if the model changes.
 */
public final class SubscriptionModelWriter {

    private SubscriptionModelWriter() {
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of("src/main/resources/models/subscription.dm.json");
        Files.createDirectories(out.getParent());
        StringWriter sw = new StringWriter();
        IProblemReporter reporter = p -> System.err.println("model problem: " + p);
        new DocumentModelSerializer().serialize(SubscriptionModel.build(), sw, reporter);
        Files.writeString(out, sw.toString());
        System.out.println("wrote " + out.toAbsolutePath());
    }
}
