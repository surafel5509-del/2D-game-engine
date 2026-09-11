package com.nova.engine

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager

class MainActivity : Activity() {
    private lateinit var editor: NovaEditorView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        editor = NovaEditorView(this)
        setContentView(editor)
    }

    override fun onResume() {
        super.onResume()
        editor.invalidate()
    }
}
