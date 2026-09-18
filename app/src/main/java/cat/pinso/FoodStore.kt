package cat.pinso

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class FoodStore(context: Context) : SQLiteOpenHelper(context, "pinso.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, bowl TEXT NOT NULL, action TEXT NOT NULL, time INTEGER NOT NULL, measured INTEGER NOT NULL, added INTEGER NOT NULL, note TEXT NOT NULL)")
        createWeights(db)
        createGoals(db)
    }
    private fun createWeights(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE weights (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, method TEXT NOT NULL, firstGrams INTEGER NOT NULL, personGrams INTEGER NOT NULL, note TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createWeights(db)
        if (oldVersion < 3) createGoals(db)
    }
    private fun createGoals(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE food_goals (id INTEGER PRIMARY KEY CHECK(id=1), dryMg INTEGER NOT NULL, wetMg INTEGER NOT NULL)")
    }
    fun goals(): FoodGoals? = readableDatabase.rawQuery("SELECT dryMg, wetMg FROM food_goals WHERE id=1", null).use {
        if (it.moveToFirst()) FoodGoals(it.getLong(0), it.getLong(1)) else null
    }
    fun saveGoals(goals: FoodGoals) {
        goals.validate()
        writableDatabase.execSQL("INSERT OR REPLACE INTO food_goals(id,dryMg,wetMg) VALUES(1,?,?)", arrayOf(goals.dryMg, goals.wetMg))
    }
    fun weights(): List<CatWeight> = readableDatabase.rawQuery("SELECT * FROM weights ORDER BY time, id", null).use { c ->
        buildList { while (c.moveToNext()) add(CatWeight(c.getLong(0), c.getLong(1), WeightMethod.valueOf(c.getString(2)), c.getLong(3), c.getLong(4), c.getString(5))) }
    }
    private fun weightValues(weight: CatWeight) = ContentValues().apply {
        put("time", weight.time); put("method", weight.method.name); put("firstGrams", weight.firstGrams)
        put("personGrams", weight.personGrams); put("note", weight.note)
    }
    fun saveWeight(weight: CatWeight) {
        weight.validate()
        if (weight.id == 0L) writableDatabase.insertOrThrow("weights", null, weightValues(weight))
        else writableDatabase.update("weights", weightValues(weight), "id=?", arrayOf(weight.id.toString()))
    }
    fun deleteWeight(weight: CatWeight) { writableDatabase.delete("weights", "id=?", arrayOf(weight.id.toString())) }
    fun backup(): BackupData {
        val db = writableDatabase
        db.beginTransaction()
        try { return BackupData(events(), weights(), goals()).also { db.setTransactionSuccessful() } }
        finally { db.endTransaction() }
    }
    fun events(): List<Event> = readableDatabase.rawQuery("SELECT * FROM events ORDER BY time, id", null).use { c ->
        buildList { while (c.moveToNext()) add(Event(c.getLong(0), Bowl.valueOf(c.getString(1)), Action.valueOf(c.getString(2)), c.getLong(3), c.getLong(4), c.getLong(5), c.getString(6))) }
    }
    fun save(event: Event) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val candidate = event.copy(id = event.id.takeIf { it > 0 } ?: Long.MAX_VALUE)
            Food.replay(events().filterNot { event.id > 0 && it.id == event.id } + candidate)
            val values = ContentValues().apply {
                put("bowl", event.bowl.name); put("action", event.action.name); put("time", event.time)
                put("measured", event.measured); put("added", event.added); put("note", event.note)
            }
            if (event.id == 0L) db.insertOrThrow("events", null, values)
            else db.update("events", values, "id=?", arrayOf(event.id.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun delete(event: Event) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            Food.replay(events().filterNot { it.id == event.id })
            db.delete("events", "id=?", arrayOf(event.id.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun restore(data: BackupData) {
        Backup.validate(data)
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("events", null, null)
            db.delete("weights", null, null)
            db.delete("food_goals", null, null)
            data.foodGoals?.let { saveGoals(it) }
            data.events.forEach { event ->
                db.insertOrThrow("events", null, ContentValues().apply {
                    put("id", event.id); put("bowl", event.bowl.name); put("action", event.action.name)
                    put("time", event.time); put("measured", event.measured)
                    put("added", event.added); put("note", event.note)
                })
            }
            data.weights.forEach { weight ->
                db.insertOrThrow("weights", null, weightValues(weight).apply { put("id", weight.id) })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
