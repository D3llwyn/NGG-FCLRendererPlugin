package com.bzlzhh.plugin.ngg

import NGGConfigEditor
import android.Manifest
import android.animation.*
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.*
import android.net.Uri
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bzlzhh.plugin.ngg.BuildConfig.useANGLE
import io.noties.markwon.Markwon
import kotlinx.coroutines.*
import okhttp3.*
import java.io.File


class MainActivity : Activity() {
    private val websiteUrl = "https://ng-gl4es.bzlzhh.top"
    private val markdownFileUrl = "https://raw.githubusercontent.com/BZLZHH/NG-GL4ES/public/README.md"
    
    private val REQUEST_CODE_PERMISSION = 0x00099
    private val REQUEST_CODE = 12

    private var forceESCopyTexSwitcher: Switch? = null
    private  var isDisableForceESCopyTex = false
    
    private val client = OkHttpClient()
    private var isMarkdownVisible = false
    private var markdownView: TextView? = null
    private var markdownViewAnimFinished = true
    
    private var hasAllFilesPermission = false
        set(value) {
            if (forceESCopyTexSwitcher != null) {
                forceESCopyTexSwitcher!!.isEnabled = value
            }
            field = value
        }
    private var isNoticedAllFilesPermissionMissing = true

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermission()
        checkAdreno740()
        checkVulkanSupportability(this)
        if (hasAllFilesPermission) {
            NGGConfigEditor.configRefresh()
        }
        
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(16, 16, 16, 16)
        }

        val kryptonTextView = TextView(this).apply {
            text = "Krypton Wrapper"
            textSize = 28f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val releaseTextView = TextView(this).apply {
            text = getAppVersionName() + if(!useANGLE) " NO-ANGLE" else ""
            textSize = 18f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }

        val byTextView = TextView(this).apply {
            text = "By BZLZHH"
            textSize = 18f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }

        val divider = View(layout.context)
        val dividerParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        divider.layoutParams = dividerParams
        divider.setBackgroundColor(Color.rgb(70, 70, 70))

        val horizontalInnerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val settingText1 = TextView(this).apply {
            text = "Priorize light and shadow effects"
            textSize = 17f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setTextColor(Color.BLUE)
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener {
                val builder = AlertDialog.Builder(layout.context)
                builder.setTitle("Light and shadow effects")
                    .setMessage("Due to some reasons, the current version cannot achieve the balance between water reflection of some lights and shadows (and possibly other functions) and efficiency, so you need to choose a solution to use. \n\nIn fact, it is more recommended that you use the \"Light and shadow efficiency priority\" solution, which has a light and shadow operation efficiency of about 5 times that of the \"Light and shadow effect priority\" solution.")
                    .show()
            }
        }
        
        val settingText2 = TextView(this).apply {
            text = "Light and shadow efficiency first"
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }

        forceESCopyTexSwitcher = Switch(this).apply {
            isEnabled = hasAllFilesPermission

            setOnCheckedChangeListener { _, _ ->
                if (!isChecked && !isDisableForceESCopyTex) {
                    isChecked = true
                    AlertDialog.Builder(this.context)
                        .setTitle("Warn")
                        .setMessage("After selecting the \"Light and Shadow Effects Priority\" solution, all light and shadow water reflections (and possibly other functions) will be rendered or used normally. \n\nHowever, this will result in very low light and shadow operation efficiency (about 20% of the \"Light and Shadow Efficiency Priority\" solution). Are you sure you want to continue using this solution?")
                        .setPositiveButton("Yes") { _: DialogInterface?, _: Int ->
                            isDisableForceESCopyTex = true
                            isChecked = false
                        }
                        .setNegativeButton("No") { _: DialogInterface?, _: Int ->  }
                        .show()
                }
                if (isDisableForceESCopyTex) {
                    isDisableForceESCopyTex = false
                }
                NGGConfigEditor.configSetInt("force_es_copy_tex", if (isChecked) 1 else 0)
                NGGConfigEditor.configSaveToFile()
                refreshConfig()
            }
        }
        
        horizontalInnerLayout.addView(settingText1)
        horizontalInnerLayout.addView(forceESCopyTexSwitcher)
        horizontalInnerLayout.addView(settingText2)

        val divider2 = View(layout.context)
        val divider2Params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        divider2.layoutParams = divider2Params
        divider2.setBackgroundColor(Color.rgb(70, 70, 70))


        val goToWebsiteButton = Button(this).apply {
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.RECTANGLE
            shape.cornerRadius = 100f
            shape.setColor(Color.rgb(159, 237, 253))
            background = shape
            text = "跳转至官网"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(websiteUrl))
                startActivity(intent)
            }
        }
        
        val shareLogButton = Button(this).apply {
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.RECTANGLE
            shape.cornerRadius = 100f
            shape.setColor(Color.rgb(159, 237, 253))
            background = shape
            text = "分享日志文件"
            setOnClickListener {
                val logFile = File(NGGConfigEditor.LOG_FILE_PATH)

                if (!logFile.exists()) {
                    Toast.makeText(this.context, "The log file does not exist!", Toast.LENGTH_SHORT).show()
                }

                try {
                    val fileUri: Uri = FileProvider.getUriForFile(
                        this.context,
                        "$packageName.fileprovider",
                        logFile
                    )

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    startActivity(Intent.createChooser(shareIntent, "Sharing log files"))
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this.context, "Failed to share log file!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        val showReadmeButton = Button(this).apply {
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.RECTANGLE
            shape.cornerRadius = 100f
            shape.setColor(Color.rgb(159, 237, 253))
            background = shape
            text = "check README.md"
            setOnClickListener {
                if (markdownViewAnimFinished) {
                    markdownViewAnimFinished = false
                    isMarkdownVisible = !isMarkdownVisible
                    if (isMarkdownVisible) {
                        text = "Hide README.md"
                        fetchMarkdown(layout)
                    } else {
                        text = "check README.md"
                        markdownView?.let { hideMarkdownWithAnimation(it, layout) }
                    }
                }
            }
        }

        layout.addView(kryptonTextView)
        layout.addView(releaseTextView)
        layout.addView(byTextView)
        layout.addView(divider)
        divider.layoutParams = (divider.layoutParams as ViewGroup.MarginLayoutParams).apply {
            topMargin = 100
            bottomMargin = 100
        }
        layout.addView(horizontalInnerLayout)
        layout.addView(divider2)
        divider2.layoutParams = (divider2.layoutParams as ViewGroup.MarginLayoutParams).apply {
            topMargin = 100
            bottomMargin = 100
        }
        layout.addView(goToWebsiteButton)
        goToWebsiteButton.layoutParams = (goToWebsiteButton.layoutParams as ViewGroup.MarginLayoutParams).apply {
            bottomMargin = 50
        }
        layout.addView(shareLogButton)
        shareLogButton.layoutParams = (shareLogButton.layoutParams as ViewGroup.MarginLayoutParams).apply {
            bottomMargin = 50
        }
        layout.addView(showReadmeButton)
        scrollView.addView(layout)
        setContentView(scrollView)

        refreshConfig()
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                hasAllFilesPermission = true
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Permission Request")
                    .setMessage("The program needs to obtain the permission to access all files in order to use the Krypton Wrapper settings function properly. Grant it?")
                    .setPositiveButton("是") { _: DialogInterface?, _: Int ->
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:" + this.packageName)
                        startActivityForResult(intent, REQUEST_CODE)
                        isNoticedAllFilesPermissionMissing = false
                    }
                    .setNegativeButton("No") { _: DialogInterface?, _: Int ->
                        isNoticedAllFilesPermissionMissing = true
                        Toast.makeText(
                            this,
                            "Refusing authorization will cause the settings to not work properly",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .setOnKeyListener { _, keyCode, _ ->
                        keyCode == KeyEvent.KEYCODE_BACK
                    }
                    .setCancelable(false)
                    .show()
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_CODE_PERMISSION
                )
            }
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                hasAllFilesPermission = true
            } else {
                Toast.makeText(this, "Refusing authorization will cause the settings to not work properly", Toast.LENGTH_SHORT)
                    .show()
                hasAllFilesPermission = false
            }
        }

    }

    private fun isVulkanSupported(context: Context): Boolean {
        val packageManager = context.packageManager
        val hasVulkanLevel1 = packageManager.hasSystemFeature("android.hardware.vulkan.level")
        val hasVulkanBasic = packageManager.hasSystemFeature("android.hardware.vulkan.version")
        return hasVulkanLevel1 || hasVulkanBasic
    }
    
    private fun checkVulkanSupportability(context: Context) {
        if (!isVulkanSupported(context)) {
            AlertDialog.Builder(context)
                .setTitle("Warning")
                .setMessage("Device not supporting Vulkan detected! This means that Krypton Wrapper with ANGLE cannot be used on this device!\n\nYou should go to the official website and download the \"NO-ANGLE\" version.")
                .setPositiveButton("Go to the official website") { _: DialogInterface?, _: Int ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(websiteUrl))
                    startActivity(intent)
                }
                .setNegativeButton("Continue using this version") { _: DialogInterface?, _: Int ->  }
                .setOnKeyListener { _, keyCode, _ ->
                    keyCode == KeyEvent.KEYCODE_BACK
                }
                .setCancelable(false)
                .show()
        }
    }
    
    private fun getGPUName(): String? {
        val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            return null
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            return null
        }

        val configAttributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )
        val eglConfigs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, configAttributes, 0, eglConfigs, 0, eglConfigs.size, numConfigs, 0)) {
            EGL14.eglTerminate(eglDisplay)
            return null
        }
        val contextAttributes = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        val eglContext = EGL14.eglCreateContext(eglDisplay, eglConfigs[0], EGL14.EGL_NO_CONTEXT, contextAttributes, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            EGL14.eglTerminate(eglDisplay)
            return null
        }
        val surfaceAttributes = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfigs[0], surfaceAttributes, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
            return null
        }
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
            return null
        }
        val renderer = GLES20.glGetString(GLES20.GL_RENDERER)
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglTerminate(eglDisplay)
        return renderer
    }
    
    private fun checkAdreno740() {
        val renderer = getGPUName()
        if (renderer != null && renderer.contains("Adreno", ignoreCase = true) && renderer.contains("740", ignoreCase = true)) {
            // device is Adreno 740
            if (useANGLE)
                AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage("The device's GPU detected is Adreno 740! Adreno 740 may have serious rendering errors in Krypton Wrapper using ANGLE!\n\nYou should go to the official website and download the \"NO-ANGLE\" version.")
                    .setPositiveButton("Go to the official website") { _: DialogInterface?, _: Int ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(websiteUrl))
                        startActivity(intent)
                    }
                    .setNegativeButton("Continue using this version") { _: DialogInterface?, _: Int ->  }
                    .setOnKeyListener { _, keyCode, _ ->
                        keyCode == KeyEvent.KEYCODE_BACK
                    }
                    .setCancelable(false)
                    .show()
        } else {
            if (!useANGLE)
             AlertDialog.Builder(this)
                 .setTitle("Warning")
                 .setMessage("The device's GPU is not detected as Adreno 740! Non-Adreno 740 devices have a few lighting and shadow rendering errors in Krypton Wrapper without ANGLE, and the efficiency is lower!\n\nYou should go to the official website and download the version without the "NO-ANGLE" mark.")
                 .setPositiveButton("Go to the official website") { _: DialogInterface?, _: Int ->
                     val intent = Intent(Intent.ACTION_VIEW, Uri.parse(websiteUrl))
                     startActivity(intent)
                 }
                 .setNegativeButton("Continue using this version") { _: DialogInterface?, _: Int ->  }
                 .setOnKeyListener { _, keyCode, _ ->
                     keyCode == KeyEvent.KEYCODE_BACK
                 }
                 .setCancelable(false)
                 .show()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun fetchMarkdown(layout: LinearLayout) {
        GlobalScope.launch {
            val markdownContent = getMarkdownFromGitHub()
            withContext(Dispatchers.Main) {
                renderMarkdownWithAnimation(markdownContent, layout)
            }
        }
    }

    private fun getMarkdownFromGitHub(): String? {
        val request = Request.Builder().url(markdownFileUrl).build()
        return try {
            val response: Response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                "Failed to get text from $markdownFileUrl"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Failed to get text from $markdownFileUrl"
        }
    }

    private fun renderMarkdownWithAnimation(markdownContent: String?, layout: LinearLayout) {
        if (markdownContent.isNullOrEmpty()) return

        val markwon = Markwon.create(this)
        markdownView = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.BLACK)
            gravity = Gravity.START
            alpha = 0f
            translationY = resources.displayMetrics.heightPixels.toFloat() * 0.3f

            setPadding(60, 40, 60, 40)

            val shape = GradientDrawable()
            shape.shape = GradientDrawable.RECTANGLE
            shape.cornerRadius = 100f
            shape.setColor(Color.rgb(230, 250, 254))
            background = shape
        }
        markwon.setMarkdown(markdownView!!, markdownContent)
        layout.addView(markdownView)
        markdownView!!.layoutParams =
            (markdownView!!.layoutParams as ViewGroup.MarginLayoutParams).apply {
                topMargin = 40
            }

        val alphaAnimator = ObjectAnimator.ofFloat(markdownView, View.ALPHA, 0f, 1f)
        val translationYAnimator = ObjectAnimator.ofFloat(
            markdownView,
            View.TRANSLATION_Y,
            resources.displayMetrics.heightPixels.toFloat() * 0.3f,
            0f
        )

        AnimatorSet().apply {
            playTogether(alphaAnimator, translationYAnimator)
            duration = 300
            interpolator = android.view.animation.DecelerateInterpolator()
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(p0: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    markdownViewAnimFinished = true
                }

                override fun onAnimationCancel(p0: Animator) {}
                override fun onAnimationRepeat(p0: Animator) {}
            })
            start()
        }
    }

    private fun hideMarkdownWithAnimation(view: View, layout: LinearLayout) {
        val alphaAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f)
        val translationYAnimator = ObjectAnimator.ofFloat(
            view,
            View.TRANSLATION_Y,
            0f,
            resources.displayMetrics.heightPixels.toFloat() * 0.3f
        )

        AnimatorSet().apply {
            playTogether(alphaAnimator, translationYAnimator)
            duration = 300
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    layout.removeView(view)
                    markdownView = null
                    markdownViewAnimFinished = true
                }

                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
            start()
        }
    }

    private fun getAppVersionName(): String {
        return try {
            val packageInfo =
                packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            packageInfo.versionName ?: "Unknown Version"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            "Unknown Version"
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                hasAllFilesPermission = true
                isNoticedAllFilesPermissionMissing = false
            } else {
                if (!isNoticedAllFilesPermissionMissing)
                    Toast.makeText(this, "Refusing authorization will cause the settings to not work properly", Toast.LENGTH_SHORT).show()
                isNoticedAllFilesPermissionMissing = true
                hasAllFilesPermission = false
            }
        }
        refreshConfig()
    }
    
    private fun refreshConfig() {
        if(hasAllFilesPermission) {
            NGGConfigEditor.configRefresh()
            var changed = false
            val forceESCopyTex = NGGConfigEditor.configGetInt("force_es_copy_tex")
            forceESCopyTexSwitcher?.isChecked = forceESCopyTex == 1

            if (forceESCopyTex != 0 && forceESCopyTex != 1) {
                changed = true
                NGGConfigEditor.configSetInt("force_es_copy_tex", 1)
            }
            if (changed) {
                NGGConfigEditor.configSaveToFile()
            }
        }
    }
}
