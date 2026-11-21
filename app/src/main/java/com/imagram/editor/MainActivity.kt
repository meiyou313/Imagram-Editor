package com.imagram.editor

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.imagram.editor.databinding.ActivityMainBinding
import ja.burhanrashid52.photoeditor.PhotoEditor
import ja.burhanrashid52.photoeditor.PhotoFilter
import ja.burhanrashid52.photoeditor.shape.ShapeBuilder
import ja.burhanrashid52.photoeditor.shape.ShapeType
import kotlinx.coroutines.launch

/**
 * Imagram Editor v0.1 — single-activity photo-only editor.
 *
 * Flow: pick a photo from the gallery -> crop (CanHub) -> edit
 * (PhotoEditor: brush / arrow / line, text, filter, undo / redo)
 * -> save to the gallery via MediaStore (scoped storage, no permissions).
 *
 * No video, no chat, no login, no stickers, no Telegram code.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var photoEditor: PhotoEditor

    private enum class Tool { NONE, BRUSH, ARROW, LINE }

    private var currentTool = Tool.NONE
    private var brushColor: Int = Color.RED
    private var currentImageUri: Uri? = null

    private var filterIndex = 0
    private val filters = listOf(PhotoFilter.NONE, PhotoFilter.BRIGHTNESS, PhotoFilter.SEPIA)

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { startCrop(it) }
        }

    private val cropImageLauncher =
        registerForActivityResult(CropImageContract()) { result ->
            if (result.isSuccessful) {
                result.uriContent?.let { loadImage(it) }
            } else if (result.error != null) {
                Toast.makeText(
                    this,
                    result.error?.message ?: getString(R.string.crop_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
            // Cancelled crop (no error): do nothing.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        photoEditor = PhotoEditor.Builder(this, binding.photoEditorView)
            .setPinchTextScalable(true)
            .setClipSourceImage(true)
            .build()

        binding.btnPick.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.btnCrop.setOnClickListener {
            val uri = currentImageUri
            if (uri != null) startCrop(uri) else pickImageLauncher.launch("image/*")
        }
        binding.btnBrush.setOnClickListener { selectTool(Tool.BRUSH) }
        binding.btnArrow.setOnClickListener { selectTool(Tool.ARROW) }
        binding.btnLine.setOnClickListener { selectTool(Tool.LINE) }
        binding.btnText.setOnClickListener { showAddTextDialog() }
        binding.btnFilter.setOnClickListener { cycleFilter() }
        binding.btnUndo.setOnClickListener { photoEditor.undo() }
        binding.btnRedo.setOnClickListener { photoEditor.redo() }
        binding.btnSave.setOnClickListener { saveImage() }

        binding.btnColorRed.setOnClickListener { setBrushColor(Color.RED) }
        binding.btnColorWhite.setOnClickListener { setBrushColor(Color.WHITE) }
        binding.btnColorYellow.setOnClickListener { setBrushColor(Color.YELLOW) }
        binding.btnColorBlack.setOnClickListener { setBrushColor(Color.BLACK) }

        updateToolButtons()
    }

    private fun startCrop(uri: Uri) {
        cropImageLauncher.launch(
            CropImageContractOptions(
                uri = uri,
                cropImageOptions = CropImageOptions(
                    guidelines = CropImageView.Guidelines.ON
                )
            )
        )
    }

    private fun loadImage(uri: Uri) {
        currentImageUri = uri
        binding.photoEditorView.source.setImageURI(uri)
        photoEditor.clearAllViews()
        photoEditor.setFilterEffect(PhotoFilter.NONE)
        filterIndex = 0
        binding.btnFilter.text = getString(R.string.filter_none)
        binding.hintText.visibility = View.GONE
        selectTool(Tool.NONE)
    }

    private fun selectTool(tool: Tool) {
        currentTool = tool
        when (tool) {
            Tool.BRUSH -> applyShape(ShapeType.Brush)
            Tool.ARROW -> applyShape(ShapeType.Arrow)
            Tool.LINE -> applyShape(ShapeType.Line)
            Tool.NONE -> photoEditor.setBrushDrawingMode(false)
        }
        updateToolButtons()
    }

    private fun applyShape(type: ShapeType) {
        val shapeBuilder = ShapeBuilder()
            .withShapeOpacity(100)
            .withShapeType(type)
            .withShapeSize(24f)
        photoEditor.setShape(shapeBuilder)
        photoEditor.setBrushColor(brushColor)
        photoEditor.setBrushDrawingMode(true)
    }

    private fun setBrushColor(color: Int) {
        brushColor = color
        photoEditor.setBrushColor(color)
    }

    private fun updateToolButtons() {
        binding.btnBrush.isSelected = currentTool == Tool.BRUSH
        binding.btnArrow.isSelected = currentTool == Tool.ARROW
        binding.btnLine.isSelected = currentTool == Tool.LINE
    }

    private fun showAddTextDialog() {
        if (currentImageUri == null) {
            Toast.makeText(this, R.string.pick_first, Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply { hint = getString(R.string.type_text) }
        AlertDialog.Builder(this)
            .setTitle(R.string.add_text)
            .setView(input)
            .setPositiveButton(R.string.add) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    photoEditor.addText(text = text, colorCodeTextView = brushColor)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun cycleFilter() {
        if (currentImageUri == null) {
            Toast.makeText(this, R.string.pick_first, Toast.LENGTH_SHORT).show()
            return
        }
        filterIndex = (filterIndex + 1) % filters.size
        val filter = filters[filterIndex]
        photoEditor.setFilterEffect(filter)
        binding.btnFilter.text = getString(R.string.filter_current, filter.name)
    }

    private fun saveImage() {
        if (currentImageUri == null) {
            Toast.makeText(this, R.string.pick_first, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                val bitmap = photoEditor.saveAsBitmap()
                if (bitmap == null) {
                    Toast.makeText(this@MainActivity, R.string.save_failed, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val savedUri = saveBitmapToGallery(bitmap)
                if (savedUri != null) {
                    Toast.makeText(this@MainActivity, R.string.saved_to_gallery, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, R.string.save_failed, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap): Uri? {
        val name = "Imagram_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/Imagram Editor"
            )
        }
        val uri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ) ?: return null
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) {
                    contentResolver.delete(uri, null, null)
                    return null
                }
            } ?: return null
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            return null
        }
        return uri
    }
}
