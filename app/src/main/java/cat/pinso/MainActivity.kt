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
    private var section = "Food"
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
        section = state?.getString("section") ?: "Food"
        filter = state?.getString("filter")?.let(Bowl::valueOf)
        render()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", page); outState.putString("filter", filter?.name)
        outState.putString("section", section)
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
        header.addView(text(if (section == "Weight") "Your cat’s weight" else when(page) { "History" -> "Every little bite"; "Insights" -> "Food at a glance"; else -> "Your cat’s bowls" }, 30f, ink, true))
        if (section == "Food") horizontal(header, *listOf("Bowls", "History", "Insights").map { name ->
            button(if (name == page) "• $name" else name) { page = name; render() }
        }.toTypedArray())
        root.addView(header)
        content = column().apply { setPadding(dp(20), dp(12), dp(20), dp(12)) }
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        if (section == "Weight") weights() else when (page) { "History" -> history(); "Insights" -> insights(); else -> bowls() }
        horizontal(root, *listOf("Food", "Weight").map { name ->
            button(if (name == section) "• $name" else name) { section = name; render() }
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
            if (title == "Today") dailyGoal(panel, start)
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
    private fun percent(fraction: Double) = java.text.NumberFormat.getPercentInstance().apply {
        maximumFractionDigits = 1
    }.format(fraction)

    private fun dailyGoal(panel: LinearLayout, start: Long) {
        val goals = store.goals()
        panel.addView(text("Daily food goal", 19f, ink, true))
        if (goals == null) {
            panel.addView(text("Set each food’s full daily allowance to see combined progress.", 15f, muted))
        } else {
            val end = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
            val today = rows.filter { it.first.time >= start && it.first.time < end }
            val dry = today.filter { it.first.bowl == Bowl.DRY }.sumOf { it.second.eaten }
            val wet = today.filter { it.first.bowl == Bowl.WET }.sumOf { it.second.eaten }
            val progress = goals.progress(dry, wet)
            val wetColor = Color.rgb(163, 83, 36)
            panel.addView(text("${percent(progress.total)} of daily food", 25f, ink, true))
            val bar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = shape(Color.rgb(226, 230, 225)); clipToOutline = true
                contentDescription = "Daily food goal: dry ${percent(progress.dry)}, wet ${percent(progress.wet)}, total ${percent(progress.total)}"
            }
            listOf(progress.dryBar to green, progress.wetBar to wetColor,
                progress.remainingBar to Color.rgb(226, 230, 225)).forEach { (fraction, color) ->
                if (fraction > 0) bar.addView(View(this).apply { setBackgroundColor(color) },
                    LinearLayout.LayoutParams(0, -1, fraction.toFloat()))
            }
            panel.addView(bar, LinearLayout.LayoutParams(-1, dp(24)).apply { topMargin = dp(8); bottomMargin = dp(8) })
            panel.addView(text("Dry ${percent(progress.dry)} · ${Food.grams(dry)} / ${Food.grams(goals.dryMg)} g", 15f, green, true))
            panel.addView(text("Wet ${percent(progress.wet)} · ${Food.grams(wet)} / ${Food.grams(goals.wetMg)} g", 15f, wetColor, true))
            panel.addView(text(if (progress.total > 1) "${percent(progress.total - 1)} above goal. Full bar colors show the relative contributions."
                else "${percent(1 - progress.total)} remaining to reach 100%", 14f, muted))
        }
        panel.addView(button(if (goals == null) "Set daily allowances" else "Edit daily allowances") { goalForm() })
    }

    private fun goalForm() {
        val goals = store.goals()
        val fields = column().apply { setPadding(dp(24), dp(8), dp(24), dp(16)) }
        fields.addView(text("Enter the full daily amount for each food as if it were the only food eaten. These are not the portions of a mixed meal.", 15f, muted))
        fun input(label: String, initial: Long?): EditText {
            fields.addView(text(label, 16f, ink, true))
            return EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                hint = "g per day"; contentDescription = label; setSingleLine()
                initial?.let { setText(Food.grams(it)) }
                fields.addView(this, LinearLayout.LayoutParams(-1, dp(60)))
            }
        }
        val dry = input("Dry food daily allowance (g)", goals?.dryMg)
        val wet = input("Wet food daily allowance (g)", goals?.wetMg)
        fields.addView(text("Example: 42 g of a 60 g dry allowance plus 60 g of a 200 g wet allowance = 70% + 30% = 100%.", 14f, muted))
        val error = text("", 14f, Color.rgb(170, 50, 40)); fields.addView(error)
        val dialog = AlertDialog.Builder(this).setTitle("Daily food allowances")
            .setView(ScrollView(this).apply { addView(fields) }).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    store.saveGoals(FoodGoals(Food.parse(dry.text.toString()), Food.parse(wet.text.toString())))
                    dialog.dismiss(); render()
                } catch (e: IllegalArgumentException) { error.text = e.message }
                catch (_: android.database.SQLException) { error.text = "Could not save the allowances. Please try again." }
            }
        }
        dialog.show()
    }
    private fun weights() {
        val entries = store.weights().reversed()
        val summary = card(Color.rgb(230, 236, 244))
        summary.addView(text("Latest measurement", 17f, muted))
        summary.addView(text(entries.firstOrNull()?.let { "${Food.grams(it.catGrams)} kg" } ?: "No weight yet", 36f, ink, true))
        entries.firstOrNull()?.let { summary.addView(text(date(it.time), 14f, muted)) }
        if (entries.size >= 2) {
            val change = entries[0].catGrams - entries[1].catGrams
            summary.addView(text("${if (change > 0) "+" else ""}${Food.grams(change)} kg since previous measurement", 14f, muted))
        }
        horizontal(summary, button("Weigh directly") { weightForm(WeightMethod.DIRECT) },
            button("By difference") { weightForm(WeightMethod.DIFFERENCE) })
        content.addView(summary)
        content.addView(text("Weight history", 22f, ink, true))
        if (entries.isEmpty()) content.addView(text("Enter your cat’s weight, or weigh yourself holding the cat and then without the cat. All weights are in kilograms.", 16f, muted))
        entries.forEach { entry ->
            val panel = card()
            panel.addView(text("${Food.grams(entry.catGrams)} kg", 26f, ink, true))
            panel.addView(text(date(entry.time), 14f, muted))
            panel.addView(text(if (entry.method == WeightMethod.DIRECT) "Direct measurement" else "By difference: ${Food.grams(entry.firstGrams)} − ${Food.grams(entry.personGrams)} kg", 15f, muted))
            if (entry.note.isNotBlank()) panel.addView(text(entry.note, 15f))
            horizontal(panel, button("Edit") { weightForm(entry.method, entry) }, button("Delete") {
                AlertDialog.Builder(this).setTitle("Delete this weight entry?")
                    .setMessage("${Food.grams(entry.catGrams)} kg · ${date(entry.time)}")
                    .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
                        try { store.deleteWeight(entry); render() }
                        catch (_: android.database.SQLException) { problem("Could not delete the entry. Please try again.") }
                    }.show()
            })
            content.addView(panel)
        }
    }

    private fun weightForm(method: WeightMethod, existing: CatWeight? = null) {
        var timestamp = existing?.time ?: System.currentTimeMillis()
        val fields = column().apply { setPadding(dp(24), dp(8), dp(24), dp(16)) }
        fun input(label: String, initial: Long?): EditText {
            fields.addView(text(label, 16f, ink, true))
            return EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                hint = "kg"; contentDescription = label; textSize = 26f; setSingleLine()
                initial?.let { setText(Food.grams(it)) }
                fields.addView(this, LinearLayout.LayoutParams(-1, dp(60)))
            }
        }
        val first = input(if (method == WeightMethod.DIRECT) "Cat’s weight (kg)" else "You holding the cat (kg)", existing?.firstGrams)
        val person = if (method == WeightMethod.DIFFERENCE) input("You without the cat (kg)", existing?.personGrams) else null
        val preview = text("", 18f, green, true)
        fields.addView(preview)
        lateinit var timeButton: Button
        timeButton = button(date(timestamp)) {
            val c = Calendar.getInstance().apply { timeInMillis = timestamp }
            DatePickerDialog(this, { _, year, month, day ->
                TimePickerDialog(this, { _, hour, minute ->
                    c.set(year, month, day, hour, minute, 0); c.set(Calendar.MILLISECOND, 0)
                    timestamp = c.timeInMillis; timeButton.text = date(timestamp)
                }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(this)).show()
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }
        fields.addView(text("When", 15f, ink, true)); fields.addView(timeButton)
        val note = EditText(this).apply {
            hint = "Note (optional)"; setText(existing?.note ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        fields.addView(note)
        fun entry() = CatWeight(existing?.id ?: 0, timestamp, method, parseKg(first.text.toString()),
            person?.let { parseKg(it.text.toString()) } ?: 0, note.text.toString().trim()).also { it.validate() }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                try { preview.text = "Cat: ${Food.grams(entry().catGrams)} kg"; preview.setTextColor(green) }
                catch (e: IllegalArgumentException) { preview.text = e.message }
            }
        }
        first.addTextChangedListener(watcher); person?.addTextChangedListener(watcher)
        val dialog = AlertDialog.Builder(this).setTitle(if (method == WeightMethod.DIRECT) "Weigh cat directly" else "Weigh by difference")
            .setView(ScrollView(this).apply { addView(fields) }).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    require(timestamp <= System.currentTimeMillis()) { "Choose a time that is not in the future." }
                    store.saveWeight(entry()); dialog.dismiss(); render()
                } catch (e: IllegalArgumentException) { preview.text = e.message; preview.setTextColor(Color.rgb(170, 50, 40)) }
                catch (_: android.database.SQLException) { preview.text = "Could not save. Please try again." }
            }
        }
        dialog.show()
    }

    private fun parseKg(value: String): Long = try { Food.parse(value) }
        catch (e: IllegalArgumentException) { throw IllegalArgumentException(e.message?.replace("grams", "kilograms")?.replace("10,000 g", "500 kg")) }

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
            val json = FoodStore(applicationContext).use { Backup.encode(it.backup()) }
            val output = contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open the selected file.")
            output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }) { Toast.makeText(this, "Backup exported", Toast.LENGTH_LONG).show() }
        else backupWork("Reading backup…", {
            val input = contentResolver.openInputStream(uri) ?: error("Cannot read the selected file.")
            input.use(Backup::read)
        }) { entries ->
            AlertDialog.Builder(this).setTitle("Replace all food and weight history?")
                .setMessage("This backup contains ${entries.events.size} food entries and ${entries.weights.size} weight entries. Importing replaces ALL current food and weight history and daily food allowances, including empty logs or unset goals. Export a backup first if you want to keep your current records. This cannot be undone.")
                .setNegativeButton("Cancel", null).setPositiveButton("Replace and import") { _, _ ->
                    backupWork("Importing backup…", {
                        FoodStore(applicationContext).use { it.restore(entries) }
                    }) { render(); Toast.makeText(this, "Food and weight history imported", Toast.LENGTH_LONG).show() }
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
