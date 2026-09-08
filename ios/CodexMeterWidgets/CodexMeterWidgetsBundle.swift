import SwiftUI
import WidgetKit

@main
struct CodexMeterWidgetsBundle: WidgetBundle {
    var body: some Widget {
        LatestModelsWidget()
        CodexMeterHomeWidget()
        FiveHourAccessoryWidget()
        WeeklyAccessoryWidget()
        DualAllowanceAccessoryWidget()
    }
}
