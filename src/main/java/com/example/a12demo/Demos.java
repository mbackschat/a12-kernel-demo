package com.example.a12demo;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.mgmtp.a12.kernel.core.tool.a12internal.api.error.IProblemReporter;
import com.mgmtp.a12.kernel.md.document.api.services.DocumentSerializationConfig;
import com.mgmtp.a12.kernel.md.document.apiV2.PartiallyKnownDocumentMultiPointer;
import com.mgmtp.a12.kernel.md.document.apiV2.immutable.DocumentV2;
import com.mgmtp.a12.kernel.md.document.apiV2.services.IDocumentV2Serializer;
import com.mgmtp.a12.kernel.md.facade.DocumentModelServiceFactory;
import com.mgmtp.a12.kernel.md.facade.DocumentRtServiceFactory;
import com.mgmtp.a12.kernel.md.facade.DocumentServiceFactory;
import com.mgmtp.a12.kernel.md.model.a12internal.DocumentModel;
import com.mgmtp.a12.kernel.md.model.a12internal.LocalizedTextMapBuilder;
import com.mgmtp.a12.kernel.md.model.api.IDocumentModel;
import com.mgmtp.a12.kernel.md.model.api.ILocalizedTextMap;
import com.mgmtp.a12.kernel.md.model.api.services.IDocumentModelResolver;
import com.mgmtp.a12.kernel.md.rt.api.DocumentProcessingConfig;
import com.mgmtp.a12.kernel.md.rt.api.IDocumentDynamicServiceConfig;
import com.mgmtp.a12.kernel.md.rt.api.IDocumentRtService;
import com.mgmtp.a12.kernel.md.rt.api.IDocumentValidationResult;
import com.mgmtp.a12.kernel.md.rt.api.ILabelProvider;
import com.mgmtp.a12.kernel.md.rt.api.IMessage;
import com.mgmtp.a12.kernel.md.rt.api.IModelCode;
import com.mgmtp.a12.kernel.md.rt.api.IModelCodeCache;
import com.mgmtp.a12.kernel.md.serializer.model.a12internal.services.DocumentModelSerializer;

/**
 * Shared plumbing for the feature-showcase demos so each {@code *Demo} stays focused on the
 * <em>kernel feature</em> it illustrates rather than on boilerplate.
 *
 * <p>Provides: the internal-builder → public-{@link IDocumentModel} bridge, a ready-to-use dynamic
 * {@link IDocumentRtService}, a bilingual {@link ILocalizedTextMap} helper, a rich result printer, and the
 * convention helpers that <b>persist every built model and document to {@code output/}</b> for inspection
 * (see this project's CLAUDE.md — those files are then copied to the committed {@code examples/}).
 */
final class Demos {

    /** Where demos persist their built models + documents (git-ignored; copied to examples/ for GitHub browsing). */
    static final Path OUT = Path.of("output");

    private Demos() {
    }

    /**
     * Bridge a model built with the internal builder API to a public {@link IDocumentModel}:
     * serialize to canonical DM JSON, then deserialize via the public serializer. (The builder yields
     * the internal model type; the runtime wants the public interface — see KERNEL-DEV-GUIDE.md.)
     */
    static IDocumentModel toPublicModel(DocumentModel built) {
        try {
            return new DocumentModelServiceFactory()
                    .createDocumentModelSerializer().deserialize(new StringReader(serializeModel(built)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Persist a built model to {@code output/<folder>/<dmId>.dm.json} for inspection. The {@code folder}
     * is the demo's name (so artifacts are found by demo, not by model id); the file keeps the model id
     * {@code dmId} so it matches the {@code "id"} inside the JSON.
     */
    static void writeModel(DocumentModel built, String folder, String dmId) {
        write(folder, dmId + ".dm.json", serializeModel(built));
    }

    /** A document serializer bound to the given model (needed to persist documents). */
    static IDocumentV2Serializer docSerializer(IDocumentModel model) {
        return new DocumentServiceFactory(resolver(model)).createDocumentV2Serializer();
    }

    /** Persist one document to {@code output/<folder>/<fileName>} (next to its model) for inspection. */
    static void writeDoc(IDocumentV2Serializer serializer, String folder, String fileName, DocumentV2 doc) {
        try {
            StringWriter sw = new StringWriter();
            serializer.serializeV2(doc, sw, DocumentSerializationConfig.builder().build());
            write(folder, fileName, sw.toString());
        } catch (Exception e) {
            throw new RuntimeException("could not serialize document " + fileName, e);
        }
    }

    /** Persist {@code doc} under the demo's folder, validate it, and print the rich report. */
    static void check(IDocumentRtService rt, IDocumentV2Serializer serializer, DocumentProcessingConfig cfg,
            String folder, String title, String fileName, DocumentV2 doc) {
        writeDoc(serializer, folder, fileName, doc);
        report(title, rt.validateFull(doc, cfg));
    }

    /** A resolver that always returns the one model this demo uses. */
    static IDocumentModelResolver resolver(IDocumentModel model) {
        return id -> model;
    }

    /** Wire the dynamic validation runtime (generate + compile validation code on demand, cached). */
    static IDocumentRtService dynamicRtService(IDocumentModelResolver resolver) {
        IModelCodeCache cache = new IModelCodeCache() {
            private final Map<String, IModelCode> codes = new ConcurrentHashMap<>();
            @Override public IModelCode getModelCode(String id) { return codes.get(id); }
            @Override public void addModelCode(String id, IModelCode code) { codes.put(id, code); }
        };
        IDocumentDynamicServiceConfig serviceConfig = new IDocumentDynamicServiceConfig() {
            @Override public IModelCodeCache getCache() { return cache; }
            @Override public Optional<String> getVariant() { return Optional.empty(); }
            @Override public Optional<ILabelProvider> getLabelProvider() { return Optional.empty(); }
        };
        return new DocumentRtServiceFactory(resolver).createDocumentRtService(serviceConfig);
    }

    /** Bilingual (en_US + de_DE) localized text — used for rule/field error messages. */
    static ILocalizedTextMap text(String en, String de) {
        return new LocalizedTextMapBuilder().add(Locale.US, en).add(Locale.GERMANY, de).build();
    }

    /** A rich validation report that surfaces the full {@link IMessage} surface. */
    static void report(String title, IDocumentValidationResult result) {
        System.out.println();
        System.out.println("=== " + title + " ===");
        System.out.println(result.noErrorOccurred() ? "  result: VALID (no blocking errors)"
                                                     : "  result: INVALID (errors present)");
        if (result.getMessages().isEmpty()) {
            System.out.println("  (no messages)");
            return;
        }
        for (IMessage m : result.getMessages()) {
            System.out.printf("  [%-7s %-13s] %-16s %s%n",
                    m.getSeverity(),                // ERROR / WARNING / INFO
                    m.getMessageType(),             // VALUE_ERROR / OMISSION_ERROR
                    m.getErrorCode(),
                    m.getErrorText());
            System.out.printf("        at field: %s%s%n",
                    pretty(m.getErrorFieldPointer()),   // which (possibly per-row) field the error attaches to
                    m.getRulePath().map(p -> "   rule: " + p).orElse(""));
        }
    }

    /** Render a field pointer as the familiar {@code Group[2]/Sub[1]/field} document path (groups carry their
     *  repetition index; the trailing field does not). */
    private static String pretty(PartiallyKnownDocumentMultiPointer pointer) {
        String[] segments = pointer.fullName().replaceFirst("^/", "").split("/");
        List<Integer> reps = pointer.repetitionIndexes();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(segments[i]);
            if (i < segments.length - 1 && i < reps.size()) {   // index on group segments, not the trailing field
                sb.append('[').append(reps.get(i)).append(']');
            }
        }
        return sb.toString();
    }

    private static String serializeModel(DocumentModel built) {
        try {
            StringWriter sw = new StringWriter();
            IProblemReporter reporter = p -> System.err.println("model problem: " + p);
            new DocumentModelSerializer().serialize(built, sw, reporter);
            return sw.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(String subdir, String fileName, String content) {
        try {
            Path dir = OUT.resolve(subdir);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(fileName), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
