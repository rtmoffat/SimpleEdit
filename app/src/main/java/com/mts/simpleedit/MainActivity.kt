package com.mts.simpleedit

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.mts.simpleedit.databinding.ActivityMainBinding
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import android.text.method.ArrowKeyMovementMethod

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentFileUri: Uri? = null

    private var isDirty: Boolean = false
    private var lastSavedText: String = ""
    private var pendingAfterSave: (() -> Unit)? = null


    private fun promptSaveIfDirtyThen(actionAfter: () -> Unit) {
        if (!isDirty) {
            actionAfter()
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Save changes?")
            .setMessage("You have unsaved changes. Save before opening another file?")
            .setPositiveButton("Save") { _, _ ->
                saveThen(actionAfter)
            }
            .setNegativeButton("Discard") { _, _ ->
                isDirty = false
                actionAfter()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }
    private fun saveThen(afterSaved: () -> Unit) {
        val text = binding.editor.text.toString()
        val uri = currentFileUri

        if (uri != null) {
            writeToUri(uri, text)
            lastSavedText = text
            isDirty = false
            toast("Saved")
            afterSaved()
        } else {
            // Save As… then continue in the CreateDocument result callback
            pendingAfterSave = afterSaved
            createDocumentLauncher.launch("notes.txt")
        }
    }

    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                // Keep permission to read/write this file across app restarts (best effort).
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                loadFromUri(uri)
                currentFileUri = uri
                toast("Opened")
            }
        }

    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) {
                val text = binding.editor.text.toString()
                writeToUri(uri, text)
                currentFileUri = uri
                lastSavedText = text
                isDirty = false
                toast("Saved")
                pendingAfterSave?.invoke()
                pendingAfterSave = null
            } else {
                // User cancelled Save As…
                pendingAfterSave = null
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.editor.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                isDirty = (binding.editor.text.toString() != lastSavedText)
            }
        })

        binding.editor.apply {
            setHorizontallyScrolling(false)

            // Keep normal cursor + selection behavior
            movementMethod = ArrowKeyMovementMethod.getInstance()

            // Provide a scroller so fling/inertia works smoothly
            setScroller(android.widget.Scroller(context))

            isVerticalScrollBarEnabled = true

            // Prevent parent views from stealing the gesture
            setOnTouchListener { v, _ ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
        ViewCompat.setNestedScrollingEnabled(binding.editor, true)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isDirty) {
                    // Temporarily disable this callback so the system back proceeds.
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                    return
                }

                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Discard changes?")
                    .setMessage("You have unsaved changes. Leaving will lose them.")
                    .setPositiveButton("Discard") { _, _ ->
                        isDirty = false
                        // Now actually go back
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })

        binding.btnNew.setOnClickListener {
            if (isDirty) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Discard changes?")
                    .setMessage("You have unsaved changes. Creating a new file will lose them.")
                    .setPositiveButton("Discard") { _, _ ->
                        binding.editor.setText("")
                        currentFileUri = null
                        lastSavedText = ""
                        isDirty = false
                        toast("New document")
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                binding.editor.setText("")
                currentFileUri = null
                lastSavedText = ""
                isDirty = false
                toast("New document")
            }
        }


        binding.btnOpen.setOnClickListener {
            promptSaveIfDirtyThen {
                openDocumentLauncher.launch(arrayOf("text/*"))
            }
        }


        binding.btnSave.setOnClickListener {
            val text = binding.editor.text.toString()
            val uri = currentFileUri
            if (uri != null) {
                writeToUri(uri, text)
                lastSavedText = binding.editor.text.toString()
                isDirty = false
                toast("Saved")
            } else {
                createDocumentLauncher.launch("notes.txt")
            }
        }
    }

    private fun loadFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    val content = reader.readText()
                    binding.editor.setText(content)
                    lastSavedText = content
                    isDirty = false
                }
            }
        } catch (e: Exception) {
            toast("Open failed: ${e.message}")
        }
    }

    private fun writeToUri(uri: Uri, text: String) {
        try {
            contentResolver.openOutputStream(uri, "wt")?.use { output ->
                OutputStreamWriter(output).use { writer ->
                    writer.write(text)
                }
            }
        } catch (e: Exception) {
            toast("Save failed: ${e.message}")
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
