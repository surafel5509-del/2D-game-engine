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
import android.widget.Toast
import java.util.Locale

/** Nova V2 application host: editor, project import, script authoring, animation authoring, play mode and CI build. */
class MainActivity : Activity(), NovaV2EditorView.Actions {
    private lateinit var editor: NovaV2EditorView
    private var gameView: NovaV2GameView? = null
    private var inGame = false
    private val projectStore by lazy { V2ProjectIO(this) }
    private var scriptSource = "# Nova Script V2\n# Commands: set, add, mul, move, flag, print\n\nset speed 220\nprint Hello from Nova"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.rgb(10, 13, 19)
        window.navigationBarColor = Color.rgb(10, 13, 19)
        showEditor()
    }

    private fun showEditor() { inGame = false; window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN); editor = NovaV2EditorView(this, this); setContentView(editor) }
    override fun onResume() { super.onResume(); if (::editor.isInitialized) editor.invalidate() }

    override fun openAssets() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }, REQUEST_ASSETS)
    }

    override fun openScript() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0) }
        val name = EditText(this).apply { hint = "player.nova"; setSingleLine(true); setText("player.nova") }
        val code = EditText(this).apply {
            setText(scriptSource); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); setTextSize(13f)
            minLines = 16; gravity = android.view.Gravity.TOP; setBackgroundColor(Color.rgb(18, 23, 31)); setPadding(16, 16, 16, 16)
        }
        box.addView(name, LinearLayout.LayoutParams(-1, 52)); box.addView(code, LinearLayout.LayoutParams(-1, 0, 1f))
        AlertDialog.Builder(this).setTitle("Nova Script Editor V2").setView(box).setNegativeButton("CANCEL", null).setNeutralButton("TEST", null).setPositiveButton("SAVE", null).create().also { dialog ->
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    scriptSource = code.text.toString(); val file = projectStore.scriptFile(name.text.toString()); file.writeText(scriptSource)
                    Toast.makeText(this, "Script saved: ${file.name}", Toast.LENGTH_SHORT).show(); dialog.dismiss()
                }
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    val result = V2ScriptRuntime().execute(code.text.toString())
                    val text = if (result.errors.isEmpty()) "PASS\n${result.logs.joinToString("\n")}" else "ERROR\n${result.errors.joinToString("\n")}"
                    Toast.makeText(this, text, Toast.LENGTH_LONG).show()
                }
            }
        }.show()
    }

    override fun openAnimation() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 4, 24, 0) }
        fun field(value: String) = EditText(this).apply { setText(value); setSingleLine(true) }
        val clip = field("Idle"); val frames = field("player_0|player_1|player_2|player_3"); val fps = field("12")
        box.addView(clip); box.addView(frames); box.addView(fps)
        AlertDialog.Builder(this).setTitle("Sprite / Animation Maker V2").setView(box).setNegativeButton("CANCEL", null).setPositiveButton("CREATE") { _, _ ->
            val values = frames.text.toString().split("|").map { it.trim() }.filter { it.isNotEmpty() }
            val made = V2AnimationMaker().create(clip.text.toString().ifBlank { "Idle" }, values, fps.text.toString().toFloatOrNull() ?: 12f)
            Toast.makeText(this, "Animation ${made.name}: ${made.frames.size} frames @ ${String.format(Locale.US, "%.1f", made.fps)} FPS", Toast.LENGTH_SHORT).show()
        }.show()
    }

    override fun runGame(scene: V2Scene) {
        projectStore.writeScene(scene)
        gameView = NovaV2GameView(this, scene); inGame = true
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN); setContentView(gameView); gameView?.requestFocus()
    }

    override fun buildProject(scene: V2Scene) {
        val config = V2BuildConfig(debug = true, aab = false); val report = V2BuildValidator().validate(config, scene, projectStore.allFiles())
        if (!report.ok) { Toast.makeText(this, "Build blocked:\n${report.errors.joinToString("\n")}", Toast.LENGTH_LONG).show(); return }
        projectStore.writeScene(scene)
        val warnings = if (report.warnings.isEmpty()) "" else "\nWarnings:\n${report.warnings.joinToString("\n")}"
        AlertDialog.Builder(this).setTitle("Nova Build V2").setMessage("Project validation passed.$warnings\n\nAPK/AAB compilation is performed by Gradle/CI so the result is a real Android artifact rather than a fake in-app file.")
            .setNegativeButton("CANCEL", null).setPositiveButton("OPEN CI BUILD") { _, _ -> startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/surafel5509-del/2D-game-engine/actions/workflows/android.yml"))) }.show()
    }

    fun exitGame() { showEditor() }
    override fun onBackPressed() { if (inGame) exitGame() else super.onBackPressed() }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_ASSETS || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<android.net.Uri>(); data.data?.let(uris::add); data.clipData?.let { for (i in 0 until it.itemCount) uris += it.getItemAt(i).uri }
        var imported = 0
        uris.distinct().forEach { uri -> val name = queryDisplayName(uri) ?: "asset_${System.currentTimeMillis()}"; if (projectStore.importFile(uri, name) != null) imported++ }
        Toast.makeText(this, "Imported $imported asset(s) into nova-v2/assets", Toast.LENGTH_SHORT).show()
    }

    private fun queryDisplayName(uri: android.net.Uri): String? = runCatching { contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()
    companion object { private const val REQUEST_ASSETS = 4102 }
}
