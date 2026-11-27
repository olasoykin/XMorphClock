package com.drdisagree.xmorphclock.domain.usecase

import com.drdisagree.xmorphclock.domain.editor.WidgetGrids
import com.drdisagree.xmorphclock.domain.model.FaceKind
import com.drdisagree.xmorphclock.domain.repository.FaceRepository
import kotlinx.coroutines.flow.first

class ApplyClockPresetUseCase(
    private val faces: FaceRepository,
    private val update: UpdateCompositionUseCase
) {
    suspend operator fun invoke(
        style: Int,
        largeMaxY: Float = DEFAULT_MAX_Y,
        smallMaxY: Float = DEFAULT_MAX_Y,
        largeMinY: Float = DEFAULT_MIN_Y,
        smallMinY: Float = DEFAULT_MIN_Y
    ) {
        val preset = faces.face(style)
        val layout = preset?.layout
        val widgetFaces = if (preset != null && layout == null) {
            (faces.faces(FaceKind.WIDGET).first() + faces.faces(FaceKind.WEATHER).first())
                .associate { it.style to it.face }
        } else {
            emptyMap()
        }
        update(clockStyle = style) { current ->
            if (layout != null) {
                layout
            } else {
                val withClock = current.withClock()
                if (preset != null) {
                    val fitted = WidgetGrids.fitClockScaleForComposition(
                        composition = withClock,
                        clockFace = preset.face,
                        faceOf = { widgetFaces[it] },
                        largeMaxY = largeMaxY,
                        smallMaxY = smallMaxY,
                        largeMinY = largeMinY,
                        smallMinY = smallMinY
                    )
                    WidgetGrids.reconcileWidgetsForClock(
                        composition = fitted,
                        clockFace = preset.face,
                        faceOf = { widgetFaces[it] },
                        largeMaxY = largeMaxY,
                        smallMaxY = smallMaxY,
                        largeMinY = largeMinY,
                        smallMinY = smallMinY
                    )
                } else {
                    withClock
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_MAX_Y = 1.9f
        const val DEFAULT_MIN_Y = 0.02f
    }
}

