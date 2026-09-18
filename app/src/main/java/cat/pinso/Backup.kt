package cat.pinso

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.ByteArrayOutputStream

object Backup {
    const val MAX_BYTES = 16 * 1024 * 1024

    fun encode(data: BackupData): String {
        validate(data)
        val array = JSONArray()
        data.events.forEach { e ->
            array.put(JSONObject().put("id", e.id).put("bowl", e.bowl.name)
                .put("action", e.action.name).put("timestamp", e.time)
                .put("measuredMg", e.measured).put("addedMg", e.added).put("note", e.note).put("hasMeasurement", e.hasMeasurement))
        }
        val weights = JSONArray()
        data.weights.forEach { w ->
            weights.put(JSONObject().put("id", w.id).put("timestamp", w.time).put("method", w.method.name)
                .put("firstGrams", w.firstGrams).put("personGrams", w.personGrams).put("note", w.note))
        }
        val goals = data.foodGoals?.let { JSONObject().put("dryMg", it.dryMg).put("wetMg", it.wetMg) }
        return JSONObject().put("format", "pinso-backup").put("version", 4)
            .put("foodGoals", goals ?: JSONObject.NULL)
            .put("events", array).put("weights", weights).toString(2).also {
                require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup exceeds the 16 MB limit." }
            }
    }

    fun read(input: InputStream): BackupData {
        val buffer = ByteArray(8192)
        val output = ByteArrayOutputStream()
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "Backup exceeds the 16 MB limit." }
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        return decode(bytes.toString(Charsets.UTF_8))
    }

    fun decode(json: String): BackupData {
        try {
            val root = JSONObject(json)
            require(root.getString("format") == "pinso-backup") { "This is not a Pinso backup." }
            require(integer(root, "version") == 4L) { "This backup version is not supported. Export a new backup from the current app." }
            val goalObject = root.get("foodGoals")
            val goals = if (goalObject == JSONObject.NULL) null else {
                require(goalObject is JSONObject) { "Invalid food goals." }
                FoodGoals(integer(goalObject, "dryMg"), integer(goalObject, "wetMg"))
            }
            val array = root.getJSONArray("events")
            val events = List(array.length()) { i ->
                val e = array.getJSONObject(i)
                val note = e.get("note")
                require(note is String) { "Invalid note." }
                val hasMeasurement = e.get("hasMeasurement")
                require(hasMeasurement is Boolean) { "Invalid measurement flag." }
                Event(integer(e, "id"), Bowl.valueOf(e.getString("bowl")),
                    Action.valueOf(e.getString("action")), integer(e, "timestamp"),
                    integer(e, "measuredMg"), integer(e, "addedMg"), note, hasMeasurement)
            }
            val weights = root.getJSONArray("weights")
            return BackupData(events, List(weights.length()) { i ->
                val w = weights.getJSONObject(i)
                val note = w.get("note")
                require(note is String) { "Invalid note." }
                CatWeight(integer(w, "id"), integer(w, "timestamp"), WeightMethod.valueOf(w.getString("method")),
                    integer(w, "firstGrams"), integer(w, "personGrams"), note)
            }, goals).also(::validate)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid backup: ${e.message ?: "unrecognized contents"}", e)
        }
    }

    private fun integer(obj: JSONObject, key: String): Long {
        val value = obj.get(key)
        require(value is Int || value is Long) { "$key must be a whole number." }
        return (value as Number).toLong()
    }

    fun validate(data: BackupData) {
        data.foodGoals?.validate()
        val events = data.events
        require(data.weights.all { it.id > 0 && it.id < Long.MAX_VALUE }) { "Invalid weight entry ID." }
        require(data.weights.map { it.id }.toSet().size == data.weights.size) { "Duplicate weight entry IDs." }
        data.weights.forEach { it.validate() }
        require(events.all { it.id > 0 && it.id < Long.MAX_VALUE && it.time >= 0 }) { "Invalid entry ID or timestamp." }
        require(events.map { it.id }.toSet().size == events.size) { "Duplicate entry IDs." }
        events.forEach {
            require(it.measured in 0..10_000_000L && it.added in 0..10_000_000L) { "Invalid food weight." }
            require(it.action !in listOf(Action.WEIGH, Action.DISCARD) || it.added == 0L) { "Unexpected added food." }
        }
        Food.replay(events)
    }
}
