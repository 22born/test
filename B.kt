import java.time.*

class MedicationScheduleResolver {
    fun resolve(request: ScheduleRequest): ScheduleResult {
        TODO("Implement medication schedule resolution")
    }
}

data class ScheduleRequest(
    val medications: List<MedicationRule>,
    val constraints: List<SeparationConstraint>,
    val rangeStart: Instant,
    val rangeEndExclusive: Instant,
    val defaultZone: ZoneId,
    val travelWindows: List<TravelWindow> = emptyList()
)

data class MedicationRule(
    val id: String,
    val dosage: String,
    val timing: DoseTimingRule,
    val activeStartDate: LocalDate,
    val activeEndDateExclusive: LocalDate? = null,
    val pauseWindows: List<InstantWindow> = emptyList(),
    val taper: List<TaperPhase> = emptyList()
)

sealed class DoseTimingRule {
    data class FixedTimes(val times: List<LocalTime>) : DoseTimingRule()
    data class TimesPerDay(val count: Int, val windows: List<LocalTimeWindow>) : DoseTimingRule()
    data class EveryHours(val hours: Int, val anchor: Instant) : DoseTimingRule()
}

data class SeparationConstraint(
    val a: String,
    val b: String,
    val minMinutes: Long
)

data class TravelWindow(
    val startDate: LocalDate,
    val endDateExclusive: LocalDate,
    val zone: ZoneId
)

data class TaperPhase(
    val startDate: LocalDate,
    val endDateExclusive: LocalDate,
    val dosage: String
)

data class InstantWindow(val start: Instant, val endExclusive: Instant)
data class LocalTimeWindow(val start: LocalTime, val endExclusive: LocalTime)

data class ScheduleResult(
    val doses: List<ScheduledDose>,
    val diagnostics: List<String>
)

data class ScheduledDose(
    val medicationId: String,
    val scheduledAt: Instant?,
    val intendedLocalDateTime: LocalDateTime,
    val status: DoseStatus,
    val dosage: String?
)

enum class DoseStatus {
    Scheduled,
    SkippedDueToPause,
    SkippedDueToConflict,
    NeedsReview
}
