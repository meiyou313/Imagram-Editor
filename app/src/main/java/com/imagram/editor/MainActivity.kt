package com.imagram.editor

import android.content.ContentValues
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
 * Imagram Editor v0.2 — single-activity photo-only editor,
 * Telegram-style UI (dark, photo fullscreen, icon pill toolbars).
 *
 * Flow: pick a photo from the gallery -> crop (CanHub) -> edit
 * (PhotoEditor: brush / arrow / line, text, filter presets,
 * undo / redo) -> save to the gallery via MediaStore
 * (scoped storage, no permissions).
 *
 * Deliberately left out (messaging parts of Telegram's editor):
 * caption field, SD/HD quality button, send button, stickers.
 * The Adjust panel offers the library's fixed PhotoFilter presets
 * as chips — PhotoEditor 3.1.1 has no continuous brightness /
 * contrast slider API, so Telegram's Enhance / Exposure / Contrast
 * sliders are NOT faked with sliders that do nothing.
 *
 * No video, no chat, no login, no Telegram code.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var photoEditor: PhotoEditor

    private enum class Tool { NONE, BRUSH, ARROW, LINE }
    private enum class Panel { NONE, DRAW, ADJUST }

    private var currentTool = Tool.NONE
    private var currentPanel = Panel.NONE
    private var brushColor: Int = Color.RED
    private var currentImageUri: Uri? = null
    private var currentFilter = PhotoFilter.NONE
    private var filterBeforeAdjust = PhotoFilter.NONE

    // The 8 colour dots in the draw panel (Telegram's dot row).
    private val drawColors = listOf(
        Color.RED,
        0xFFFF9500.toInt(), // orange
        Color.YELLOW,
        0xFF34C759.toInt(), // green
        0xFF32ADE6.toInt(), // cyan
        0xFF5856D6.toInt(), // purple
        Color.WHITE,
        Color.BLACK
    )

    // Extra colours behind the colour-wheel button.
    private val extraColors = listOf(
        0xFFFF2D55.toInt(), // pink
        0xFF8B4513.toInt(), // brown
        0xFF0E7C66.toInt(), // teal
        0xFF8E8E93.toInt()  // grey
    )

    // Filter presets (PhotoFilter enum, PhotoEditor 3.1.1, verified
    // from the library source at tag v.3.1.1).
    private val filters = listOf(
        PhotoFilter.NONE to R.string.filter_none,
        PhotoFilter.AUTO_FIX to R.string.filter_auto_fix,
        PhotoFilter.BRIGHTNESS to R.string.filter_brightness,
        PhotoFilter.CONTRAST to R.string.filter_contrast,
        PhotoFilter.SEPIA to R.string.filter_sepia,
        PhotoFilter.BLACK_WHITE to R.string.filter_black_white,
        PhotoFilter.SATURATE to R.string.filter_saturate,
        PhotoFilter.TEMPERATURE to R.string.filter_temperature,
        PhotoFilter.VIGNETTE to R.string.filter_vignette,
        PhotoFilter.GRAY_SCALE to R.string.filter_gray_scale
    )

    private val dotHolders = mutableListOf<FrameLayout>()
    private val filterChips = mutableMapOf<PhotoFilter, TextView>()

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

        buildColorDots()
        buildFilterChips()

        // Empty state + top bar.
        binding.btnPickEmpty.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.btnTopBack.setOnClickListener {
            if (currentPanel != Panel.NONE) closePanels()
            else pickImageLauncher.launch("image/*")
        }
        binding.btnTopUndo.setOnClickListener { photoEditor.undo() }
        binding.btnTopRedo.setOnClickListener { photoEditor.redo() }
        binding.btnClearAll.setOnClickListener { clearAll() }

        // Main toolbar pill: crop / brush / text / adjust + save circle.
        binding.btnCrop.setOnClickListener {
            val uri = currentImageUri
            if (uri != null) startCrop(uri) else pickImageLauncher.launch("image/*")
        }
        binding.btnBrush.setOnClickListener { openDrawPanel() }
        binding.btnText.setOnClickListener { showAddTextDialog() }
        binding.btnAdjust.setOnClickListener { openAdjustPanel() }
        binding.btnSave.setOnClickListener { saveImage() }

        // Draw panel: colour wheel + brush-type icons.
        binding.btnColorWheel.setOnClickListener { showMoreColorsDialog() }
        binding.btnDrawBrush.setOnClickListener { selectTool(Tool.BRUSH) }
        binding.btnDrawArrow.setOnClickListener { selectTool(Tool.ARROW) }
        binding.btnDrawLine.setOnClickListener { selectTool(Tool.LINE) }
        binding.btnDrawClose.setOnClickListener { closePanels() }
        binding.btnDrawDone.setOnClickListener { closePanels() }
        binding.tvDrawTab.setOnClickListener { /* already in DRAW mode */ }
        binding.tvTextTab.setOnClickListener { showAddTextDialog() }

        // Adjust panel: CANCEL reverts, DONE confirms.
        binding.btnAdjustCancel.setOnClickListener {
            applyFilter(filterBeforeAdjust)
            closePanels()
        }
        binding.btnAdjustDone.setOnClickListener { closePanels() }

        updateToolStates()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    // ---- Panels -------------------------------------------------------

    private fun showPanel(panel: Panel) {
        currentPanel = panel
        binding.mainBar.visibility = if (panel == Panel.NONE) View.VISIBLE else View.GONE
        binding.drawPanel.visibility = if (panel == Panel.DRAW) View.VISIBLE else View.GONE
        binding.adjustPanel.visibility = if (panel == Panel.ADJUST) View.VISIBLE else View.GONE
        binding.btnClearAll.visibility = if (panel == Panel.DRAW) View.VISIBLE else View.GONE
        updateToolStates()
    }

    private fun closePanels() = showPanel(Panel.NONE)

    private fun openDrawPanel() {
        if (currentImageUri == null) {
            Toast.makeText(this, R.string.pick_first, Toast.LENGTH_SHORT).show()
            return
        }
        if (currentTool == Tool.NONE) selectTool(Tool.BRUSH)
        showPanel(Panel.DRAW)
    }

    private fun openAdjustPanel() {
        if (currentImageUri == null) {
            Toast.makeText(this, R.string.pick_first, Toast.LENGTH_SHORT).show()
            return
        }
        filterBeforeAdjust = currentFilter
        showPanel(Panel.ADJUST)
        updateFilterChips()
    }

    // ---- Drawing tools -------------------------------------------------

    private fun selectTool(tool: Tool) {
        currentTool = tool
        when (tool) {
            Tool.BRUSH -> applyShape(ShapeType.Brush)
            Tool.ARROW -> applyShape(ShapeType.Arrow())
            Tool.LINE -> applyShape(ShapeType.Line)
            Tool.NONE -> photoEditor.setBrushDrawingMode(false)
        }
        updateToolStates()
    }

    private fun applyShape(type: ShapeType) {
        val shapeBuilder = ShapeBuilder()
            .withShapeOpacity(100)
            .withShapeType(type)
            .withShapeSize(24f)
            .withShapeColor(brushColor)
        photoEditor.setShape(shapeBuilder)
        photoEditor.setBrushDrawingMode(true)
    }

    private fun setBrushColor(color: Int) {
        brushColor = color
        updateDotSelection()
        // In PhotoEditor 3.1.1 there is no setBrushColor() method: colour
        // lives on the ShapeBuilder (withShapeColor) and setShape() replaces
        // the whole builder, so re-apply the active tool for the new colour
        // to take effect immediately.
        if (currentTool != Tool.NONE) selectTool(currentTool)
    }

    private fun updateToolStates() {
        binding.btnDrawBrush.isSelected = currentTool == Tool.BRUSH
        binding.btnDrawArrow.isSelected = currentTool == Tool.ARROW
        binding.btnDrawLine.isSelected = currentTool == Tool.LINE
        binding.btnBrush.isSelected = currentPanel == Panel.DRAW && currentTool != Tool.NONE
    }

    private fun clearAll() {
        photoEditor.clearAllViews()
        applyFilter(PhotoFilter.NONE)
        selectTool(Tool.NONE)
        Toast.makeText(this, R.string.cleared, Toast.LENGTH_SHORT).show()
    }

    // ---- Colour dots ----------------------------------------------------

    private fun buildColorDots() {
        drawColors.forEach { color ->
            val holder = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            }
            val dot = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER)
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.dot)
                backgroundTintList = ColorStateList.valueOf(color)
            }
            holder.addView(dot)
            holder.tag = color
            holder.setOnClickListener { setBrushColor(color) }
            binding.colorDotsRow.addView(holder)
            dotHolders.add(holder)
        }
        updateDotSelection()
    }

    private fun updateDotSelection() {
        dotHolders.forEach { holder ->
            holder.background =
                if (holder.tag == brushColor) {
                    ContextCompat.getDrawable(this, R.drawable.dot_ring)
                } else {
                    null
                }
        }
    }

    private fun showMoreColorsDialog() {
        val grid = GridLayout(this).apply {
            columnCount = 4
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.more_colors)
            .setView(grid)
            .setNegativeButton(R.string.cancel, null)
            .create()
        (drawColors + extraColors).forEach { color ->
            val swatch = View(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dp(48)
                    height = dp(48)
                    setMargins(dp(8), dp(8), dp(8), dp(8))
                }
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.dot)
                backgroundTintList = ColorStateList.valueOf(color)
                setOnClickListener {
                    setBrushColor(color)
                    dialog.dismiss()
                }
            }
            grid.addView(swatch)
        }
        dialog.show()
    }

    // ---- Filters ----------------------------------------------------------

    private fun buildFilterChips() {
        filters.forEach { (filter, labelRes) ->
            val chip = TextView(this).apply {
                text = getString(labelRes)
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(dp(16), dp(8), dp(16), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                setOnClickListener { applyFilter(filter) }
            }
            binding.filterChipsRow.addView(chip)
            filterChips[filter] = chip
        }
        updateFilterChips()
    }

    private fun filterLabel(filter: PhotoFilter): String {
        val labelRes = filters.first { it.first == filter }.second
        return getString(labelRes)
    }

    private fun applyFilter(filter: PhotoFilter) {
        currentFilter = filter
        photoEditor.setFilterEffect(filter)
        binding.tvAdjustCurrent.text =
            getString(R.string.filter_current, filterLabel(filter))
        updateFilterChips()
    }

    private fun updateFilterChips() {
        filterChips.forEach { (filter, chip) ->
            chip.background = ContextCompat.getDrawable(
                this,
                if (filter == currentFilter) R.drawable.chip_bg_selected
                else R.drawable.chip_bg
            )
        }
    }

    // ---- Text -------------------------------------------------------------

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

    // ---- Pick / crop / save -------------------------------------------------

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
        applyFilter(PhotoFilter.NONE)
        binding.emptyState.visibility = View.GONE
        binding.topBar.visibility = View.VISIBLE
        binding.bottomContainer.visibility = View.VISIBLE
        selectTool(Tool.NONE)
        closePanels()
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
