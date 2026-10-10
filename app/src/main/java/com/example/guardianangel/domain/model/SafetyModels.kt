package com.example.guardianangel.domain.model

/**
 * Offense categories for safety modeling and Berkeley crime dataset normalization.
 *
 * Distinct categories ensure violent person crimes, property crimes, and disorder
 * calls are not treated interchangeably.
 */
enum class OffenseCategory(val displayName: String, val baseWeight: Float) {
    ViolentPerson("Violent person offense", 1.0f),
    SexualOffense("Sexual offense", 1.0f),
    Kidnapping("Kidnapping / abduction", 1.0f),
    Robbery("Robbery", 0.85f),
    Assault("Assault", 0.80f),
    PropertyOffense("Property offense", 0.35f),
    DisorderPublicSafety("Disorder / public safety", 0.20f),
    Unknown("Unclassified incident", 0.30f);

    companion object {
        fun fromCodeOrCategory(text: String): OffenseCategory {
            val upper = text.uppercase()
            return when {
                upper.contains("RAPE") || upper.contains("SEX") -> SexualOffense
                upper.contains("KIDNAP") || upper.contains("ABDUCT") -> Kidnapping
                upper.contains("ROBBERY") || upper.contains("HOLDUP") -> Robbery
                upper.contains("HOMICIDE") || upper.contains("MURDER") || upper.contains("SHOOTING") -> ViolentPerson
                upper.contains("ASSAULT") || upper.contains("BATTERY") || upper.contains("FIGHT") -> Assault
                upper.contains("BURGLARY") || upper.contains("THEFT") || upper.contains("LARCENY") ||
                    upper.contains("VEHICLE") || upper.contains("STOLEN") || upper.contains("VANDALISM") -> PropertyOffense
                upper.contains("DISTURB") || upper.contains("NOISE") || upper.contains("TRESPASS") ||
                    upper.contains("INTOX") || upper.contains("DISORDER") || upper.contains("SUSPICIOUS") -> DisorderPublicSafety
                else -> Unknown
            }
        }
    }
}

/**
 * Normalized incident record from Berkeley Police Department (BPD) or UC Berkeley UCPD.
 *
 * Privacy invariant: Exact victim names, private apartment numbers, and raw narrative
 * details are never retained or propagated to the UI.
 */
data class BerkeleyIncident(
    val sourceId: String,
    val sourceEventId: String,
    val jurisdiction: String, // e.g. "City of Berkeley BPD" vs "UC Berkeley UCPD"
    val eventTimeMillis: Long,
    val offenseCategory: OffenseCategory,
    val officialOffenseCode: String,
    val latitude: Double,
    val longitude: Double,
    val locationPrecision: String = "block-level",
    val agency: String,
    val retrievedAtMillis: Long,
)

/**
 * Privacy-preserving aggregated spatial safety cell (approx. 100–250 meters).
 *
 * Never displays exact incident pins to avoid doxxing victims or misrepresenting neighborhoods.
 */
data class CrimeCell(
    val cellId: String,
    val center: GeoPoint,
    val incidentCount: Int,
    val offenseCounts: Map<OffenseCategory, Int>,
    val weightedScore: Float,
    val lastReportedTimeMillis: Long,
    val radiusMeters: Float = 125f,
    val primaryOffense: OffenseCategory = OffenseCategory.Unknown,
    val jurisdiction: String = "City of Berkeley BPD",
)

/**
 * Verified safe haven types for transit safety.
 */
enum class SafeHavenType(val displayName: String) {
    PoliceStation("Police Station"),
    Hospital24x7("24/7 Emergency Hospital"),
    FireStation("Fire Station"),
    VerifiedPublicFacility("Verified Public Facility"),
    CommunityPartner("Community Partner SafeStop"),
}

data class SafeHavenPoi(
    val id: String,
    val name: String,
    val type: SafeHavenType,
    val location: GeoPoint,
    val address: String,
    val isOpen24Hours: Boolean = true,
    val openHoursDescription: String = "Open 24 hours",
    val isVerified: Boolean = true,
    val phone: String? = null,
)

/** Confidence in the environmental safety assessment. */
enum class DataConfidence(val label: String) {
    High("High confidence"),
    Moderate("Moderate confidence"),
    Limited("Limited safety data"),
}

/** Risk band for a evaluated walking route corridor. */
enum class RiskBand(val label: String) {
    Minimal("Lowest modeled exposure"),
    Low("Lower modeled exposure"),
    Moderate("Moderate modeled exposure"),
    Elevated("Higher reported exposure"),
    Severe("High exposure / caution"),
}

/** Segment assessment along a corridor polyline. */
data class SegmentAssessment(
    val start: GeoPoint,
    val end: GeoPoint,
    val segmentRisk: Float, // 0.0f (lowest) to 1.0f (highest)
    val lightingPercent: Int,
    val nearbyHavensCount: Int,
    val reportedExposure: String,
    val isWarning: Boolean = false,
    val warningMessage: String? = null,
)

/**
 * Comprehensive deterministic route evaluation output.
 */
data class RouteAssessment(
    val routeId: String,
    val providerRouteLabel: String,
    val durationMinutes: Int,
    val distanceMeters: Int,
    val safetyScore: Int, // 0 to 100 prototype index
    val riskBand: RiskBand,
    val confidence: DataConfidence,
    val dataFreshnessDays: Int,
    val crimeExposureScore: Float, // Lower is better
    val lightingCoveragePercent: Int,
    val weatherRiskScore: Float, // 0.0f (clear) to 1.0f (severe)
    val safeHavenCount: Int,
    val detourFromFastestMinutes: Int,
    val highlights: List<String>,
    val warnings: List<String>,
    val segmentAssessments: List<SegmentAssessment> = emptyList(),
    val sourceVersion: String = "Berkeley-2026.1",
)

/** Central category for Angel's live text status bubble. */
enum class StatusCategory {
    ListeningStatus,
    NavigationStatus,
    LocationStatus,
    SafetyAssessmentStatus,
    HomeStatus,
    EmergencyStatus,
}

data class AngelStatusMessage(
    val text: String,
    val category: StatusCategory,
    val timestampMillis: Long = System.currentTimeMillis(),
)
