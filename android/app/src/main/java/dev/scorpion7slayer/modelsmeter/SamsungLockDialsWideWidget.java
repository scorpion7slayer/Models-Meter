package dev.scorpion7slayer.modelsmeter;

import dev.scorpion7slayer.modelsmeter.SamsungLockWidgetSupport;

/* JADX INFO: loaded from: classes.dex */
public final class SamsungLockDialsWideWidget extends SamsungLockWidgetProvider {
    @Override // dev.scorpion7slayer.modelsmeter.SamsungLockWidgetProvider
    protected SamsungLockWidgetSupport.Shape shape() {
        return SamsungLockWidgetSupport.Shape.WIDE;
    }

    @Override // dev.scorpion7slayer.modelsmeter.SamsungLockWidgetProvider
    protected SamsungLockWidgetSupport.Style style() {
        return SamsungLockWidgetSupport.Style.DIALS;
    }
}
