package com.abdulhameed.novavideo

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class MainActivity : Activity() {
    private lateinit var home: LinearLayout
    private lateinit var settings: LinearLayout
    private lateinit var createCard: LinearLayout
    private lateinit var statusCard: LinearLayout
    private lateinit var resultCard: LinearLayout
    private lateinit var etIdea: EditText
    private lateinit var tvError: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvScene: TextView
    private lateinit var tvSaved: TextView
    private lateinit var video: VideoView
    private lateinit var etKimi: EditText
    private lateinit var etHf: EditText
    private lateinit var cbBetter: CheckBox
    private lateinit var ivThumb: ImageView
    private lateinit var photoBox: LinearLayout
    private lateinit var btnPhoto: Button
    private lateinit var shapeBox: LinearLayout

    private var seconds = 4.0
    private var vidWidth = 704
    private var vidHeight = 512
    private var videoFile: File? = null
    private var photoFile: File? = null
    private var photoW = 1
    private var photoH = 1
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Style.BG
        window.navigationBarColor = Style.BG
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        buildUi()
        if (Keys.hf(this).isEmpty()) showSettings()
    }

    override fun onBackPressed() {
        if (settings.visibility == View.VISIBLE) {
            showHome()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (isFinishing) Api.cancel()
        super.onDestroy()
    }

    private fun dp(v: Int): Int = Style.dp(this, v)

    private fun ui(r: () -> Unit) {
        runOnUiThread { r() }
    }

    private fun wrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun wrapTop(topDp: Int): LinearLayout.LayoutParams {
        val lp = wrap()
        lp.setMargins(0, dp(topDp), 0, 0)
        return lp
    }

    // ---------- UI ----------
    private fun buildUi() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Style.BG)
        scroll.isFillViewport = true
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(18), dp(20), dp(18), dp(28))
        scroll.addView(root)

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.ic_launcher)
        header.addView(logo, LinearLayout.LayoutParams(dp(44), dp(44)))
        val title = Style.text(this, "NOVA VIDEO", 20f, Style.TEXT, true)
        title.letterSpacing = 0.08f
        title.setPadding(dp(12), 0, 0, 0)
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val gear = Style.text(this, "Settings", 14f, Style.ACCENT2, true)
        gear.setPadding(dp(14), dp(9), dp(14), dp(9))
        gear.background = Style.fill(this, Style.SOFT, 14)
        gear.setOnClickListener { showSettings() }
        header.addView(gear)
        root.addView(header, wrap())

        val sub = Style.text(this, "Describe it. AI makes the video.", 14f, Style.DIM, false)
        sub.setPadding(0, dp(10), 0, dp(4))
        root.addView(sub)

        home = LinearLayout(this)
        home.orientation = LinearLayout.VERTICAL
        settings = LinearLayout(this)
        settings.orientation = LinearLayout.VERTICAL
        settings.visibility = View.GONE
        root.addView(home, wrapTop(10))
        root.addView(settings, wrapTop(10))

        buildHome()
        buildSettings()
        setContentView(scroll)
    }

    private fun chips(options: List<String>, selected: Int, onPick: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val views = ArrayList<TextView>()
        val paint = { sel: Int ->
            for (i in views.indices) {
                val on = i == sel
                views[i].setTextColor(if (on) Color.WHITE else Style.TEXT)
                views[i].background = if (on) Style.gradient(this, 12) else Style.fill(this, Style.FIELD, 12)
            }
        }
        for ((i, o) in options.withIndex()) {
            val t = Style.text(this, o, 15f, Style.TEXT, true)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(8), dp(12), dp(8), dp(12))
            t.setOnClickListener {
                paint(i)
                onPick(i)
            }
            views.add(t)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(3), 0, dp(3), 0)
            row.addView(t, lp)
        }
        paint(selected)
        return row
    }

    private fun buildHome() {
        // Create card
        createCard = Style.cardBox(this)
        createCard.addView(Style.label(this, "Your idea"))
        etIdea = EditText(this)
        etIdea.hint = "e.g. a fox running through a neon city at night"
        etIdea.setHintTextColor(Style.DIM)
        etIdea.setTextColor(Style.TEXT)
        etIdea.textSize = 16f
        etIdea.minLines = 4
        etIdea.gravity = Gravity.TOP
        etIdea.background = Style.fill(this, Style.FIELD, 14)
        etIdea.setPadding(dp(14), dp(12), dp(14), dp(12))
        etIdea.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        createCard.addView(etIdea, wrap())

        createCard.addView(Style.label(this, "Photo (optional)"))
        btnPhoto = Style.softButton(this, "Add a photo") { pickPhoto() }
        createCard.addView(btnPhoto, wrap())
        photoBox = LinearLayout(this)
        photoBox.orientation = LinearLayout.HORIZONTAL
        photoBox.gravity = Gravity.CENTER_VERTICAL
        photoBox.visibility = View.GONE
        ivThumb = ImageView(this)
        ivThumb.scaleType = ImageView.ScaleType.CENTER_CROP
        photoBox.addView(ivThumb, LinearLayout.LayoutParams(dp(72), dp(72)))
        val pinfo = Style.text(this, "Photo added. The video starts from it.", 14f, Style.TEXT, false)
        pinfo.setPadding(dp(12), 0, dp(8), 0)
        photoBox.addView(pinfo, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val remove = Style.text(this, "Remove", 14f, Style.ERROR, true)
        remove.setPadding(dp(10), dp(10), dp(10), dp(10))
        remove.setOnClickListener { clearPhoto() }
        photoBox.addView(remove)
        createCard.addView(photoBox, wrapTop(10))

        createCard.addView(Style.label(this, "Length"))
        val lengths = listOf(2.0, 4.0, 6.0)
        createCard.addView(chips(listOf("2 sec", "4 sec", "6 sec"), 1) { i -> seconds = lengths[i] }, wrap())

        shapeBox = LinearLayout(this)
        shapeBox.orientation = LinearLayout.VERTICAL
        shapeBox.addView(Style.label(this, "Shape"))
        val ws = listOf(704, 512, 512)
        val hs = listOf(512, 704, 512)
        shapeBox.addView(chips(listOf("Wide", "Tall", "Square"), 0) { i ->
            vidWidth = ws[i]
            vidHeight = hs[i]
        }, wrap())
        createCard.addView(shapeBox, wrap())

        val note = Style.text(
            this,
            "Free service: only a few clips per day. Longer clips use more of your free time.",
            12f, Style.DIM, false
        )
        note.setPadding(0, dp(14), 0, dp(4))
        createCard.addView(note)

        tvError = Style.text(this, "", 13f, Style.ERROR, false)
        tvError.visibility = View.GONE
        tvError.setPadding(0, dp(6), 0, dp(4))
        createCard.addView(tvError)

        createCard.addView(Style.primaryButton(this, "Create video") { onCreateClicked() }, wrapTop(10))
        home.addView(createCard, wrap())

        // Status card
        statusCard = Style.cardBox(this)
        statusCard.visibility = View.GONE
        statusCard.addView(Style.label(this, "Working on it"))
        val bar = ProgressBar(this)
        bar.isIndeterminate = true
        bar.indeterminateTintList = android.content.res.ColorStateList.valueOf(Style.ACCENT2)
        statusCard.addView(bar, LinearLayout.LayoutParams(dp(44), dp(44)))
        tvStatus = Style.text(this, "", 15f, Style.TEXT, false)
        tvStatus.setPadding(0, dp(10), 0, dp(6))
        statusCard.addView(tvStatus)
        tvScene = Style.text(this, "", 13f, Style.DIM, false)
        tvScene.setTypeface(tvScene.typeface, Typeface.ITALIC)
        statusCard.addView(tvScene)
        statusCard.addView(Style.softButton(this, "Cancel") { Api.cancel() }, wrapTop(14))
        home.addView(statusCard, wrap())

        // Result card
        resultCard = Style.cardBox(this)
        resultCard.visibility = View.GONE
        video = VideoView(this)
        val mc = MediaController(this)
        mc.setAnchorView(video)
        video.setMediaController(mc)
        video.setOnPreparedListener { mp ->
            mp.isLooping = true
            video.start()
        }
        resultCard.addView(video, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)))
        resultCard.addView(Style.primaryButton(this, "Save to gallery") { saveVideo() }, wrapTop(14))
        tvSaved = Style.text(this, "", 13f, Style.DIM, false)
        tvSaved.visibility = View.GONE
        tvSaved.setPadding(0, dp(8), 0, 0)
        resultCard.addView(tvSaved)
        resultCard.addView(Style.softButton(this, "Make another video") { backToCreate("") }, wrapTop(10))
        home.addView(resultCard, wrapTop(14))
    }

    private fun secretField(value: String): EditText {
        val e = EditText(this)
        e.setText(value)
        e.setTextColor(Style.TEXT)
        e.textSize = 15f
        e.setSingleLine(true)
        e.background = Style.fill(this, Style.FIELD, 14)
        e.setPadding(dp(14), dp(12), dp(14), dp(12))
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        return e
    }

    private fun buildSettings() {
        val card = Style.cardBox(this)
        card.addView(Style.text(this, "Settings", 20f, Style.TEXT, true))

        card.addView(Style.label(this, "Hugging Face token (required)"))
        etHf = secretField(Keys.hf(this))
        card.addView(etHf, wrap())
        card.addView(Style.softButton(this, "Get a free token") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/settings/tokens")))
        }, wrapTop(8))

        card.addView(Style.label(this, "Kimi API key (optional)"))
        etKimi = secretField(Keys.kimi(this))
        card.addView(etKimi, wrap())
        val k = Style.text(this, "With a Kimi key, your idea is turned into a detailed scene first.", 12f, Style.DIM, false)
        k.setPadding(0, dp(6), 0, 0)
        card.addView(k)

        cbBetter = CheckBox(this)
        cbBetter.text = "Better quality (slower, uses more free time)"
        cbBetter.setTextColor(Style.TEXT)
        cbBetter.isChecked = Keys.better(this)
        card.addView(cbBetter, wrapTop(14))

        val safe = Style.text(this, "Your keys stay only on this phone.", 12f, Style.DIM, false)
        safe.setPadding(0, dp(10), 0, dp(4))
        card.addView(safe)

        card.addView(Style.primaryButton(this, "Save") {
            Keys.save(this, etKimi.text.toString(), etHf.text.toString(), cbBetter.isChecked)
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            showHome()
        }, wrapTop(10))
        card.addView(Style.softButton(this, "Back") { showHome() }, wrapTop(8))
        settings.addView(card, wrap())
    }

    private fun showSettings() {
        etHf.setText(Keys.hf(this))
        etKimi.setText(Keys.kimi(this))
        cbBetter.isChecked = Keys.better(this)
        home.visibility = View.GONE
        settings.visibility = View.VISIBLE
    }

    private fun showHome() {
        settings.visibility = View.GONE
        home.visibility = View.VISIBLE
    }

    private fun backToCreate(error: String) {
        video.stopPlayback()
        statusCard.visibility = View.GONE
        resultCard.visibility = View.GONE
        createCard.visibility = View.VISIBLE
        if (error.isEmpty()) {
            tvError.visibility = View.GONE
        } else {
            tvError.text = error
            tvError.visibility = View.VISIBLE
        }
    }

    // ---------- Photo ----------
    private fun pickPhoto() {
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.type = "image/*"
        i.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(Intent.createChooser(i, "Choose a photo"), 101)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 101 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        Thread {
            try {
                val f = loadPhoto(uri)
                ui { setPhoto(f) }
            } catch (e: Exception) {
                val why = e.message ?: "unknown error"
                ui { Toast.makeText(this, "Could not open that photo: $why", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun loadPhoto(uri: Uri): File {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val side = max(bounds.outWidth, bounds.outHeight)
        while (side / sample > 2048) sample *= 2
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        var bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw Exception("that is not a picture")
        var degrees = 0
        try {
            contentResolver.openInputStream(uri)?.use { s ->
                val ex = ExifInterface(s)
                val o = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                degrees = when (o) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        } catch (e: Exception) {
        }
        if (degrees != 0) {
            val m = Matrix()
            m.postRotate(degrees.toFloat())
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        val longest = max(bmp.width, bmp.height)
        if (longest > 1024) {
            val sc = 1024f / longest
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt(), (bmp.height * sc).toInt(), true)
        }
        val f = File(cacheDir, "photo_" + System.currentTimeMillis() + ".jpg")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        photoW = bmp.width
        photoH = bmp.height
        return f
    }

    private fun setPhoto(f: File) {
        photoFile = f
        ivThumb.setImageURI(Uri.fromFile(f))
        photoBox.visibility = View.VISIBLE
        btnPhoto.text = "Change photo"
        shapeBox.visibility = View.GONE
        etIdea.hint = "What should happen? e.g. the dog runs toward the camera (optional)"
    }

    private fun clearPhoto() {
        photoFile = null
        photoBox.visibility = View.GONE
        btnPhoto.text = "Add a photo"
        shapeBox.visibility = View.VISIBLE
        etIdea.hint = "e.g. a fox running through a neon city at night"
    }

    private fun shapeFromPhoto(): Pair<Int, Int> {
        val ratio = photoW.toDouble() / photoH.toDouble()
        return if (ratio > 1.15) Pair(704, 512) else if (ratio < 0.87) Pair(512, 704) else Pair(512, 512)
    }

    // ---------- Actions ----------
    private fun onCreateClicked() {
        if (busy) return
        val idea = etIdea.text.toString().trim()
        val photo = photoFile
        if (idea.isEmpty() && photo == null) {
            Toast.makeText(this, "Type your idea or add a photo first", Toast.LENGTH_SHORT).show()
            return
        }
        val hf = Keys.hf(this)
        if (hf.isEmpty()) {
            Toast.makeText(this, "Add your Hugging Face token first", Toast.LENGTH_LONG).show()
            showSettings()
            return
        }
        val kimi = Keys.kimi(this)
        val better = Keys.better(this)
        val secs = seconds
        val dims = if (photo == null) Pair(vidWidth, vidHeight) else shapeFromPhoto()
        val w = dims.first
        val h = dims.second

        busy = true
        Api.reset()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tvError.visibility = View.GONE
        createCard.visibility = View.GONE
        resultCard.visibility = View.GONE
        statusCard.visibility = View.VISIBLE
        tvScene.text = ""
        tvStatus.text = "Starting..."

        Thread {
            try {
                var scene = idea
                if (idea.isEmpty()) {
                    scene = "The scene comes to life with gentle natural motion and a slow cinematic camera move."
                } else if (kimi.isNotEmpty()) {
                    ui { tvStatus.text = "Kimi is writing your scene..." }
                    try {
                        scene = Api.improvePrompt(kimi, idea, photo != null)
                        val shown = scene
                        ui { tvScene.text = shown }
                    } catch (e: Cancelled) {
                        throw e
                    } catch (e: Exception) {
                        val why = e.message ?: "not available"
                        ui { tvScene.text = "Kimi was skipped ($why). Using your original idea." }
                    }
                }
                val out = File(cacheDir, "nova_" + System.currentTimeMillis() + ".mp4")
                Api.generateVideo(hf, scene, secs, w, h, better, photo, out) { msg -> ui { tvStatus.text = msg } }
                videoFile = out
                ui { showResult(out) }
            } catch (e: Cancelled) {
                ui { backToCreate("Cancelled.") }
            } catch (e: Exception) {
                val msg = e.message ?: "Something went wrong."
                ui { backToCreate(msg) }
            } finally {
                ui {
                    busy = false
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }.start()
    }

    private fun showResult(file: File) {
        statusCard.visibility = View.GONE
        resultCard.visibility = View.VISIBLE
        tvSaved.visibility = View.GONE
        video.setVideoPath(file.absolutePath)
    }

    private fun saveVideo() {
        val f = videoFile ?: return
        Thread {
            try {
                val msg: String
                if (Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues()
                    values.put(MediaStore.Video.Media.DISPLAY_NAME, "NovaVideo_" + System.currentTimeMillis() + ".mp4")
                    values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/NovaVideo")
                    val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw Exception("Could not create the file")
                    contentResolver.openOutputStream(uri)!!.use { o ->
                        f.inputStream().use { it.copyTo(o) }
                    }
                    msg = "Saved to Movies/NovaVideo in your gallery."
                } else {
                    val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)!!
                    val dest = File(dir, "NovaVideo_" + System.currentTimeMillis() + ".mp4")
                    f.copyTo(dest, true)
                    msg = "Saved to " + dest.absolutePath
                }
                ui {
                    tvSaved.text = msg
                    tvSaved.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                val why = e.message ?: "unknown error"
                ui {
                    tvSaved.text = "Could not save: $why"
                    tvSaved.visibility = View.VISIBLE
                }
            }
        }.start()
    }
}
