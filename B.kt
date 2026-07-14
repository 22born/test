Task
Implement:
class MedicationScheduleResolver {
    fun resolve(request: ScheduleRequest): ScheduleResult
}
The exact internal representation is up to you, but the public contract must support:
data class ScheduleRequest(
    val medications: List<MedicationRule>,
    val constraints: List<SeparationConstraint>,
    val rangeStart: ZonedDateTime,
    val rangeEndExclusive: ZonedDateTime,
    val defaultZone: ZoneId,
    val travelWindows: List<TravelWindow> = emptyList()
)

data class ScheduleResult(
    val doses: List<ScheduledDose>,
    val diagnostics: List<String>
)

data class ScheduledDose(
    val medicationId: String,
    val scheduledAt: ZonedDateTime,
    val dosage: String,
    val status: DoseStatus
)

enum class DoseStatus {
    Scheduled,
    SkippedDueToPause,
    SkippedDueToConflict,
    NeedsReview
}
