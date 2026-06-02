package com.example.a12demo;

import java.util.List;

import com.mgmtp.a12.kernel.md.model.a12internal.DocumentModel;
import com.mgmtp.a12.kernel.md.model.a12internal.fieldtypes.EnumerationType;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.DocumentModelBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.FieldBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.GroupBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.EnumerationTypeBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.NumberTypeBuilder;
import com.mgmtp.a12.kernel.md.model.internal.betterbuilders.ft.StringTypeBuilder;

/**
 * A small <b>Subscription</b> model used by the typed-accessor showcase: a {@code Subscription} group with a
 * {@code Tier} enumeration and a repeating {@code Addons} list. Its serialized JSON
 * ({@code src/main/resources/models/subscription.dm.json}, produced by {@link SubscriptionModelWriter}) is the
 * input to the build-time typed-accessor generator.
 */
public final class SubscriptionModel {

    public static final String DM_ID = "subscription";

    private SubscriptionModel() {
    }

    public static DocumentModel build() {
        DocumentModel dm = DocumentModelBuilder.builderWithDefaultsForTests().id(DM_ID).build();
        FieldBuilder.with(dm).name("/Subscription/Tier").ft(EnumerationTypeBuilder.builder().modify(b -> b.values(List.of(
                EnumerationType.EnumValue.builder().value("BASIC").label(Demos.text("Basic", "Basis")).build(),
                EnumerationType.EnumValue.builder().value("PRO").label(Demos.text("Pro", "Pro")).build(),
                EnumerationType.EnumValue.builder().value("ENTERPRISE").label(Demos.text("Enterprise", "Enterprise")).build())))).build();
        GroupBuilder.with(dm).name("/Subscription/Addons").repeat(20).build();
        FieldBuilder.with(dm).name("/Subscription/Addons/Name").ft(StringTypeBuilder.builder()).build();
        FieldBuilder.with(dm).name("/Subscription/Addons/MonthlyFee")
                .ft(NumberTypeBuilder.builder().modify(b -> b.maxFractionalDigits(2))).build();
        return dm;
    }
}
