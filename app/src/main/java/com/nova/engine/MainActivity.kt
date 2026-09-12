package com.nova.engine

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/** Nova V2 application host: editor, project import, script authoring, animation authoring and playable runtime. */
class MainActivity : Activity(), NovaV2EditorView.Actions {
    private lateinit var editor: NovaV2EditorView
    private var gameView: NovaV2GameView? = null
    private val projectStore by lazy { V2ProjectIO(this) }
    private var activeScene = V2Scene()
    private var scriptSource = "# Nova Script V2\n# Commands: set, add, mul, move, flag, print\n\nset speed 220\nprint Hello from Nova"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.rgb(10, 13, 19)
        window.navigationBarColor = Color.rgb(10, 13, 19)
        editor = NovaV2EditorView(this, this)
        setContentView(editor)
    }

    override fun onResume() { super.onResume(); editor.invalidate() }

    override fun openAssets() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, REQUEST_ASSETS)
    }

    override fun openScript() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0) }
        val name = EditText(this).apply { hint = "player.nova"; setSingleLine(true); setText("player.nova") }
        val editor = EditText(this).apply {
            setText(scriptSource); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); setTextSize(13f)
            minLines = 16; gravity = android.view.Gravity.TOP; setBackgroundColor(Color.rgb(18, 23, 31)); setPadding(16, 16, 16, 16)
        }
        box.addView(name, LinearLayout.LayoutParams(-1, 52)); box.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f))
        AlertDialog.Builder(this).setTitle("Nova Script Editor").setView(box)
            .setNegativeButton("CANCEL", null)
            .setNeutralButton("TEST", null)
            .setPositiveButton("SAVE", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        scriptSource = editor.text.toString(); val file = projectStore.scriptFile(name.text.toString()); file.writeText(scriptSource)
                        Toast.makeText(this, "Script saved: ${file.name}", Toast.LENGTH_SHORT).show(); dialog.dismiss()
                    }
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        val result = V2ScriptRuntime().execute(editor.text.toString())
                        val message = if (result.errors.isEmpty()) "PASS\n${result.logs.joinToString("\n")}" else "ERROR\n${result.errors.joinToString("\n")}"
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
            }.show()
    }

    override fun openAnimation() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 4, 24, 0) }
        fun field(hint: String, value: String) = EditText(this).apply { this.hint = hint; setText(value); setSingleLine(true) }
        val clip = field("Animation name", "Idle"); val frames = field("Frames: player_0|player_1|player_2", "player_0|player_1|player_2|player_3"); val fps = field("FPS", "12")
        box.addView(clip); box.addView(frames); box.addView(fps)
        AlertDialog.Builder(this).setTitle("Sprite / Animation Maker").setView(box).setNegativeButton("CANCEL", null).setPositiveButton("CREATE") { _, _ ->
            val values = frames.text.toString().split("|").map { it.trim() }.filter { it.isNotEmpty() }
            val made = V2AnimationMaker().create(clip.text.toString().ifBlank { "Idle" }, values, fps.text.toString().toFloatOrNull() ?: 12f)
            activeScene.nodes.firstOrNull { it.id == editorSelectedId() }?.animation = made.name
            Toast.makeText(this, "Animation ${made.name}: ${made.frames.size} frames @ ${String.format(Locale.US, "%.1f", made.fps)} FPS", Toast.LENGTH_SHORT).show()
        }.show()
    }

    override fun runGame(scene: V2Scene) {
        activeScene = scene
        gameView = NovaV2GameView(this, scene)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(gameView)
        gameView?.requestFocus()
    }

    override fun buildProject(scene: V2Scene) {
        val config = V2BuildConfig(debug = true, aab = false)
        val report = V2BuildValidator().validate(config, scene, projectStore.allFiles())
        if (!report.ok) { Toast.makeText(this, "Build blocked:\n${report.errors.joinToString("\n")}", Toast.LENGTH_LONG).show(); return }
        projectStore.writeScene(scene)
        val warnings = if (report.warnings.isEmpty()) "" else "\nWarnings:\n${report.warnings.joinToString("\n")}"
        AlertDialog.Builder(this).setTitle("Nova Build V2")
            .setMessage("Project validated successfully.$warnings\n\nThe Android APK/AAB compiler is Gradle/CI. This button validates and opens the project's CI build workflow; it does not fake an APK inside the editor.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("OPEN CI BUILD") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/surafel5509-del/2D-game-engine/actions/workflows/android.yml")))
            }.show()
    }

    fun exitGame() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        editor = NovaV2EditorView(this, this)
        setContentView(editor)
    }

    override fun onBackPressed() {
        if (gameView != null && gameView === (window.decorView.findViewById<View>(android.R.id.content) as? View)) { exitGame(); return }
        super.onBackPressed()
    }

    private fun editorSelectedId(): String? = activeScene.nodes.firstOrNull()?.id

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_ASSETS || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<android.net.Uri>()
        data.data?.let(uris::add); data.clipData?.let { clip -> for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri }
        var imported = 0
        uris.distinct().forEach { uri ->
            val name = queryDisplayName(uri) ?: "asset_${System.currentTimeMillis()}"
            if (projectStore.importFile(uri, name) != null) imported++
        }
        Toast.makeText(this, "Imported $imported asset(s) into Nova project", Toast.LENGTH_SHORT).show()
    }

    private fun queryDisplayName(uri: android.net.Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    companion object { private const val REQUEST_ASSETS = 4102 }
}
