package com.example.a12demo;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

import com.mgmtp.a12.kernel.md.document.apiV2.DocumentMultiPointer;
import com.mgmtp.a12.kernel.md.document.apiV2.immutable.DocumentV2;
import com.mgmtp.a12.kernel.md.document.apiV2.services.IDocumentV2Serializer;
import com.mgmtp.a12.kernel.md.model.a12internal.DocumentModel;
import com.mgmtp.a12.kernel.md.model.a12internal.Field;
import com.mgmtp.a12.kernel.md.model.api.IDocumentModel;
import com.mgmtp.a12.kernel.md.model.api.IRule.Severity;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.DocumentModelBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.FieldBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.RuleBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.NumberTypeBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.StringTypeBuilder;
import com.mgmtp.a12.kernel.md.rt.api.DocumentProcessingConfig;
import com.mgmtp.a12.kernel.md.rt.api.IDocumentRtService;

/**
 * Showcase: <b>incremental / "wizard" validation</b> — validate only the part of a document the user can
 * currently see, without nagging about fields on later pages. {@code validatePart} reports an error only if
 * the rule's error field is in the supplied "relevant" set, so a multi-page form validates page-by-page.
 * Mainstream validators are all-or-nothing per object; this relevance-scoped mode is unusual.
 *
 * <p>Domain: a 3-page <b>Loan application</b> — Applicant, Employment, Loan. The same document is validated
 * fully, then page-by-page.
 */
public final class PartialValidationDemo {

    static final String DM_ID = "loan-application";
    /** Output/examples folder for this demo (named by demo, not by model id). */
    static final String FOLDER = "partial-validation";

    public static void main(String[] args) {
        DocumentModel built = build();
        Demos.writeModel(built, FOLDER, DM_ID);
        IDocumentModel model = Demos.toPublicModel(built);
        IDocumentRtService rt = Demos.dynamicRtService(Demos.resolver(model));
        IDocumentV2Serializer ser = Demos.docSerializer(model);
        DocumentProcessingConfig cfg = DocumentProcessingConfig.builder(Locale.US).build();

        // Name missing (page 1); income present but low; amount huge -> affordability fails (page 3).
        DocumentV2 doc = DocumentV2.empty(DM_ID)
                .withFieldValue("Employment[1]/Employer", "Globex")
                .withFieldValue("Employment[1]/MonthlyIncome", BigDecimal.valueOf(2000))
                .withFieldValue("Loan[1]/Amount", BigDecimal.valueOf(500000))
                .withFieldValue("Loan[1]/TermMonths", BigDecimal.valueOf(120));
        Demos.writeDoc(ser, FOLDER, "application.json", doc);

        // Full validation sees every page at once.
        Demos.report("validateFull (whole application)", rt.validateFull(doc, cfg));

        // Partial validation: only the fields on the named page(s) are "relevant". An error is reported only
        // if BOTH its error field and every field its rule references are relevant — so you're never nagged
        // about something you can't fix from the current page.
        report("validatePart — page 1: Applicant", rt, doc, cfg, "Applicant");
        report("validatePart — page 2: Employment", rt, doc, cfg, "Employment");
        // Page 3 alone is clean: the affordability rule also references income (page 2), so it's held back...
        report("validatePart — page 3: Loan (alone)", rt, doc, cfg, "Loan");
        // ...until the review step brings both pages into scope; now the cross-page rule fires.
        report("validatePart — review: Employment + Loan", rt, doc, cfg, "Employment", "Loan");
    }

    private static void report(String title, IDocumentRtService rt, DocumentV2 doc,
            DocumentProcessingConfig cfg, String... relevantGroups) {
        Set<DocumentMultiPointer> relevant = new java.util.HashSet<>();
        for (String g : relevantGroups) {
            relevant.add(DocumentMultiPointer.ofPathWithAllWildcards(g));
        }
        Demos.report(title, rt.validatePart(doc, relevant, cfg));
    }

    static DocumentModel build() {
        DocumentModel dm = DocumentModelBuilder.builderWithDefaultsForTests().id(DM_ID).build();

        // Page 1 — Applicant
        Field fullName = FieldBuilder.with(dm).name("/Applicant/FullName").ft(StringTypeBuilder.builder()).build();
        FieldBuilder.with(dm).name("/Applicant/Ssn").ft(StringTypeBuilder.builder()).build();
        // Page 2 — Employment
        FieldBuilder.with(dm).name("/Employment/Employer").ft(StringTypeBuilder.builder()).build();
        Field income = FieldBuilder.with(dm).name("/Employment/MonthlyIncome").ft(NumberTypeBuilder.builder()).build();
        // Page 3 — Loan
        Field amount = FieldBuilder.with(dm).name("/Loan/Amount").ft(NumberTypeBuilder.builder()).build();
        FieldBuilder.with(dm).name("/Loan/TermMonths").ft(NumberTypeBuilder.builder()).build();

        // Page-1 rule: applicant name is required.
        rule(dm, "/Applicant/NameRequiredRule", fullName,
                "FieldNotFilled(FullName)", "NAME_REQUIRED", Severity.ERROR,
                "Applicant name is required.", "Name des Antragstellers ist erforderlich.");
        // Page-2 rule: income is required.
        rule(dm, "/Employment/IncomeRequiredRule", income,
                "FieldNotFilled(MonthlyIncome)", "INCOME_REQUIRED", Severity.ERROR,
                "Monthly income is required.", "Monatliches Einkommen ist erforderlich.");
        // Page-3 cross-page rule: loan must be affordable (<= 100x monthly income). Error attaches to Loan/Amount.
        rule(dm, "/Loan/AffordabilityRule", amount,
                "FieldFilled(Amount) And FieldFilled(/Employment/MonthlyIncome) And [/Loan/Amount] > [/Employment/MonthlyIncome] * 100",
                "NOT_AFFORDABLE", Severity.ERROR,
                "Loan amount exceeds 100x monthly income.", "Darlehensbetrag übersteigt das 100-fache Monatseinkommen.");

        return dm;
    }

    private static void rule(DocumentModel dm, String name, Field errorField, String condition, String code,
            Severity severity, String en, String de) {
        RuleBuilder.with(dm).name(name).field(errorField).modify(rb -> rb
                .errorCondition(condition).errorCode(code).severity(severity)
                .errorMessage(Demos.text(en, de))).build();
    }
}
