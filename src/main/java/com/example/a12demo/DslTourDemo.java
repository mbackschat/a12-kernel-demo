package com.example.a12demo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import com.mgmtp.a12.kernel.md.document.apiV2.immutable.DocumentV2;
import com.mgmtp.a12.kernel.md.document.apiV2.services.IDocumentV2Serializer;
import com.mgmtp.a12.kernel.md.model.a12internal.DocumentModel;
import com.mgmtp.a12.kernel.md.model.a12internal.Field;
import com.mgmtp.a12.kernel.md.model.a12internal.fieldtypes.EnumerationType;
import com.mgmtp.a12.kernel.md.model.api.IDocumentModel;
import com.mgmtp.a12.kernel.md.model.api.IRule.Severity;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.DocumentModelBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.FieldBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.RuleBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.DateTypeBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.EnumerationTypeBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.NumberTypeBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.StringTypeBuilder;
import com.mgmtp.a12.kernel.md.rt.api.DocumentProcessingConfig;
import com.mgmtp.a12.kernel.md.rt.api.IDocumentRtService;

/**
 * Showcase: a <b>tour of the DSL</b> — a breadth sampler of expressive, mostly single-field rules that read
 * like business language yet are authored as model data. Several are genuinely awkward in code-first libraries
 * (string slicing, tolerance compare, date arithmetic, value-list membership, "at least one of N").
 *
 * <p>Domain: an <b>Event registration</b>. The {@code incomplete} document is crafted to trip almost every
 * rule at once; the {@code complete} one passes.
 */
public final class DslTourDemo {

    static final String DM_ID = "event-registration";
    /** Output/examples folder for this demo (named by demo, not by model id). */
    static final String FOLDER = "dsl-tour";

    public static void main(String[] args) {
        DocumentModel built = build();
        Demos.writeModel(built, FOLDER, DM_ID);
        IDocumentModel model = Demos.toPublicModel(built);
        IDocumentRtService rt = Demos.dynamicRtService(Demos.resolver(model));
        IDocumentV2Serializer ser = Demos.docSerializer(model);
        DocumentProcessingConfig cfg = DocumentProcessingConfig.builder(Locale.US).build();

        Demos.check(rt, ser, cfg, FOLDER, "complete registration", "complete.json", DocumentV2.empty(DM_ID)
                .withFieldValue("Reg[1]/TicketCode", "EU-204871")
                .withFieldValue("Reg[1]/PromoCode", "XY7788")
                .withFieldValue("Reg[1]/AccessPin", "AZ8K1")
                .withFieldValue("Reg[1]/QuotedFee", new BigDecimal("249"))
                .withFieldValue("Reg[1]/PaidFee", new BigDecimal("251"))
                .withFieldValue("Reg[1]/SeatBlock", "GENERAL")
                .withFieldValue("Reg[1]/PassportExpiry", "2030-04-01")
                .withFieldValue("Reg[1]/EventDate", "2027-09-12")
                .withFieldValue("Reg[1]/Decision", "CONFIRMED")
                .withFieldValue("Reg[1]/Phone", "+49 89 5550"));

        Demos.check(rt, ser, cfg, FOLDER, "incomplete registration", "incomplete.json", DocumentV2.empty(DM_ID)
                .withFieldValue("Reg[1]/TicketCode", "US-991200")   // region code is not EU
                .withFieldValue("Reg[1]/PromoCode", "spring!")      // pattern violated
                .withFieldValue("Reg[1]/AccessPin", "12")           // not 5 chars
                .withFieldValue("Reg[1]/QuotedFee", new BigDecimal("249"))
                .withFieldValue("Reg[1]/PaidFee", new BigDecimal("210"))   // differs by 39 (> 5)
                .withFieldValue("Reg[1]/SeatBlock", "BACKSTAGE")    // sold-out block
                .withFieldValue("Reg[1]/PassportExpiry", "2026-07-01")     // expires within 6 months
                .withFieldValue("Reg[1]/EventDate", "2020-01-01")          // already passed
                .withFieldValue("Reg[1]/Decision", "DECLINED"));           // declined w/o reason; no Phone/Email
    }

    static DocumentModel build() {
        DocumentModel dm = DocumentModelBuilder.builderWithDefaultsForTests().id(DM_ID).build();

        Field ticket = FieldBuilder.with(dm).name("/Reg/TicketCode").ft(StringTypeBuilder.builder()).build();
        Field promo  = FieldBuilder.with(dm).name("/Reg/PromoCode").ft(StringTypeBuilder.builder()).build();
        Field pin    = FieldBuilder.with(dm).name("/Reg/AccessPin").ft(StringTypeBuilder.builder()).build();
        Field quoted = FieldBuilder.with(dm).name("/Reg/QuotedFee").ft(NumberTypeBuilder.builder()).build();
        FieldBuilder.with(dm).name("/Reg/PaidFee").ft(NumberTypeBuilder.builder()).build();
        Field seat   = FieldBuilder.with(dm).name("/Reg/SeatBlock").ft(StringTypeBuilder.builder()).build();
        Field passport = FieldBuilder.with(dm).name("/Reg/PassportExpiry").ft(DateTypeBuilder.builder()).build();
        Field eventDate = FieldBuilder.with(dm).name("/Reg/EventDate").ft(DateTypeBuilder.builder()).build();
        Field reason = FieldBuilder.with(dm).name("/Reg/DeclineReason").ft(StringTypeBuilder.builder()).build();
        Field phone  = FieldBuilder.with(dm).name("/Reg/Phone").ft(StringTypeBuilder.builder()).build();
        FieldBuilder.with(dm).name("/Reg/Email").ft(StringTypeBuilder.builder()).build();
        Field decision = FieldBuilder.with(dm).name("/Reg/Decision").ft(EnumerationTypeBuilder.builder().modify(b -> b.values(List.of(
                EnumerationType.EnumValue.builder().value("CONFIRMED").label(Demos.text("Confirmed", "Bestätigt")).build(),
                EnumerationType.EnumValue.builder().value("WAITLISTED").label(Demos.text("Waitlisted", "Warteliste")).build(),
                EnumerationType.EnumValue.builder().value("DECLINED").label(Demos.text("Declined", "Abgelehnt")).build())))).build();

        // String slicing: ticket code must come from the EU region (first two characters).
        rule(dm, "/Reg/TicketRegionRule", ticket,
                "FieldFilled(TicketCode) And RangeAsString(TicketCode, 1, 2) != \"EU\"", "TICKET_NOT_EU", Severity.ERROR,
                "Ticket code must be issued in the EU region (EU-...).", "Ticketcode muss aus der EU-Region stammen (EU-...).");

        // Regex (cross-language-safe subset).
        rule(dm, "/Reg/PromoFormatRule", promo,
                "FieldFilled(PromoCode) And [PromoCode] PatternViolated \"[A-Z]{2}[0-9]{4}\"", "PROMO_FORMAT", Severity.ERROR,
                "Promo code must be 2 letters followed by 4 digits.", "Promocode: 2 Buchstaben gefolgt von 4 Ziffern.");

        // String length.
        rule(dm, "/Reg/PinLengthRule", pin,
                "FieldFilled(AccessPin) And Length(AccessPin) != 5", "PIN_LENGTH", Severity.ERROR,
                "Access PIN must be exactly 5 characters.", "Zugangs-PIN muss genau 5 Zeichen haben.");

        // Tolerance comparison (first-class operator): quoted vs. paid fee.
        rule(dm, "/Reg/FeeMismatchRule", quoted,
                "[QuotedFee] DiffersWithToleranceRange5 [PaidFee]", "FEE_MISMATCH", Severity.WARNING,
                "Paid fee differs from the quote by more than 5.", "Gezahlte Gebühr weicht um mehr als 5 vom Angebot ab.");

        // Value-list membership: some seat blocks are sold out.
        rule(dm, "/Reg/SeatSoldOutRule", seat,
                "FieldValueIncludedInValueList(SeatBlock, \"VIP\", \"BACKSTAGE\", \"PRESS\")", "SEAT_SOLD_OUT", Severity.ERROR,
                "That seat block is sold out.", "Dieser Sitzblock ist ausverkauft.");

        // Date arithmetic: passport must stay valid for at least 6 months from today.
        rule(dm, "/Reg/PassportValidityRule", passport,
                "FieldFilled(PassportExpiry) And DifferenceInMonths(Today, PassportExpiry) < 6", "PASSPORT_EXPIRES_SOON", Severity.ERROR,
                "Passport must be valid for at least 6 more months.", "Reisepass muss noch mindestens 6 Monate gültig sein.");

        // Date vs. Today: the event must be in the future.
        rule(dm, "/Reg/EventPastRule", eventDate,
                "FieldFilled(EventDate) And [EventDate] < Today", "EVENT_IN_PAST", Severity.ERROR,
                "The event date has already passed.", "Das Veranstaltungsdatum liegt in der Vergangenheit.");

        // Enumeration comparison + conditional requiredness.
        rule(dm, "/Reg/DeclineReasonRule", reason,
                "[Decision] == \"DECLINED\" And FieldNotFilled(DeclineReason)", "REASON_REQUIRED", Severity.ERROR,
                "A reason is required when a registration is declined.", "Bei Ablehnung ist eine Begründung erforderlich.");

        // "At least one of N" quantifier: at least one contact channel.
        rule(dm, "/Reg/ContactRule", phone,
                "NoFieldFilled(Phone, Email)", "CONTACT_REQUIRED", Severity.ERROR,
                "Provide at least one contact: phone or email.", "Mindestens einen Kontakt angeben: Telefon oder E-Mail.");

        return dm;
    }

    private static void rule(DocumentModel dm, String name, Field errorField, String condition, String code,
            Severity severity, String en, String de) {
        RuleBuilder.with(dm).name(name).field(errorField).modify(rb -> rb
                .errorCondition(condition).errorCode(code).severity(severity)
                .errorMessage(Demos.text(en, de))).build();
    }
}
