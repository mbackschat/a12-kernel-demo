package com.example.a12demo.purchaseorder;

import java.util.Locale;

import com.mgmtp.a12.kernel.md.model.a12internal.DocumentModel;
import com.mgmtp.a12.kernel.md.model.a12internal.Field;
import com.mgmtp.a12.kernel.md.model.a12internal.LocalizedTextMapBuilder;
import com.mgmtp.a12.kernel.md.model.api.IRule.Severity;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.DocumentModelBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.FieldBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.RuleBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.BooleanTypeBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.DateTypeBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.NumberTypeBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.StringTypeBuilder;

/**
 * Builds a small "Purchase Order" {@link DocumentModel} entirely in code, using the kernel's model
 * builder API. The model has one group ("Order") with a handful of fields and four validation rules
 * written in the A12 DSL. Each rule states the <em>error</em> scenario (the condition is true exactly
 * when the data is invalid).
 *
 * <p>The builder classes live in the kernel's {@code internal} (A12-internal) tier; they are kept
 * isolated in this single class so a kernel upgrade only ever touches one file.
 */
public final class PurchaseOrderModel {

    /** The document-model id; documents reference this id. */
    public static final String DM_ID = "purchase-order";

    private PurchaseOrderModel() {
    }

    public static DocumentModel build() {
        // builderWithDefaultsForTests() gives a valid header + modelConfig
        // (decimalSeparator=".", timeZone=Europe/Berlin, conditionLanguage=en_US,
        //  fieldRefByShortNameAllowed=true -> conditions may use short field names).
        DocumentModel dm = DocumentModelBuilder.builderWithDefaultsForTests().id(DM_ID).build();

        // Fields. Paths are root-absolute (leading "/"); the first field auto-creates the
        // root group "Order" (non-repeating). Field-type is set via the matching ft-builder.
        Field customerName = FieldBuilder.with(dm).name("/Order/CustomerName").ft(StringTypeBuilder.builder()).build();
        Field orderDate    = FieldBuilder.with(dm).name("/Order/OrderDate").ft(DateTypeBuilder.builder()).build();
        Field deliveryDate = FieldBuilder.with(dm).name("/Order/DeliveryDate").ft(DateTypeBuilder.builder()).build();
        Field quantity     = FieldBuilder.with(dm).name("/Order/Quantity").ft(NumberTypeBuilder.builder()).build();
        // UnitPrice allows up to 2 decimal places (otherwise NumberType defaults to integers only,
        // which would raise a *formal* error "value must be integer").
        FieldBuilder.with(dm).name("/Order/UnitPrice")
                .ft(NumberTypeBuilder.builder().modify(b -> b.maxFractionalDigits(2))).build();
        FieldBuilder.with(dm).name("/Order/Express").ft(BooleanTypeBuilder.builder()).build();

        // Rule 1 (ERROR): customer name is mandatory.
        rule(dm, "/Order/CustomerRequiredRule", customerName,
                "FieldNotFilled(CustomerName)", "CUSTOMER_REQUIRED", Severity.ERROR,
                "Customer name is required.");

        // Rule 2 (ERROR): quantity, if given, must be positive.
        rule(dm, "/Order/QuantityPositiveRule", quantity,
                "FieldFilled(Quantity) And [Quantity] <= 0", "QTY_POSITIVE", Severity.ERROR,
                "Quantity must be greater than 0.");

        // Rule 3 (ERROR): delivery date must not be before the order date.
        rule(dm, "/Order/DeliveryAfterOrderRule", deliveryDate,
                "FieldFilled(OrderDate) And FieldFilled(DeliveryDate) And [DeliveryDate] < [OrderDate]",
                "DELIVERY_BEFORE_ORDER", Severity.ERROR,
                "Delivery date must not be before the order date.");

        // Rule 4 (WARNING): unusually large quantity — flag, but still valid.
        rule(dm, "/Order/LargeOrderRule", quantity,
                "FieldFilled(Quantity) And [Quantity] > 1000", "LARGE_ORDER", Severity.WARNING,
                "Large quantity (> 1000) — please double-check.");

        return dm;
    }

    /** Adds a rule (named {@code ruleName}) whose error attaches to {@code errorField}. */
    private static void rule(DocumentModel dm, String ruleName, Field errorField, String condition, String code,
            Severity severity, String enText) {
        RuleBuilder.with(dm).name(ruleName).field(errorField).modify(rb -> rb
                .errorCondition(condition)
                .errorCode(code)
                .severity(severity)
                .errorMessage(new LocalizedTextMapBuilder().add(Locale.US, enText).build()))
                .build();
    }
}
