import SwiftUI
import WidgetKit

@main
struct ModelsMeterWidgetsBundle: WidgetBundle {
    var body: some Widget {
        LatestModelsWidget()
        ModelsMeterHomeWidget()
        FiveHourAccessoryWidget()
        WeeklyAccessoryWidget()
        DualAllowanceAccessoryWidget()
    }
}
