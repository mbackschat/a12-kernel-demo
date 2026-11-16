package com.example.a12demo;

import java.math.BigDecimal;

import com.example.a12demo.typed.views.Subscription;
import com.mgmtp.a12.kernel.md.document.apiV2.immutable.DocumentV2;
import com.mgmtp.a12.kernel.md.document.apiV2.services.IDocumentV2Serializer;
import com.mgmtp.a12.kernel.md.model.a12internal.DocumentModel;
import com.mgmtp.a12.kernel.md.model.api.IDocumentModel;

/**
 * Showcase: <b>typed accessor overlays</b> — compile-time-typed, autocompleting, cast-free access to an
 * otherwise schema-less {@link DocumentV2}. The kernel <em>generates</em> view + pointer classes for a specific
 * DocumentModel at build time (the {@code TypedAccessorGenerator} CLI, wired as the isolated {@code genTypedAccessors}
 * Gradle task over {@code src/main/resources/models/subscription.dm.json}). This is the inverse of inferring a type
 * from a schema (Zod's {@code z.infer}): the kernel emits a typed navigation API over a dynamic document, and a
 * model/code drift becomes a <em>compile error</em> rather than a runtime {@code null}.
 *
 * <p>Domain: a <b>Subscription</b> with a {@code Tier} and a repeating {@code Addons} list. This demo lives in its
 * own {@code typedAccessor} source set so a codegen hiccup can never break the other demos' build.
 */
public final class TypedAccessorDemo {

    /** Output/examples folder for this demo (named by demo, not by model id). */
    static final String FOLDER = "typed-accessor";

    private TypedAccessorDemo() {
    }

    public static void main(String[] args) {
        // Persist the model + sample document to output/typed-accessor/ (copied into examples/ like the other demos).
        DocumentModel built = SubscriptionModel.build();
        Demos.writeModel(built, FOLDER, SubscriptionModel.DM_ID);
        IDocumentModel model = Demos.toPublicModel(built);
        IDocumentV2Serializer ser = Demos.docSerializer(model);

        // A plain, schema-less document, built the ordinary way.
        DocumentV2 doc = DocumentV2.empty(SubscriptionModel.DM_ID)
                .withFieldValue("Subscription[1]/Tier", "PRO")
                .withFieldValue("Subscription[1]/Addons[1]/Name", "Extra Seats").withFieldValue("Subscription[1]/Addons[1]/MonthlyFee", new BigDecimal("9.99"))
                .withFieldValue("Subscription[1]/Addons[2]/Name", "Priority Support").withFieldValue("Subscription[1]/Addons[2]/MonthlyFee", new BigDecimal("19.00"));
        Demos.writeDoc(ser, FOLDER, "subscription.json", doc);

        System.out.println("\n=== untyped access (Object + string paths + manual casts) ===");
        System.out.println("  doc.fieldValue(\"Subscription[1]/Tier\") = " + doc.fieldValue("Subscription[1]/Tier")
                + "   (Object — you must know the path and the type)");

        // Wrap the SAME document in the generated, compile-time-typed overlay view (no copy).
        Subscription view = Subscription._viewOf(doc);
        System.out.println("\n=== typed access (generated overlay: autocompleted, cast-free, DM-checked at compile time) ===");
        System.out.println("  view.subscription().tier() = " + view.subscription().tier() + "   (a generated Tier enum)");

        BigDecimal total = BigDecimal.ZERO;
        for (var addon : view.subscription().addons()) {            // List<Addons>, strongly typed
            System.out.printf("  add-on: %-18s %s%n", addon.name(), addon.monthlyFee());  // String, BigDecimal
            total = total.add(addon.monthlyFee());
        }
        System.out.println("  total add-on monthly fee = " + total);

        System.out.println("\n  If the model changed — say Addons.MonthlyFee were renamed — `addon.monthlyFee()` would");
        System.out.println("  fail to COMPILE, instead of silently returning null the way a string-path lookup would.");
    }
}
