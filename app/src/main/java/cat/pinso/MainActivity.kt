package cat.pinso

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.View
import android.widget.*
import java.text.DateFormat
import java.util.Calendar

class MainActivity : Activity() {
    private lateinit var store: FoodStore
    private lateinit var content: LinearLayout
    private var page = "Bowls"
    private var filter: Bowl? = null
    private val ink = Color.rgb(35, 51, 42)
    private val muted = Color.rgb(97, 111, 102)
    private val green = Color.rgb(56, 103, 80)
    private val backgroundColor = Color.rgb(247, 247, 240)
    private var rows = emptyList<Pair<Event, Result>>()
    private var backupProgress: AlertDialog? = null
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        store = FoodStore(this)
        page = state?.getString("page") ?: "Bowls"
        filter = state?.getString("filter")?.let(Bowl::valueOf)
        render()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", page); outState.putString("filter", filter?.name)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { backupProgress?.dismiss(); store.close(); super.onDestroy() }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun text(value: String, size: Float = 16f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(5), 0, dp(5))
    }
    private fun shape(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(22).toFloat() }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setTextColor(green); textSize = 14f
        minHeight = dp(48); setOnClickListener { action() }
    }
    private fun card(color: Int = Color.WHITE): LinearLayout = column().apply {
        background = shape(color); setPadding(dp(20), dp(16), dp(20), dp(18))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
    }
    private fun horizontal(parent: LinearLayout, vararg buttons: View) {
        parent.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            buttons.forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        })
    }
    private fun render() {
        rows = Food.replay(store.events())
        val root = column().apply { setBackgroundColor(backgroundColor) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        val header = column().apply { setPadding(dp(24), dp(12), dp(24), dp(8)) }
        horizontal(header, text("PINSO", 13f, green, true), button("Backups") { backups() })
        header.addView(text(when(page) { "History" -> "Every little bite"; "Insights" -> "Food at a glance"; else -> "Your cat’s bowls" }, 30f, ink, true))
        header.addView(text(when(page) { "History" -> "Measurements, meals, and fresh starts."; "Insights" -> "Separate bowls. A clearer picture."; else -> "A little care, one meal at a time." }, 15f, muted))
        root.addView(header)
        content = column().apply { setPadding(dp(20), dp(12), dp(20), dp(12)) }
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        when (page) { "History" -> history(); "Insights" -> insights(); else -> bowls() }
        horizontal(root, *listOf("Bowls", "History", "Insights").map { name ->
            button(if (name == page) "• $name" else name) { page = name; render() }
        }.toTypedArray())
        setContentView(root)
        root.requestApplyInsets()
    }
    private fun bowls() {
        Bowl.entries.forEach { bowl ->
            val entries = rows.filter { it.first.bowl == bowl }
            val current = entries.lastOrNull()?.second?.after ?: 0L
            val panel = card(if (bowl == Bowl.DRY) Color.rgb(232, 239, 225) else Color.rgb(249, 235, 221))
            panel.addView(text(bowl.title, 21f, ink, true))
            panel.addView(text("${Food.grams(current)} g", 44f, ink, true))
            panel.addView(text("Last recorded amount", 13f, muted))
            panel.addView(text(entries.lastOrNull()?.let { "Updated ${date(it.first.time)}" } ?: "Ready for the first meal", 13f, muted))
            horizontal(panel, button("+ Add") { form(bowl, Action.ADD) }, button("Weigh") { form(bowl, Action.WEIGH) })
            horizontal(panel, button("Replace") { form(bowl, Action.REPLACE) }, button("Throw away") { form(bowl, Action.DISCARD) })
            panel.addView(button("View history") { filter = bowl; page = "History"; render() })
            content.addView(panel)
        }
        content.addView(text("Use food weight only: tare your scale with the empty bowl. Amounts stay at the last recorded value until you log another action.", 14f, muted))
    }
    private fun history() {
        horizontal(content, button(if (filter == null) "• All" else "All") { filter = null; render() },
            *Bowl.entries.map { bowl -> button(if (filter == bowl) "• ${bowl.title}" else bowl.title) { filter = bowl; render() } }.toTypedArray())
        val visible = rows.filter { filter == null || it.first.bowl == filter }.reversed()
        if (visible.isEmpty()) content.addView(text("No activity yet. Add the food currently in a bowl to get started.", 18f, muted))
        visible.forEach { (event, result) ->
            val panel = card()
            panel.addView(text("${event.action.title} · ${event.bowl.title}", 19f, ink, true))
            panel.addView(text(date(event.time), 13f, muted))
            if (event.action != Action.ADD) panel.addView(text("Measured ${Food.grams(event.measured)} g · Eaten ≈ ${Food.grams(result.eaten)} g"))
            if (event.action == Action.ADD || event.action == Action.REPLACE) panel.addView(text("Added ${Food.grams(event.added)} g"))
            if (event.action == Action.REPLACE || event.action == Action.DISCARD) panel.addView(text("Discarded ${Food.grams(result.discarded)} g"))
            panel.addView(text("Bowl: ${Food.grams(result.before)} → ${Food.grams(result.after)} g", 15f, green, true))
            if (event.note.isNotBlank()) panel.addView(text(event.note, 14f, muted))
            horizontal(panel, button("Edit") { form(event.bowl, event.action, event) }, button("Delete") {
                AlertDialog.Builder(this).setTitle("Delete this entry?").setMessage("Later bowl balances and consumption will be recalculated.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
                        try { store.delete(event); render() } catch (e: IllegalArgumentException) { problem("Cannot delete: ${e.message}") }
                    }.show()
            })
            content.addView(panel)
        }
    }
    private fun insights() {
        val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val weekStart = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_YEAR, -6) }.timeInMillis
        listOf("Today" to start, "Last 7 days" to weekStart, "All time" to 0L).forEach { (title, since) ->
            val panel = card(); panel.addView(text(title, 23f, ink, true))
            Bowl.entries.forEach { bowl ->
                val entries = rows.filter { it.first.bowl == bowl && it.first.time >= since }
                panel.addView(text(bowl.title, 17f, green, true))
                panel.addView(text("≈ ${Food.grams(entries.sumOf { it.second.eaten })} g eaten", 25f, ink, true))
                panel.addView(text("${Food.grams(entries.sumOf { it.first.added })} g added  ·  ${Food.grams(entries.sumOf { it.second.discarded })} g discarded", 14f, muted))
            }
            content.addView(panel)
        }
        content.addView(text("Consumption is an estimate from weight differences, recorded at measurement time. It may include spills or moisture loss, and may span more than one day.", 14f, muted))
    }
    private fun date(time: Long) = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(java.util.Date(time))
    private fun problem(message: String) { AlertDialog.Builder(this).setTitle("Check this entry").setMessage(message).setPositiveButton("OK", null).show() }
    private fun backups() {
        AlertDialog.Builder(this).setTitle("Backups")
            .setItems(arrayOf("Export backup…", "Import backup…")) { _, which ->
                try {
                    if (which == 0) startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                        putExtra(Intent.EXTRA_TITLE, "pinso-${java.time.LocalDate.now()}.json")
                    }, 101)
                    else startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                    }, 102)
                } catch (_: android.content.ActivityNotFoundException) { problem("No file picker is available on this device.") }
            }.setNegativeButton("Cancel", null).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || requestCode !in listOf(101, 102)) return
        val uri = data?.data ?: return
        if (requestCode == 101) backupWork("Exporting backup…", {
            val json = FoodStore(applicationContext).use { Backup.encode(it.events()) }
            val output = contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open the selected file.")
            output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }) { Toast.makeText(this, "Backup exported", Toast.LENGTH_LONG).show() }
        else backupWork("Reading backup…", {
            val input = contentResolver.openInputStream(uri) ?: error("Cannot read the selected file.")
            input.use(Backup::read)
        }) { entries ->
            AlertDialog.Builder(this).setTitle("Replace all food history?")
                .setMessage("This backup contains ${entries.size} entries. Importing will replace all ${store.events().size} current entries in both bowls. Export a backup first if you want to keep them. This cannot be undone.")
                .setNegativeButton("Cancel", null).setPositiveButton("Replace and import") { _, _ ->
                    backupWork("Importing backup…", {
                        FoodStore(applicationContext).use { it.restore(entries) }
                    }) { render(); Toast.makeText(this, "Imported ${entries.size} entries", Toast.LENGTH_LONG).show() }
                }.show()
        }
    }

    private fun <T> backupWork(message: String, work: () -> T, done: (T) -> Unit) {
        val progress = AlertDialog.Builder(this).setMessage(message).setCancelable(false).create()
        backupProgress = progress
        progress.show()
        Thread {
            val result = runCatching(work)
            runOnUiThread {
                if (!isDestroyed && !isFinishing) {
                    progress.dismiss()
                    backupProgress = null
                    result.fold(done) { error ->
                        AlertDialog.Builder(this).setTitle("Backup failed")
                            .setMessage(error.message ?: "Could not access the backup file. Please try again.")
                            .setPositiveButton("OK", null).show()
                    }
                }
            }
        }.start()
    }
    private fun form(bowl: Bowl, action: Action, existing: Event? = null) {
        var timestamp = existing?.time ?: System.currentTimeMillis()
        var refreshPreview: () -> Unit = {}
        val fields = column().apply { setPadding(dp(24), dp(8), dp(24), dp(16)) }
        fields.addView(text("${bowl.title} · food weight only", 14f, muted))
        fun input(label: String, value: String): EditText {
            fields.addView(text(label, 15f, ink, true))
            return EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setSingleLine(); hint = "0 g"; textSize = 26f; setText(value)
                contentDescription = label
                fields.addView(this, LinearLayout.LayoutParams(-1, dp(60)))
            }
        }
        val measured = if (action != Action.ADD) input("Amount found (g)", existing?.let { Food.grams(it.measured) } ?: "") else null
        val added = if (action == Action.ADD || action == Action.REPLACE) input(if (action == Action.REPLACE) "New food (g)" else "Amount to add (g)", existing?.let { Food.grams(it.added) } ?: "") else null
        if (action == Action.REPLACE) fields.addView(text("The old remainder is discarded before adding the new food.", 13f, muted))
        val whenButton = button(date(timestamp)) {
            val c = Calendar.getInstance().apply { timeInMillis = timestamp }
            DatePickerDialog(this, { _, year, month, day ->
                TimePickerDialog(this, { _, hour, minute ->
                    c.set(year, month, day, hour, minute, 0); c.set(Calendar.MILLISECOND, 0)
                    timestamp = c.timeInMillis
                    (fields.findViewWithTag<View>("timestamp") as Button).text = date(timestamp)
                    refreshPreview()
                }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(this)).show()
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }.apply { tag = "timestamp" }
        fields.addView(text("When", 15f, ink, true)); fields.addView(whenButton)
        val note = EditText(this).apply { hint = "Note (optional)"; setText(existing?.note ?: ""); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }
        fields.addView(note)
        val preview = text("", 14f, green); fields.addView(preview)
        fun event() = Event(existing?.id ?: 0, bowl, action, timestamp, measured?.let { Food.parse(it.text.toString()) } ?: 0, added?.let { Food.parse(it.text.toString()) } ?: 0, note.text.toString().trim())
        fun calculate(): Result {
            val candidate = event().copy(id = existing?.id ?: Long.MAX_VALUE)
            return Food.replay(store.events().filterNot { existing != null && it.id == existing.id } + candidate).first { it.first.id == candidate.id }.second
        }
        fun updatePreview() {
            try {
                val result = calculate()
                preview.setTextColor(green)
                preview.text = "Bowl after: ${Food.grams(result.after)} g\nEstimated eaten: ${Food.grams(result.eaten)} g · Discarded: ${Food.grams(result.discarded)} g"
            } catch (e: IllegalArgumentException) { preview.text = e.message }
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreview() }
            override fun afterTextChanged(s: Editable?) = Unit
        }
        measured?.addTextChangedListener(watcher); added?.addTextChangedListener(watcher)
        refreshPreview = { updatePreview() }
        val dialog = AlertDialog.Builder(this).setTitle("${if (existing != null) "Edit · " else ""}${action.title}")
            .setView(ScrollView(this).apply { addView(fields) }).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    require(timestamp <= System.currentTimeMillis()) { "Choose a time that is not in the future." }
                    store.save(event()); dialog.dismiss(); render()
                    Toast.makeText(this, "Saved · edit or delete in History", Toast.LENGTH_SHORT).show()
                } catch (e: IllegalArgumentException) { preview.text = e.message; preview.setTextColor(Color.rgb(170, 50, 40)) }
                catch (_: android.database.SQLException) { preview.text = "Could not save. Please try again; your entry is still here." }
            }
        }
        dialog.show()
        updatePreview()
    }
}
