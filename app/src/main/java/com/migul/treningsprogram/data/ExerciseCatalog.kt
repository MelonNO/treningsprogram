package com.migul.treningsprogram.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

data class DbExerciseEntry(
    val id: String,
    val name: String,
    val normalizedName: String,
    val tokenSet: Set<String>,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val equipment: String?,
    val level: String?,
    val category: String?,
    val images: List<String>,
    val instructions: List<String>
)

data class CatalogEntry(
    val imageUrl: String,
    val instructions: String,
    val equipment: List<String>,
    val muscleGroup: String
)

private data class JsonExercise(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("level") val level: String? = null,
    @SerializedName("mechanic") val mechanic: String? = null,
    @SerializedName("equipment") val equipment: String? = null,
    @SerializedName("primaryMuscles") val primaryMuscles: List<String> = emptyList(),
    @SerializedName("secondaryMuscles") val secondaryMuscles: List<String> = emptyList(),
    @SerializedName("instructions") val instructions: List<String> = emptyList(),
    @SerializedName("category") val category: String? = null,
    @SerializedName("images") val images: List<String> = emptyList()
)

object ExerciseCatalog {

    @Volatile private var _entries: List<DbExerciseEntry> = emptyList()
    @Volatile private var _byId: Map<String, DbExerciseEntry> = emptyMap()
    @Volatile private var _byNormalized: Map<String, DbExerciseEntry> = emptyMap()
    @Volatile private var initialized = false

    val entries: List<DbExerciseEntry> get() = _entries
    val byId: Map<String, DbExerciseEntry> get() = _byId

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                val json = context.assets.open("exercise_db/exercises.json").bufferedReader().readText()
                val type = object : TypeToken<List<JsonExercise>>() {}.type
                val raw: List<JsonExercise> = Gson().fromJson(json, type)
                val mapped = raw.map { it.toDbEntry() }
                _entries = mapped
                _byId = mapped.associateBy { it.id }
                _byNormalized = mapped.associateBy { it.normalizedName }
                initialized = true
            } catch (_: Exception) {
                // graceful: catalog stays empty
            }
        }
    }

    fun getImageSource(dbId: String, frame: Int = 0): String =
        "file:///android_asset/exercise_db/images/$dbId/$frame.jpg"

    fun queryCatalog(
        muscle: String? = null,
        equipment: String? = null,
        level: String? = null,
        category: String? = null
    ): List<DbExerciseEntry> = _entries.filter { e ->
        (muscle == null || e.primaryMuscles.any { it.contains(muscle, ignoreCase = true) }
                || e.secondaryMuscles.any { it.contains(muscle, ignoreCase = true) }) &&
        (equipment == null || (e.equipment != null && e.equipment.contains(equipment, ignoreCase = true))) &&
        (level == null || (e.level != null && e.level.equals(level, ignoreCase = true))) &&
        (category == null || (e.category != null && e.category.equals(category, ignoreCase = true)))
    }

    fun findByNormalizedName(normalized: String): DbExerciseEntry? = _byNormalized[normalized]

    fun getDbEntry(dbId: String): DbExerciseEntry? = _byId[dbId]

    // --- Backward-compat surface for ExerciseInfoBottomSheet ---

    fun getEntry(name: String): CatalogEntry? = staticEntries[name]

    fun getImageUrl(name: String): String? {
        // Try DB first: exact static mapping
        val staticUrl = staticEntries[name]?.imageUrl
        if (!staticUrl.isNullOrBlank()) return staticUrl
        return null
    }

    fun getInstructions(name: String): String? =
        staticEntries[name]?.instructions?.takeIf { it.isNotBlank() }

    // --- Static instruction catalog (for ExerciseInfoBottomSheet) ---

    private val staticEntries: Map<String, CatalogEntry> = mapOf(
        "Bench Press" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Barbell_Bench_Press_-_Medium_Grip/0.jpg",
            instructions = "Lie flat on the bench with feet on the floor. Grip the bar just wider than shoulder-width, lower it under control to mid-chest, then press back to lockout.",
            equipment = listOf("Barbell", "Bench"), muscleGroup = "Chest"
        ),
        "Incline Dumbbell Press" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Incline_Dumbbell_Press/0.jpg",
            instructions = "Set the bench to 30-45°. Press the dumbbells up from shoulder height, keeping elbows at roughly 45° to your torso, and lower with control.",
            equipment = listOf("Dumbbells", "Incline bench"), muscleGroup = "Chest"
        ),
        "Cable Flyes" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Low_Cable_Crossover/0.jpg",
            instructions = "Stand between two cable stacks with handles set at chest height. Keeping a slight elbow bend, bring your hands together in a wide arc in front of your chest.",
            equipment = listOf("Cable machine"), muscleGroup = "Chest"
        ),
        "Push-ups" to CatalogEntry(
            imageUrl = "",
            instructions = "Start in a high plank with hands just wider than shoulder-width. Lower your chest toward the floor while keeping your body rigid, then press back up.",
            equipment = listOf("Bodyweight"), muscleGroup = "Chest"
        ),
        "Deadlift" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Barbell_Deadlift/0.jpg",
            instructions = "Stand with the bar over your mid-foot, hip-width apart. Hinge at the hips and bend the knees to grip the bar, brace your core, then drive through the floor to stand tall.",
            equipment = listOf("Barbell"), muscleGroup = "Back"
        ),
        "Barbell Row" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Bent_Over_Barbell_Row/0.jpg",
            instructions = "Hinge forward until your torso is nearly parallel to the floor. Pull the bar into your lower ribcage, driving elbows back, then lower with control.",
            equipment = listOf("Barbell"), muscleGroup = "Back"
        ),
        "Pull-ups" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Wide-Grip_Rear_Pull-Up/0.jpg",
            instructions = "Hang from the bar with an overhand grip, slightly wider than shoulders. Pull your chin above the bar by driving elbows toward your hips, then lower under control.",
            equipment = listOf("Pull-up bar"), muscleGroup = "Back"
        ),
        "Lat Pulldown" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Wide-Grip_Lat_Pulldown/0.jpg",
            instructions = "Sit at the machine and grab the bar wide. Pull the bar to your upper chest while leaning back slightly and driving elbows down, then extend arms under control.",
            equipment = listOf("Cable machine", "Lat pulldown bar"), muscleGroup = "Back"
        ),
        "Cable Row" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Seated_Cable_Rows/0.jpg",
            instructions = "Sit at the cable row station with knees slightly bent. Pull the handle to your lower abdomen, keeping your torso upright and squeezing your shoulder blades.",
            equipment = listOf("Cable machine"), muscleGroup = "Back"
        ),
        "Squat" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Barbell_Squat/0.jpg",
            instructions = "Rest the bar across your upper traps. Feet shoulder-width apart, brace your core, then sit into the squat until thighs are parallel to the floor. Drive through your heels to stand.",
            equipment = listOf("Barbell", "Squat rack"), muscleGroup = "Legs"
        ),
        "Leg Press" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Leg_Press/0.jpg",
            instructions = "Sit in the machine with feet hip-width on the platform. Lower the platform until knees reach 90°, then press back to just short of lockout.",
            equipment = listOf("Leg press machine"), muscleGroup = "Legs"
        ),
        "Romanian Deadlift" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Romanian_Deadlift/0.jpg",
            instructions = "Hold the bar at hip level, feet hip-width. Push hips back and lower the bar along your legs, keeping a neutral spine, until you feel a deep hamstring stretch. Return by driving hips forward.",
            equipment = listOf("Barbell"), muscleGroup = "Legs"
        ),
        "Leg Curl" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Seated_Leg_Curl/0.jpg",
            instructions = "Adjust the pad so it rests just above your heels. Curl your legs toward your glutes through a full range of motion, then lower slowly.",
            equipment = listOf("Leg curl machine"), muscleGroup = "Legs"
        ),
        "Leg Extension" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Leg_Extensions/0.jpg",
            instructions = "Sit in the machine with the pad across your shins. Extend your legs to full lockout, hold briefly, then lower under control.",
            equipment = listOf("Leg extension machine"), muscleGroup = "Legs"
        ),
        "Calf Raise" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Standing_Calf_Raises/0.jpg",
            instructions = "Stand with balls of feet on the edge of a step or calf raise platform. Rise as high as possible onto your toes, hold, then lower your heels below the platform.",
            equipment = listOf("Calf raise machine or step"), muscleGroup = "Legs"
        ),
        "Overhead Press" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Barbell_Shoulder_Press/0.jpg",
            instructions = "Stand with the bar at collar-bone height, grip just outside shoulder-width. Press the bar overhead to full lockout, then lower under control.",
            equipment = listOf("Barbell"), muscleGroup = "Shoulders"
        ),
        "Lateral Raises" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Side_Lateral_Raise/0.jpg",
            instructions = "Hold dumbbells at your sides. With a slight elbow bend, raise your arms out to the side to shoulder height, leading with your elbows. Lower slowly.",
            equipment = listOf("Dumbbells"), muscleGroup = "Shoulders"
        ),
        "Face Pulls" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Face_Pull/0.jpg",
            instructions = "Attach a rope to a high pulley. Pull the rope toward your face, flaring elbows out and externally rotating your shoulders. Hold for a beat and return slowly.",
            equipment = listOf("Cable machine", "Rope attachment"), muscleGroup = "Shoulders"
        ),
        "Bicep Curl" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Dumbbell_Bicep_Curl/0.jpg",
            instructions = "Stand holding dumbbells with palms facing forward. Keeping upper arms still, curl the weights toward your shoulders, squeeze at the top, then lower under control.",
            equipment = listOf("Dumbbells"), muscleGroup = "Arms"
        ),
        "Hammer Curl" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Hammer_Curls/0.jpg",
            instructions = "Hold dumbbells with a neutral (hammer) grip. Keeping elbows pinned to your sides, curl the weights up with thumbs pointing to the ceiling, then lower slowly.",
            equipment = listOf("Dumbbells"), muscleGroup = "Arms"
        ),
        "Tricep Pushdown" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Triceps_Pushdown/0.jpg",
            instructions = "Attach a bar or rope to a high cable. Keeping upper arms at your sides, push the attachment down to full elbow extension, then return slowly.",
            equipment = listOf("Cable machine"), muscleGroup = "Arms"
        ),
        "Skull Crusher" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/EZ-Bar_Skullcrusher/0.jpg",
            instructions = "Lie on a flat bench and hold an EZ bar above your chest. Lower the bar toward your forehead by bending the elbows only, then press back to lockout.",
            equipment = listOf("EZ bar or barbell", "Bench"), muscleGroup = "Arms"
        ),
        "Plank" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Plank/0.jpg",
            instructions = "Rest on forearms and toes with your body forming a straight line from head to heels. Squeeze your glutes, brace your core, and breathe steadily.",
            equipment = listOf("Bodyweight"), muscleGroup = "Core"
        ),
        "Crunches" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Crunches/0.jpg",
            instructions = "Lie on your back with knees bent and feet flat. Place hands behind your head. Contract your abs to curl your shoulders off the floor, then lower under control.",
            equipment = listOf("Bodyweight"), muscleGroup = "Core"
        ),
        "Russian Twist" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Russian_Twist/0.jpg",
            instructions = "Sit with knees bent and feet elevated, leaning back slightly. Rotate your torso side to side, tapping the floor (or a weight) on each side while keeping your core braced.",
            equipment = listOf("Bodyweight", "Optional: dumbbell or plate"), muscleGroup = "Core"
        ),
        "Leg Raises" to CatalogEntry(
            imageUrl = "file:///android_asset/exercise_db/images/Flat_Bench_Lying_Leg_Raise/0.jpg",
            instructions = "Lie flat on your back with legs straight. Keeping lower back pressed to the floor, raise your legs to 90° then lower slowly without letting feet touch the ground.",
            equipment = listOf("Bodyweight"), muscleGroup = "Core"
        ),
        "Outdoor Run" to CatalogEntry(imageUrl = "", instructions = "Run outdoors at your target pace. Warm up with 5 minutes easy jogging, hit your target pace, and cool down with easy jogging at the end.", equipment = listOf("Running shoes"), muscleGroup = "Cardio"),
        "Interval Run" to CatalogEntry(imageUrl = "", instructions = "Alternate between hard efforts (80-95% max HR) and easy recovery jogs. Warm up and cool down for 5-10 minutes around the intervals.", equipment = listOf("Running shoes"), muscleGroup = "Cardio"),
        "Tempo Run" to CatalogEntry(imageUrl = "", instructions = "Run at a comfortably hard pace you could sustain for about an hour — roughly 80-85% max HR. Great for building lactate threshold.", equipment = listOf("Running shoes"), muscleGroup = "Cardio"),
        "Easy Jog" to CatalogEntry(imageUrl = "", instructions = "Run at a conversational pace (60-70% max HR). You should be able to speak in full sentences. Focus on easy effort and recovery.", equipment = listOf("Running shoes"), muscleGroup = "Cardio"),
        "Treadmill Run" to CatalogEntry(imageUrl = "", instructions = "Set speed and incline on the treadmill to match your target pace. Avoid holding the rails — maintain upright posture and natural arm swing.", equipment = listOf("Treadmill"), muscleGroup = "Cardio"),
        "Stationary Bike" to CatalogEntry(imageUrl = "", instructions = "Set resistance so pedaling is moderately challenging. Maintain a cadence of 80-100 RPM. Keep your back straight and core lightly engaged.", equipment = listOf("Stationary bike"), muscleGroup = "Cardio"),
        "Burpees" to CatalogEntry(imageUrl = "", instructions = "From standing, squat down and place hands on the floor, jump feet back to a plank, perform a push-up, jump feet forward to hands, then jump up with arms overhead.", equipment = listOf("Bodyweight"), muscleGroup = "Cardio"),
        "Mountain Climbers" to CatalogEntry(imageUrl = "file:///android_asset/exercise_db/images/Mountain_Climbers/0.jpg", instructions = "Start in a high plank. Drive one knee toward your chest, then quickly switch legs in a running motion, keeping hips level and core tight throughout.", equipment = listOf("Bodyweight"), muscleGroup = "Cardio"),
        "High Knees" to CatalogEntry(imageUrl = "", instructions = "Run in place, driving each knee as high as possible while pumping your arms. Aim for a fast, light-footed pace and keep your core engaged.", equipment = listOf("Bodyweight"), muscleGroup = "Cardio"),
        "Jump Rope" to CatalogEntry(imageUrl = "", instructions = "Jump with feet together or alternating, keeping jumps small and efficient. Land softly on the balls of your feet with a slight knee bend.", equipment = listOf("Jump rope"), muscleGroup = "Cardio")
    )

    // --- Normalization helper (shared with resolver) ---

    fun normalizeName(raw: String): String {
        var s = raw.lowercase().trim()
        // strip parenthetical clarifications before further processing
        s = s.replace(Regex("\\(.*?\\)"), " ")
        // strip punctuation (keep letters, digits, spaces)
        s = s.replace(Regex("[^a-z0-9\\s]"), " ")
        // expand abbreviations (word-boundary sensitive)
        val abbrevs = mapOf(
            "\\bdb\\b" to "dumbbell",
            "\\bdumbell\\b" to "dumbbell",   // common single-l typo
            "\\bbb\\b" to "barbell",
            "\\bkb\\b" to "kettlebell",
            "\\bohp\\b" to "overhead press",
            "\\brdl\\b" to "romanian deadlift",
            "\\bsldl\\b" to "stiff leg deadlift",
            "\\bcg\\b" to "close grip",
            "\\bsa\\b" to "single arm",
            "\\balt\\b" to "alternate",
            "\\bext\\b" to "extension",
            "\\bbw\\b" to "bodyweight",
            "\\bez\\b" to "ez bar",
            "\\bcgbp\\b" to "close grip bench press"
        )
        abbrevs.forEach { (pattern, replacement) ->
            s = s.replace(Regex(pattern), replacement)
        }
        // normalize equipment synonyms
        s = s.replace(Regex("\\bbody only\\b"), "bodyweight")
        s = s.replace(Regex("\\bbody weight\\b"), "bodyweight")
        s = s.replace(Regex("\\bresistance band[s]?\\b"), "band")
        s = s.replace(Regex("\\bbands\\b"), "band")
        // collapse whitespace
        s = s.replace(Regex("\\s+"), " ").trim()
        // basic plural stemming: strip trailing 's' from tokens ≥ 5 chars not ending in 'ss'
        s = s.split(" ").joinToString(" ") { token ->
            if (token.length >= 5 && token.endsWith('s') && !token.endsWith("ss"))
                token.dropLast(1)
            else
                token
        }
        return s
    }

    private fun JsonExercise.toDbEntry(): DbExerciseEntry {
        val norm = normalizeName(name)
        return DbExerciseEntry(
            id = id,
            name = name,
            normalizedName = norm,
            tokenSet = norm.split(" ").filter { it.length > 1 }.toSet(),
            primaryMuscles = primaryMuscles,
            secondaryMuscles = secondaryMuscles,
            equipment = equipment?.lowercase()?.let {
                when {
                    it.contains("body only") || it.contains("body weight") -> "bodyweight"
                    else -> it
                }
            },
            level = level?.lowercase(),
            category = category?.lowercase(),
            images = images,
            instructions = instructions
        )
    }
}
