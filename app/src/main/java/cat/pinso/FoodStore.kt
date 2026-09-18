package cat.pinso

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class FoodStore(context: Context) : SQLiteOpenHelper(context, "pinso.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, bowl TEXT NOT NULL, action TEXT NOT NULL, time INTEGER NOT NULL, measured INTEGER NOT NULL, added INTEGER NOT NULL, note TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
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

    fun restore(events: List<Event>) {
        Food.replay(events)
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("events", null, null)
            events.forEach { event ->
                db.insertOrThrow("events", null, ContentValues().apply {
                    put("id", event.id); put("bowl", event.bowl.name); put("action", event.action.name)
                    put("time", event.time); put("measured", event.measured)
                    put("added", event.added); put("note", event.note)
                })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
