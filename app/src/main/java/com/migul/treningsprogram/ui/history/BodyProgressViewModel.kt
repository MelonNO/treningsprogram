package com.migul.treningsprogram.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.migul.treningsprogram.data.backup.BackupScheduler
import com.migul.treningsprogram.data.db.dao.BodyMeasurementDao
import com.migul.treningsprogram.data.db.dao.BodyMetricDao
import com.migul.treningsprogram.data.db.entity.BodyMeasurement
import com.migul.treningsprogram.data.db.entity.BodyMetric
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.domain.BodyComposition
import com.migul.treningsprogram.domain.BodyProgressCharts
import com.migul.treningsprogram.domain.BodyProgressRange
import com.migul.treningsprogram.domain.DateRangeFilter
import com.migul.treningsprogram.domain.DayBoundary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Body-progress batch 2026-08-04 (brief 02) — the Body tab.
 *
 * Deliberately its own ViewModel rather than more surface on the already-large shared
 * [HistoryViewModel]: this tab shares no state with Recap/Stats/Progress/History.
 *
 * One flow layer only (`combine(...).stateIn`) — never a stateIn feeding another stateIn, which is
 * the v1.24.1 F3 trap that produces a permanently dead UI on device while JVM tests stay green.
 */
@HiltViewModel
class BodyProgressViewModel @Inject constructor(
    private val bodyMeasurementDao: BodyMeasurementDao,
    private val bodyMetricDao: BodyMetricDao,
    private val backupScheduler: BackupScheduler,
    private val prefs: PreferencesManager
) : ViewModel() {

    // ── Profile ───────────────────────────────────────────────────────────────────────────────
    // Read through on every access: App Settings can change height/sex while this tab is alive, and
    // body fat is DERIVED, so the charts must pick the new profile up on the next emission.

    val sex: String get() = prefs.sex
    val heightCm: Float get() = prefs.heightCm

    /** Decision 2: a male profile never sees a hip field anywhere. */
    val showHip: Boolean get() = BodyComposition.needsHip(prefs.sex)

    /** A5: without height + sex there is no body fat to show — the UI points at App Settings. */
    val profileComplete: Boolean
        get() = BodyComposition.isKnownSex(prefs.sex) && prefs.heightCm > 0f

    // ── Time window (decision 7 / A6) ─────────────────────────────────────────────────────────

    /** Selected preset; null while a custom calendar range is active. */
    val rangePreset = MutableStateFlow<BodyProgressRange.Preset?>(BodyProgressRange.DEFAULT)

    /** Custom calendar range (A6); null unless the user picked one. */
    val customRange = MutableStateFlow<DateRangeFilter.Range?>(null)

    fun selectPreset(preset: BodyProgressRange.Preset) {
        customRange.value = null
        rangePreset.value = preset
    }

    fun selectCustomRange(range: DateRangeFilter.Range) {
        rangePreset.value = null
        customRange.value = range
    }

    /** A profile-change ping so the derived body-fat series recomputes without a data write. */
    private val profileRevision = MutableStateFlow(0)

    fun onProfileMayHaveChanged() { profileRevision.value += 1 }

    // ── Chart data ────────────────────────────────────────────────────────────────────────────

    private val measurements: Flow<List<BodyMeasurement>> = bodyMeasurementDao.getAll()
    private val metrics: Flow<List<BodyMetric>> = bodyMetricDao.getAll()

    /**
     * The effective window: the active preset resolved against today, or the custom range.
     * Recomputed per emission so a preset stays correct across a midnight rollover.
     */
    private fun effectiveRange(
        preset: BodyProgressRange.Preset?,
        custom: DateRangeFilter.Range?
    ): DateRangeFilter.Range? = when {
        custom != null -> custom
        preset != null -> BodyProgressRange.rangeFor(preset, DayBoundary.todayEpochDay())
        else -> null
    }

    val series: StateFlow<BodyProgressCharts.Series> =
        combine(
            measurements, metrics, rangePreset, customRange, profileRevision
        ) { ms, mx, preset, custom, _ ->
            BodyProgressCharts.build(
                measurements = ms,
                metrics = mx,
                range = effectiveRange(preset, custom),
                sex = prefs.sex,
                heightCm = prefs.heightCm.takeIf { it > 0f }
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            BodyProgressCharts.Series()
        )

    /** False = the 10 most recent entries; true = the whole history, so old entries are reachable. */
    val showAllEntries = MutableStateFlow(false)

    fun toggleShowAllEntries() { showAllEntries.value = !showAllEntries.value }

    /**
     * The deletable entry list inside the logging section (never range-windowed). Carries BOTH
     * kinds — body weight (including Home quick-adds) and girths — paired into one row per save.
     */
    val recentEntries: StateFlow<List<BodyProgressCharts.EntryRow>> =
        combine(measurements, metrics, profileRevision, showAllEntries) { ms, mx, _, showAll ->
            BodyProgressCharts.recentEntries(
                measurements = ms,
                metrics = mx,
                sex = prefs.sex,
                heightCm = prefs.heightCm.takeIf { it > 0f },
                limit = if (showAll) BodyProgressCharts.NO_LIMIT
                else BodyProgressCharts.DEFAULT_ENTRY_LIMIT
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Total number of logged entries, for the "Show all N" affordance. */
    val totalEntryCount: StateFlow<Int> =
        combine(measurements, metrics) { ms, mx ->
            BodyProgressCharts.recentEntries(ms, mx, prefs.sex, null, BodyProgressCharts.NO_LIMIT).size
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ── Logging ───────────────────────────────────────────────────────────────────────────────

    /**
     * Saves one user-facing entry. Every field is individually optional; weight goes to the
     * existing `body_measurements` series (the same rows the Home quick-add writes, so the two
     * paths stay one series) and the girths to `body_metrics`. Both rows share one timestamp, which
     * is what pairs them back up in [BodyProgressCharts.recentEntries].
     *
     * Returns false when there was nothing to save.
     */
    fun logEntry(weightKg: Float?, waistCm: Float?, neckCm: Float?, hipCm: Float?): Boolean {
        // Decision 2 again, enforced at the write: a male profile can never store a hip value even
        // if a stale view somehow supplied one.
        val hip = hipCm.takeIf { showHip }
        if (weightKg == null && waistCm == null && neckCm == null && hip == null) return false

        val now = System.currentTimeMillis()
        viewModelScope.launch {
            if (weightKg != null) {
                bodyMeasurementDao.insert(BodyMeasurement(dateMs = now, weightKg = weightKg))
            }
            val metric = BodyMetric(
                dateMs = now, waistCm = waistCm, neckCm = neckCm, hipCm = hip
            )
            if (!metric.isEmpty) bodyMetricDao.insert(metric)
            backupScheduler.requestBackup()
        }
        return true
    }

    /**
     * Deletes [part] of a listed entry.
     *
     * A listed entry can span two tables, so this covers all three cases the user can hit: a
     * body-weight row on its own (including one added from the Home screen), a girth row on its
     * own, and a combined save where only one half is wrong.
     *
     * Room's `@Delete` matches on the PRIMARY KEY, so reconstructing the entity from the row's id
     * is sufficient — the other fields are never consulted.
     */
    fun deleteEntry(
        row: BodyProgressCharts.EntryRow,
        part: BodyProgressCharts.DeletePart = BodyProgressCharts.DeletePart.ALL
    ) {
        viewModelScope.launch {
            if (part.removesWeight) row.measurementId?.let {
                bodyMeasurementDao.delete(
                    BodyMeasurement(id = it, dateMs = row.dateMs, weightKg = row.weightKg ?: 0f)
                )
            }
            if (part.removesMetrics) row.metricId?.let {
                bodyMetricDao.delete(
                    BodyMetric(
                        id = it, dateMs = row.dateMs,
                        waistCm = row.waistCm, neckCm = row.neckCm, hipCm = row.hipCm
                    )
                )
            }
            backupScheduler.requestBackup()
        }
    }
}
