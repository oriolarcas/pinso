package cat.pinso

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.ByteArrayOutputStream

object Backup {
    const val MAX_BYTES = 16 * 1024 * 1024

    fun encode(events: List<Event>): String {
        validate(events)
        val array = JSONArray()
        events.forEach { e ->
            array.put(JSONObject().put("id", e.id).put("bowl", e.bowl.name)
                .put("action", e.action.name).put("timestamp", e.time)
                .put("measuredMg", e.measured).put("addedMg", e.added).put("note", e.note))
        }
        return JSONObject().put("format", "pinso-backup").put("version", 1)
            .put("events", array).toString(2).also {
                require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup exceeds the 16 MB limit." }
            }
    }

    fun read(input: InputStream): List<Event> {
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

    fun decode(json: String): List<Event> {
        try {
            val root = JSONObject(json)
            require(root.getString("format") == "pinso-backup") { "This is not a Pinso backup." }
            require(integer(root, "version") == 1L) { "This backup version is not supported." }
            val array = root.getJSONArray("events")
            return List(array.length()) { i ->
                val e = array.getJSONObject(i)
                val note = e.get("note")
                require(note is String) { "Invalid note." }
                Event(integer(e, "id"), Bowl.valueOf(e.getString("bowl")),
                    Action.valueOf(e.getString("action")), integer(e, "timestamp"),
                    integer(e, "measuredMg"), integer(e, "addedMg"), note)
            }.also(::validate)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid backup: ${e.message ?: "unrecognized contents"}", e)
        }
    }

    private fun integer(obj: JSONObject, key: String): Long {
        val value = obj.get(key)
        require(value is Int || value is Long) { "$key must be a whole number." }
        return (value as Number).toLong()
    }

    private fun validate(events: List<Event>) {
        require(events.all { it.id > 0 && it.id < Long.MAX_VALUE && it.time >= 0 }) { "Invalid entry ID or timestamp." }
        require(events.map { it.id }.toSet().size == events.size) { "Duplicate entry IDs." }
        events.forEach {
            require(it.measured in 0..10_000_000L && it.added in 0..10_000_000L) { "Invalid food weight." }
            require(it.action != Action.ADD || it.measured == 0L) { "An addition cannot contain a measurement." }
            require(it.action !in listOf(Action.WEIGH, Action.DISCARD) || it.added == 0L) { "Unexpected added food." }
        }
        Food.replay(events)
    }
}
