package com.drdisagree.xmorphclock.xposed.preview

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.drdisagree.xmorphclock.BuildConfig
import com.drdisagree.xmorphclock.data.mapper.ClockFaceJsonMapper
import com.drdisagree.xmorphclock.data.source.ClockFaces
import com.drdisagree.xmorphclock.domain.editor.FaceComposer
import com.drdisagree.xmorphclock.domain.model.ClockFace
import com.drdisagree.xmorphclock.engine.ClockFaceView
import com.drdisagree.xmorphclock.engine.FaceColors
import com.drdisagree.xmorphclock.xposed.hook.HookEnv
import com.drdisagree.xmorphclock.xposed.hook.HostApplication
import com.drdisagree.xmorphclock.xposed.prefs.ModulePrefs

@SuppressLint("ViewConstructor")
class PreviewClockSlot(
    context: Context,
    initialLarge: Boolean
) : FrameLayout(context) {

    private var isLargeState: Boolean = initialLarge
    private var clockView: ClockFaceView? = null
    private var moduleContext: Context? = null
    private var isListening = false

    private val prefListener = {
        post { sync() }
        Unit
    }

    init {
        clipChildren = false
        clipToPadding = false
        sync()
    }

    fun setLarge(large: Boolean) {
        if (isLargeState == large) return
        isLargeState = large
        clockView?.setLargeProgress(if (large) 1f else 0f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isListening) {
            ModulePrefs.addListener(prefListener)
            isListening = true
        }
        sync()
    }

    override fun onDetachedFromWindow() {
        if (isListening) {
            ModulePrefs.removeListener(prefListener)
            isListening = false
        }
        super.onDetachedFromWindow()
    }

    fun sync() {
        val enabled = ModulePrefs.isClockEnabled && ModulePrefs.isWallpaperPreviewEnabled
        if (!enabled) {
            isVisible = false
            clockView?.isVisible = false
            return
        }

        isVisible = true
        FaceColors.setPalette(ModulePrefs.palette)

        val targetContext = moduleContext(context) ?: return
        val rawFace = resolveFace(targetContext) ?: return
        val face = FaceComposer.clockOnly(rawFace)

        val current = clockView
        if (current == null) {
            val view = ClockFaceView(context, face, targetContext).apply {
                morphOnChange = false
                setLargeProgress(if (isLargeState) 1f else 0f)
            }
            clockView = view
            addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        } else {
            current.isVisible = true
            current.setLargeProgress(if (isLargeState) 1f else 0f)
            if (current.face != face) {
                current.face = face
            }
            current.updateTime()
            current.requestLayout()
            current.invalidate()
        }
    }

    private fun resolveFace(context: Context): ClockFace? =
        ModulePrefs.faceJson?.let { ClockFaceJsonMapper.fromJson(it) }
            ?: ClockFaces.faceOf(context, ModulePrefs.faceStyle)
            ?: ClockFaces.presets(context).firstOrNull()

    private fun moduleContext(context: Context): Context? {
        moduleContext?.let { return it }
        val candidates = listOfNotNull(context, context.applicationContext, HostApplication.context())
        for (candidate in candidates) {
            val created = runCatching {
                candidate.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
            }.getOrNull()
            if (created != null) {
                moduleContext = created
                return created
            }
        }
        HookEnv.log("Preview module context unavailable")
        return null
    }
}
