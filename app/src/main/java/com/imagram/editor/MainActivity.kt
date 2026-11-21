package com.imagram.editor

import android.content.ContentValues
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
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
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.canhub.cropper.CropImageView
import com.imagram.editor.databinding.ActivityMainBinding
import ja.burhanrashid52.photoeditor.PhotoEditor
import ja.burhanrashid52.photoeditor.shape.ShapeBuilder
import ja.burhanrashid52.photoeditor.shape.ShapeType
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageContrastFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageExposureFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSaturationFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSharpenFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageWhiteBalanceFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Imagram Editor v0.3 — single-activity photo-only editor,
 * Telegram-style UI (dark, photo fullscreen, icon pill toolbars).
 *
 * Flow: pick a photo from the gallery -> it loads DIRECTLY into the
 * editor with the full toolbar (crop / brush / text / adjust / save).
 * Crop is a toolbar tool, not a forced first step. Crop runs on an
 * embedded CropImageView in our own screen with our own CANCEL /
 * DONE buttons (v0.2 used CanHub's CropImageActivity, whose confirm
 * control lives in an ActionBar that never appears under our
 * NoActionBar theme — the user was trapped in a crop screen with no
 * visible confirm or cancel; see BUILD-NOTES.md).
 *
 * Adjust is real GPUImage 2.1.0 sliders (Exposure / Contrast /
 * Saturation / Sharpness / Warmth) with live preview, CANCEL reverts
 * to the values before the panel opened, DONE confirms. "Enhance"
 * AUTO is a tuned combination of GPUImage filters — GPUImage has no
 * single Enhance filter (see BUILD-NOTES.md for the exact values).
 *
 * Drawings and text stay on top via PhotoEditor; saving goes through
 * photoEditor.saveAsBitmap() so everything is captured. Adjustments
 * always start from the current base bitmap (picked/cropped photo),
 * never compounded on an already-adjusted bitmap.
 *
 * Deliberately left out (messaging parts of Telegram's editor):
 * caption field, SD/HD quality button, send button, stickers.
 *
 * No video, no chat, no login, no Telegram code.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var photoEditor: PhotoEditor
    private lateinit var gpuImage: GPUImage

    private enum class Tool { NONE, BRUSH, ARROW, LINE }
    private enum class Panel { NONE, DRAW, ADJUST }

    /** Adjust slider values. Defaults are the neutral (no-change) values. */
    private data class AdjustParams(
        val exposure: Float = 0f,    // -2.0 .. +2.0
        val contrast: Float = 1f,    // 0.0 .. 2.0
        val saturation: Float = 1f,  // 0.0 .. 2.0
        val sharpness: Float = 0f,   // 0.0 .. 2.0
        val warmth: Float = 5000f    // 4000K .. 7000K
    )

    // "Enhance" AUTO: GPUImage has no single Enhance filter, so this is
    // a tuned combination (see BUILD-NOTES.md for the exact values).
    private val enhancePreset = AdjustParams(
        exposure = 0.10f,
        contrast = 1.18f,
        saturation = 1.20f,
        sharpness = 0.40f,
        warmth = 5200f
    )

    private var currentTool = Tool.NONE
    private var currentPanel = Panel.NONE
    private var brushColor: Int = Color.RED

    // The photo being edited. baseBitmap is the source for every adjust
    // render (never compounded); previewBitmap is what the ImageView
    // currently shows (base, or the latest GPUImage render of it).
    private var baseBitmap: Bitmap? = null
    private var previewBitmap: Bitmap? = null

    private var currentAdjust = AdjustParams()
    private var adjustBefore = AdjustParams()
    private var adjustErrorToastShown = false

    // One GPU thread for all adjust renders (GPUImage is not thread-safe).
    private val gpuDispatcher = Dispatchers.Default.limitedParallelism(1)
    private var applyJob: Job? = null
    private var renderSeq = 0

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

    private val dotHolders = mutableListOf<FrameLayout>()

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            // v0.3: picking loads the photo DIRECTLY into the editor
            // with the full toolbar. No forced crop first (that was
            // the v0.2 bug — see BUILD-NOTES.md).
            uri?.let { loadImage(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        photoEditor = PhotoEditor.Builder(this, binding.photoEditorView)
            .setPinchTextScalable(true)
            .setClipSourceImage(true)
            .build()

        gpuImage = GPUImage(this)

        buildColorDots()
        setupAdjustSliders()

        // System back: leave the crop screen first, then close a panel,
        // otherwise finish the app.
        onBackPressedDispatcher.addCallback(this) {
            when {
                binding.cropContainer.visibility == View.VISIBLE -> closeCrop()
                currentPanel != Panel.NONE -> closePanels()
                else -> finish()
            }
        }

        // Empty state + top bar.
        binding.btnPickEmpty.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.btnTopBack.setOnClickListener {
            when {
                binding.cropContainer.visibility == View.VISIBLE -> closeCrop()
                currentPanel != Panel.NONE -> closePanels()
                else -> pickImageLauncher.launch("image/*")
            }
        }
        binding.btnTopUndo.setOnClickListener { photoEditor.undo() }
        binding.btnTopRedo.setOnClickListener { photoEditor.redo() }
        binding.btnClearAll.setOnClickListener { clearAll() }

        // Main toolbar pill: crop / brush / text / adjust + save circle.
        binding.btnCrop.setOnClickListener {
            if (baseBitmap == null) pickImageLauncher.launch("image/*")
            else openCrop()
        }
        binding.btnBrush.setOnClickListener { openDrawPanel() }
        binding.btnText.setOnClickListener { showAddTextDialog() }
        binding.btnAdjust.setOnClickListener { openAdjustPanel() }
        binding.btnSave.setOnClickListener { saveImage() }

        // Crop screen: our own CANCEL / DONE (always visible).
        binding.btnCropCancel.setOnClickListener { closeCrop() }
        binding.btnCropDone.setOnClickListener { confirmCrop() }

        // Draw panel: colour wheel + brush-type icons.
        binding.btnColorWheel.setOnClickListener { showMoreColorsDialog() }
        binding.btnDrawBrush.setOnClickListener { selectTool(Tool.BRUSH) }
        binding.btnDrawArrow.setOnClickListener { selectTool(Tool.ARROW) }
        binding.btnDrawLine.setOnClickListener { selectTool(Tool.LINE) }
        binding.btnDrawClose.setOnClickListener { closePanels() }
        binding.btnDrawDone.setOnClickListener { closePanels() }
        binding.tvDrawTab.setOnClickListener { /* already in DRAW mode */ }
        binding.tvTextTab.setOnClickListener { showAddTextDialog() }

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
        if (baseBitmap == null) {
            Toast.makeText(this, R.string.pick_first, Toast.LENGTH_SHORT).show()
            return
        }
        if (currentTool == Tool.NONE) selectTool(Tool.BRUSH)
        showPanel(Panel.DRAW)
    }

    private fun openAdjustPanel() {
        if (baseBitmap == null) {
            Toast.makeText(this, R.string.pick_first, Toast.LENGTH_SHORT).show()
            return
        }
        adjustBefore = currentAdjust.copy()
        adjustErrorToastShown = false
        showPanel(Panel.ADJUST)
        syncAdjustUi()
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
        currentAdjust = AdjustParams()
        syncAdjustUi()
        renderAdjust()
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

    // ---- Adjust sliders (GPUImage 2.1.0) --------------------------------

    private fun setupAdjustSliders() {
        attachSlider(
            seekBar = binding.seekExposure,
            valueView = binding.tvExposureValue,
            labelRes = R.string.adjust_exposure,
            fromProgress = { (it - 50) / 25f },
            formatValue = { String.format(Locale.US, "%+.2f", it) },
            update = { currentAdjust = currentAdjust.copy(exposure = it) }
        )
        attachSlider(
            seekBar = binding.seekContrast,
            valueView = binding.tvContrastValue,
            labelRes = R.string.adjust_contrast,
            fromProgress = { it / 50f },
            formatValue = { String.format(Locale.US, "%.2f", it) },
            update = { currentAdjust = currentAdjust.copy(contrast = it) }
        )
        attachSlider(
            seekBar = binding.seekSaturation,
            valueView = binding.tvSaturationValue,
            labelRes = R.string.adjust_saturation,
            fromProgress = { it / 50f },
            formatValue = { String.format(Locale.US, "%.2f", it) },
            update = { currentAdjust = currentAdjust.copy(saturation = it) }
        )
        attachSlider(
            seekBar = binding.seekSharpness,
            valueView = binding.tvSharpnessValue,
            labelRes = R.string.adjust_sharpness,
            fromProgress = { it / 50f },
            formatValue = { String.format(Locale.US, "%.2f", it) },
            update = { currentAdjust = currentAdjust.copy(sharpness = it) }
        )
        attachSlider(
            seekBar = binding.seekWarmth,
            valueView = binding.tvWarmthValue,
            labelRes = R.string.adjust_warmth,
            fromProgress = { 4000f + it * 30f },
            formatValue = { String.format(Locale.US, "%dK", it.toInt()) },
            update = { currentAdjust = currentAdjust.copy(warmth = it) }
        )

        // Enhance AUTO: a tuned combination (see enhancePreset).
        binding.btnEnhanceAuto.setOnClickListener {
            currentAdjust = enhancePreset.copy()
            syncAdjustUi()
            renderAdjust()
        }

        // CANCEL reverts to the values before the panel opened.
        binding.btnAdjustCancel.setOnClickListener {
            currentAdjust = adjustBefore.copy()
            syncAdjustUi()
            renderAdjust()
            closePanels()
        }
        binding.btnAdjustDone.setOnClickListener { closePanels() }
    }

    private fun attachSlider(
        seekBar: SeekBar,
        valueView: TextView,
        labelRes: Int,
        fromProgress: (Int) -> Float,
        formatValue: (Float) -> String,
        update: (Float) -> Unit
    ) {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val value = fromProgress(progress)
                update(value)
                valueView.text = formatValue(value)
                binding.tvAdjustCurrent.text = getString(
                    R.string.adjust_current, getString(labelRes), formatValue(value)
                )
                renderAdjust()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    /** Push currentAdjust into the sliders, value labels and summary line. */
    private fun syncAdjustUi() {
        val p = currentAdjust
        binding.seekExposure.progress = ((p.exposure * 25f) + 50f).roundToInt().coerceIn(0, 100)
        binding.seekContrast.progress = (p.contrast * 50f).roundToInt().coerceIn(0, 100)
        binding.seekSaturation.progress = (p.saturation * 50f).roundToInt().coerceIn(0, 100)
        binding.seekSharpness.progress = (p.sharpness * 50f).roundToInt().coerceIn(0, 100)
        binding.seekWarmth.progress = ((p.warmth - 4000f) / 30f).roundToInt().coerceIn(0, 100)
        binding.tvExposureValue.text = String.format(Locale.US, "%+.2f", p.exposure)
        binding.tvContrastValue.text = String.format(Locale.US, "%.2f", p.contrast)
        binding.tvSaturationValue.text = String.format(Locale.US, "%.2f", p.saturation)
        binding.tvSharpnessValue.text = String.format(Locale.US, "%.2f", p.sharpness)
        binding.tvWarmthValue.text = String.format(Locale.US, "%dK", p.warmth.toInt())
        val summary = adjustSummary()
        binding.tvAdjustCurrent.text = if (summary != null) {
            getString(R.string.adjust_current, getString(summary.first), summary.second)
        } else {
            getString(R.string.adjust_title)
        }
    }

    /** First non-neutral slider, for the summary line (null = all neutral). */
    private fun adjustSummary(): Pair<Int, String>? {
        val p = currentAdjust
        return when {
            p.exposure != 0f ->
                R.string.adjust_exposure to String.format(Locale.US, "%+.2f", p.exposure)
            p.contrast != 1f ->
                R.string.adjust_contrast to String.format(Locale.US, "%.2f", p.contrast)
            p.saturation != 1f ->
                R.string.adjust_saturation to String.format(Locale.US, "%.2f", p.saturation)
            p.sharpness != 0f ->
                R.string.adjust_sharpness to String.format(Locale.US, "%.2f", p.sharpness)
            p.warmth != 5000f ->
                R.string.adjust_warmth to String.format(Locale.US, "%dK", p.warmth.toInt())
            else -> null
        }
    }

    private fun buildAdjustFilter(p: AdjustParams) = GPUImageFilterGroup(
        listOf(
            GPUImageExposureFilter(p.exposure),
            GPUImageContrastFilter(p.contrast),
            GPUImageSaturationFilter(p.saturation),
            GPUImageSharpenFilter(p.sharpness),
            GPUImageWhiteBalanceFilter(p.warmth, 0f)
        )
    )

    /**
     * Render currentAdjust onto baseBitmap and show the result.
     * Always starts from baseBitmap — never compounded on a previous
     * render. Debounced (60 ms) and serialised on one GPU thread;
     * a newer render cancels an older one (renderSeq token).
     */
    private fun renderAdjust() {
        val base = baseBitmap ?: return
        val params = currentAdjust.copy()
        if (params == AdjustParams()) {
            // Neutral: show the base bitmap itself, no GPU work.
            applyJob?.cancel()
            setPreviewBitmap(base)
            return
        }
        val token = ++renderSeq
        applyJob?.cancel()
        applyJob = lifecycleScope.launch {
            delay(60)
            val out = withContext(gpuDispatcher) {
                try {
                    gpuImage.setImage(base)
                    gpuImage.setFilter(buildAdjustFilter(params))
                    gpuImage.getBitmapWithFilterApplied()
                } catch (e: Exception) {
                    null
                }
            }
            if (token != renderSeq) return@launch
            if (out != null) {
                setPreviewBitmap(out)
            } else {
                setPreviewBitmap(base)
                if (!adjustErrorToastShown) {
                    adjustErrorToastShown = true
                    Toast.makeText(this@MainActivity, R.string.adjust_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setPreviewBitmap(bitmap: Bitmap) {
        val old = previewBitmap
        previewBitmap = bitmap
        binding.photoEditorView.source.setImageBitmap(bitmap)
        if (old != null && old !== bitmap && old !== baseBitmap) old.recycle()
    }

    private fun setBaseBitmap(bitmap: Bitmap, resetAdjust: Boolean) {
        applyJob?.cancel()
        val oldBase = baseBitmap
        baseBitmap = bitmap
        if (oldBase != null && oldBase !== bitmap && oldBase !== previewBitmap) {
            oldBase.recycle()
        }
        if (resetAdjust) currentAdjust = AdjustParams()
        syncAdjustUi()
        renderAdjust()
    }

    // ---- Text -------------------------------------------------------------

    private fun showAddTextDialog() {
        if (baseBitmap == null) {
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

    private fun loadImage(uri: Uri) {
        // Decode off the main thread (camera photos are large).
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) { decodeBitmap(uri) }
            if (bitmap == null) {
                Toast.makeText(this@MainActivity, R.string.load_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            photoEditor.clearAllViews()
            setBaseBitmap(bitmap, resetAdjust = true)
            binding.emptyState.visibility = View.GONE
            binding.topBar.visibility = View.VISIBLE
            binding.bottomContainer.visibility = View.VISIBLE
            selectTool(Tool.NONE)
            closePanels()
        }
    }

    /** Decode a photo downscaled to at most [maxDim] on the long side,
     * with EXIF rotation applied (android.media.ExifInterface, API 24+
     * = our minSdk, so no new dependency is needed). */
    private fun decodeBitmap(uri: Uri, maxDim: Int = 2048): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        } catch (e: Exception) {
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = try {
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (e: Exception) {
            null
        } ?: return null
        val rotation = try {
            contentResolver.openInputStream(uri)?.use { input ->
                when (ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }
        if (rotation == 0f) return decoded
        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    /** Open the crop screen: the embedded CropImageView on the image
     * as currently displayed (adjustments baked in), with our own
     * CANCEL / DONE buttons — always visible, no ActionBar needed. */
    private fun openCrop() {
        if (baseBitmap == null) {
            Toast.makeText(this, R.string.pick_first, Toast.LENGTH_SHORT).show()
            return
        }
        selectTool(Tool.NONE)
        closePanels()
        binding.cropImageView.setImageBitmap(previewBitmap ?: baseBitmap)
        binding.cropImageView.guidelines = CropImageView.Guidelines.ON
        binding.topBar.visibility = View.GONE
        binding.bottomContainer.visibility = View.GONE
        binding.cropContainer.visibility = View.VISIBLE
    }

    private fun confirmCrop() {
        val cropped = binding.cropImageView.croppedImage
        if (cropped == null) {
            Toast.makeText(this, R.string.crop_failed, Toast.LENGTH_SHORT).show()
            closeCrop()
            return
        }
        // The crop is taken from the image as displayed (adjustments
        // baked in). Drawings/text were placed on the old photo, so
        // they are cleared and adjustments reset for the new base.
        photoEditor.clearAllViews()
        setBaseBitmap(cropped, resetAdjust = true)
        closeCrop()
    }

    private fun closeCrop() {
        binding.cropContainer.visibility = View.GONE
        binding.topBar.visibility = View.VISIBLE
        binding.bottomContainer.visibility = View.VISIBLE
    }

    private fun saveImage() {
        if (baseBitmap == null) {
            Toast.makeText(this, R.string.pick_first, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                // Captures the source ImageView (adjusted preview) plus
                // all drawings and text on top.
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
