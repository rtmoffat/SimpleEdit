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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentFileUri: Uri? = null

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
                writeToUri(uri, binding.editor.text.toString())
                currentFileUri = uri
                toast("Saved")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnNew.setOnClickListener {
            binding.editor.setText("")
            currentFileUri = null
            toast("New document")
        }

        binding.btnOpen.setOnClickListener {
            // Only show openable documents. You can restrict to arrayOf("text/plain") if you want.
            openDocumentLauncher.launch(arrayOf("text/*"))
        }

        binding.btnSave.setOnClickListener {
            val text = binding.editor.text.toString()
            val uri = currentFileUri
            if (uri != null) {
                writeToUri(uri, text)
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
