package com.example.a12demo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.mgmtp.a12.kernel.md.document.apiV2.immutable.DocumentV2;
import com.mgmtp.a12.kernel.md.document.apiV2.immutable.GroupInstanceV2;
import com.mgmtp.a12.kernel.md.document.apiV2.services.IDmAwareDocService;
import com.mgmtp.a12.kernel.md.document.apiV2.services.IDocumentV2Serializer;
import com.mgmtp.a12.kernel.md.document.apiV2.utils.DocumentV2Utils;
import com.mgmtp.a12.kernel.md.document.apiV2.documentchanges.Change;
import com.mgmtp.a12.kernel.md.document.apiV2.documentchanges.DocumentChanges;
import com.mgmtp.a12.kernel.md.facade.DocumentServiceFactory;
import com.mgmtp.a12.kernel.md.model.a12internal.DocumentModel;
import com.mgmtp.a12.kernel.md.model.api.IDocumentModel;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.DocumentModelBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.FieldBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.GroupBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.NumberTypeBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.StringTypeBuilder;
import com.mgmtp.a12.model.notification.RankedNotification;

/**
 * Showcase: <b>the document is a real, immutable data structure</b> — not just something to validate.
 * The kernel ships a persistent {@code DocumentV2} (copy-on-write with structural sharing) plus
 * model-aware tooling, which is well outside what a code-first validator (Zod/Joi/…) offers.
 *
 * <p>Domain: a cooking <b>Recipe</b> with a repeating list of <b>Ingredients</b>. Demonstrates:
 * <ol>
 *   <li><b>Auto-vivifying writes</b> — writing {@code Ingredients[3]} creates the missing rows [1],[2].</li>
 *   <li><b>Structural sharing</b> — an edit returns a new document that physically reuses unchanged subtrees.</li>
 *   <li><b>Built-in diffing</b> — {@code DocumentV2Utils.compare} reports exactly which fields changed.</li>
 *   <li><b>DM-aware tooling</b> — coerce a raw string to the model's Java type; strip fields not in the model.</li>
 * </ol>
 */
public final class DocumentApiDemo {

    static final String DM_ID = "recipe";
    /** Output/examples folder for this demo (named by demo, not by model id). */
    static final String FOLDER = "document-api";

    public static void main(String[] args) {
        DocumentModel built = build();
        Demos.writeModel(built, FOLDER, DM_ID);
        IDocumentModel model = Demos.toPublicModel(built);
        DocumentServiceFactory factory = new DocumentServiceFactory(Demos.resolver(model));
        IDocumentV2Serializer ser = factory.createDocumentV2Serializer();
        IDmAwareDocService dmAware = factory.createDmAwareDocService(DM_ID);

        // 1) Auto-vivifying writes: set Ingredients[3] directly; [1] and [2] are created as empty rows.
        DocumentV2 v1 = DocumentV2.empty(DM_ID)
                .withFieldValue("Recipe[1]/Title", "Pancakes")
                .withFieldValue("Recipe[1]/Ingredients[3]/Name", "Milk")
                .withFieldValue("Recipe[1]/Ingredients[3]/Grams", BigDecimal.valueOf(250));
        Demos.writeDoc(ser, FOLDER, "recipe-built.json", v1);
        System.out.println("\n=== 1. auto-vivifying writes ===");
        System.out.println("  set Ingredients[3] only -> total Ingredient rows now: "
                + v1.groupAllRepetitions("Recipe[1]/Ingredients[0]").size() + " (rows 1 & 2 auto-created empty)");

        // 2) Structural sharing: editing the Title returns a NEW document that reuses the Ingredients subtree.
        DocumentV2 v2 = v1.withFieldValue("Recipe[1]/Title", "Fluffy Pancakes");
        System.out.println("\n=== 2. immutability + structural sharing ===");
        System.out.println("  v1 != v2 (new instance):              " + (v1 != v2));
        System.out.println("  Ingredients[3] subtree REUSED (same): "
                + (v1.group("Recipe[1]/Ingredients[3]") == v2.group("Recipe[1]/Ingredients[3]")));
        System.out.println("  v1 Title=" + v1.fieldValue("Recipe[1]/Title") + " | v2 Title=" + v2.fieldValue("Recipe[1]/Title"));

        // 3) Built-in diff between the two versions.
        System.out.println("\n=== 3. document diff (v1 -> v2) ===");
        DocumentChanges diff = DocumentV2Utils.compare(v1, v2,
                DocumentV2Utils.CompareConfig.builder().emptyAndNullAsAbsent(true).build());
        for (Change<?> c : diff.expandedFieldChanges()) {
            System.out.printf("  %-7s %s : %s -> %s%n",
                    c.isUpdate() ? "UPDATED" : c.isAdd() ? "ADDED" : "DELETED",
                    c.pointer(), valueOf(c.oldValue()), valueOf(c.newValue()));
        }
        Demos.writeDoc(ser, FOLDER, "recipe-updated.json", v2);

        // 4) DM-aware tooling: coerce a raw string to the model's Java type; strip unknown fields.
        System.out.println("\n=== 4. DM-aware tooling ===");
        List<RankedNotification> notes = new ArrayList<>();
        Object typed = dmAware.convertToJavaTypeV2("/Recipe/Ingredients/Grams", "120", notes::add);
        System.out.println("  convertToJavaTypeV2(\"120\") -> " + typed
                + (typed != null ? " (" + typed.getClass().getSimpleName() + ")" : " " + notes));
        DocumentV2 withStray = v2.withFieldValue("Recipe[1]/SecretIngredient", "love");
        DocumentV2 cleaned = dmAware.removeUnknowns(withStray);
        System.out.println("  before removeUnknowns: SecretIngredient=" + withStray.fieldValue("Recipe[1]/SecretIngredient"));
        System.out.println("  after  removeUnknowns: SecretIngredient=" + cleaned.fieldValue("Recipe[1]/SecretIngredient") + " (stripped — not in the model)");
    }

    private static String valueOf(Object fieldInstance) {
        // Change carries FieldInstanceV2; print its value via toString for the demo.
        return String.valueOf(fieldInstance);
    }

    static DocumentModel build() {
        DocumentModel dm = DocumentModelBuilder.builderWithDefaultsForTests().id(DM_ID).build();
        FieldBuilder.with(dm).name("/Recipe/Title").ft(StringTypeBuilder.builder()).build();
        GroupBuilder.with(dm).name("/Recipe/Ingredients").repeat(50).build();
        FieldBuilder.with(dm).name("/Recipe/Ingredients/Name").ft(StringTypeBuilder.builder()).build();
        FieldBuilder.with(dm).name("/Recipe/Ingredients/Grams").ft(NumberTypeBuilder.builder()).build();
        return dm;
    }
}
