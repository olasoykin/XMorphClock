package com.drdisagree.xmorphclock.ui.home

import android.graphics.RectF
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdisagree.xmorphclock.R
import com.drdisagree.xmorphclock.domain.editor.CustomFaces
import com.drdisagree.xmorphclock.domain.editor.ItemGroups
import com.drdisagree.xmorphclock.domain.editor.LockTarget
import com.drdisagree.xmorphclock.domain.editor.WidgetGrids
import com.drdisagree.xmorphclock.domain.editor.WidgetZone
import com.drdisagree.xmorphclock.domain.model.ClockFace
import com.drdisagree.xmorphclock.domain.model.CompositionItem
import com.drdisagree.xmorphclock.domain.model.EditScope
import com.drdisagree.xmorphclock.domain.model.ElementKind
import com.drdisagree.xmorphclock.domain.model.FaceGrid
import com.drdisagree.xmorphclock.domain.model.FacePreset
import com.drdisagree.xmorphclock.domain.model.ItemState
import com.drdisagree.xmorphclock.domain.model.LockscreenAffordance
import com.drdisagree.xmorphclock.domain.model.ModuleStatus
import com.drdisagree.xmorphclock.domain.model.ShortcutSlot
import com.drdisagree.xmorphclock.engine.FaceColors
import com.drdisagree.xmorphclock.ui.components.AppIcon
import com.drdisagree.xmorphclock.ui.components.ClockFacePreview
import com.drdisagree.xmorphclock.ui.components.ExpressiveButton
import com.drdisagree.xmorphclock.ui.components.ExpressiveDialog
import com.drdisagree.xmorphclock.ui.components.ExpressiveIconButton
import com.drdisagree.xmorphclock.ui.components.ExpressiveTonalButton
import com.drdisagree.xmorphclock.ui.components.FitText
import com.drdisagree.xmorphclock.ui.components.LocalClockGeometry
import com.drdisagree.xmorphclock.ui.components.LocalFaceData
import com.drdisagree.xmorphclock.ui.components.LocalSampleFaceData
import com.drdisagree.xmorphclock.ui.components.LockscreenPreview
import com.drdisagree.xmorphclock.ui.components.MarqueeText
import com.drdisagree.xmorphclock.ui.components.StatusDot
import com.drdisagree.xmorphclock.ui.components.StatusPill
import com.drdisagree.xmorphclock.ui.components.StatusTone
import com.drdisagree.xmorphclock.ui.components.expressivePillShape
import com.drdisagree.xmorphclock.ui.editor.ColorPickerDialog
import com.drdisagree.xmorphclock.ui.editor.ColorTarget
import com.drdisagree.xmorphclock.ui.editor.EditorCanvas
import com.drdisagree.xmorphclock.ui.editor.EditorPanel
import com.drdisagree.xmorphclock.ui.editor.EditorTopBar
import com.drdisagree.xmorphclock.ui.editor.EditorUiState
import com.drdisagree.xmorphclock.ui.editor.EditorViewModel
import com.drdisagree.xmorphclock.ui.editor.FontPickerDialog
import com.drdisagree.xmorphclock.ui.editor.GroupingBar
import com.drdisagree.xmorphclock.ui.editor.IconPickerDialog
import com.drdisagree.xmorphclock.ui.editor.elementLabel
import com.drdisagree.xmorphclock.ui.theme.DarkPalette
import com.drdisagree.xmorphclock.ui.theme.LocalPalette
import kotlin.math.roundToInt

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val SheetShape = RoundedCornerShape(28.dp)
private val CLOCK_CARD_WIDTH = 94.dp
private val WIDGET_CARD_UNIT = 90.dp
private val WIDGET_CARD_WIDE = 152.dp
private val INLINE_CARD_WIDTH = 166.dp
private val SHEET_MARGIN = 12.dp
private val PREVIEW_SIDE_GAP = 16.dp
private val LABEL_GAP = 6.dp
private val GRABBER_WIDTH = 36.dp
private val GRABBER_HEIGHT = 4.dp
private val GRABBER_TOP = 10.dp
private val GRABBER_AREA = 24.dp
private val SHEET_PEEK = 76.dp
private const val FACES_FRACTION = 0.44f
private const val FACES_EXPANDED_FRACTION = 0.72f
private const val EDITOR_FRACTION = 0.45f
private const val MORPH_MS = 460
private val MAXIMIZED_DOCK_HEIGHT = 68.dp
private val DRAG_THRESHOLD = 32.dp
private val CARD_RADIUS = 20.dp
private val BLOCK_PADDING = 10.dp
private val CLOCK_PADDING = 10.dp
private val CARD_RING = 2.5.dp
internal val ThumbInk = Color(0xFF1F2227)
private val BADGE_TOP = 10.dp
private val BADGE_INSET = 10.dp

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    editor: EditorViewModel,
    previewOnly: Boolean = false,
    onOpenStatus: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val defaultText = stringResource(R.string.default_text)
    val elementNames = ElementKind.entries.associateWith { stringResource(elementLabel(it)) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editorState by editor.uiState.collectAsStateWithLifecycle()
    val styleStatus by viewModel.styleStatus.collectAsStateWithLifecycle()
    val editing = state.editingStyle != null && editorState.face != null && editorState.style == state.editingStyle
    val sheetOpen = editing || state.sheetOpen

    val aspect = LocalClockGeometry.current.aspect
    val density = LocalDensity.current
    val screenHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val facesHeight = (screenHeight * FACES_FRACTION - navBottom - SHEET_MARGIN).coerceAtLeast(280.dp)
    val facesExpandedHeight = (screenHeight * FACES_EXPANDED_FRACTION - navBottom - SHEET_MARGIN).coerceAtLeast(360.dp)
    val editorHeight = (screenHeight * EDITOR_FRACTION - navBottom - SHEET_MARGIN).coerceAtLeast(280.dp)

    var facesExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.sheetOpen, state.activeTarget) {
        if (!state.sheetOpen || state.activeTarget == null) facesExpanded = false
    }

    val morphAnim = animateFloatAsState(
        if (editing) 1f else 0f,
        tween(MORPH_MS, easing = Emphasized),
        label = "morph"
    )
    val sheetProgressAnim = animateFloatAsState(
        if (sheetOpen) 1f else 0f,
        tween(MORPH_MS, easing = Emphasized),
        label = "sheetProgress"
    )
    val expandAnim = animateFloatAsState(
        if (facesExpanded && !editing && state.sheetOpen) 1f else 0f,
        tween(MORPH_MS, easing = Emphasized),
        label = "expand"
    )

    val imeBottom = WindowInsets.ime.getBottom(density)
    val lift = with(density) { (imeBottom - navBottom.roundToPx()).coerceAtLeast(0).toDp() }
    val keyboardAnim = animateFloatAsState(if (imeBottom > 0 && editing) 1f else 0f, tween(KEYBOARD_MS, easing = Emphasized), label = "keyboard")
    val editSheetHeight = {
        lerp(editorHeight, minOf(editorHeight, KEYBOARD_SHEET), keyboardAnim.value)
    }
    val activeSheetHeight = {
        val targetFacesHeight = lerp(facesHeight, facesExpandedHeight, expandAnim.value)
        lerp(targetFacesHeight, editSheetHeight(), morphAnim.value)
    }

    val revealAnim = animateFloatAsState(if (state.preview == null) 0f else 1f, tween(320), label = "reveal")
    var playToken by remember { mutableIntStateOf(0) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var sheetUp by rememberSaveable { mutableStateOf(false) }
    var guides by rememberSaveable { mutableStateOf(true) }
    var liveInfo by rememberSaveable { mutableStateOf(false) }
    var previewInfo by rememberSaveable(previewOnly) { mutableStateOf(previewOnly) }
    var pickingColor by rememberSaveable { mutableStateOf<ColorTarget?>(null) }
    var colorTarget by rememberSaveable { mutableStateOf(ColorTarget.FILL) }
    var pickingIcon by rememberSaveable { mutableStateOf(false) }
    var pickingFont by rememberSaveable { mutableStateOf(false) }
    val userFonts by editor.userFonts.collectAsStateWithLifecycle()
    val fontFailed = stringResource(R.string.font_add_failed)
    val context = LocalContext.current
    val resources by rememberUpdatedState(LocalResources.current)
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            pickingFont = false
            editor.addFont(it.toString()) { Toast.makeText(context, fontFailed, Toast.LENGTH_SHORT).show() }
        }
    }
    var addingElement by rememberSaveable { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onAddImage(it.toString(), elementNames.getValue(ElementKind.IMAGE)) }
    }
    var actionsFor by remember { mutableStateOf<FacePreset?>(null) }
    var naming by remember { mutableStateOf<FacePreset?>(null) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var savingStyle by remember { mutableStateOf<String?>(null) }
    val liveWallpaper by viewModel.liveWallpaper.collectAsStateWithLifecycle()

    val fullAnim = animateFloatAsState(
        if (fullscreen) 1f else 0f,
        tween(MORPH_MS, easing = Emphasized),
        label = "full"
    )
    val openAnim = animateFloatAsState(
        if (sheetUp && fullscreen) 1f else 0f,
        tween(MORPH_MS, easing = Emphasized),
        label = "sheetOpen"
    )
    LaunchedEffect(fullscreen) { if (!fullscreen) sheetUp = false }

    val currentState by rememberUpdatedState(state)

    fun openEditorFor(itemId: Int? = null) {
        currentState.clockPreset?.let { preset ->
            editor.open(viewModel.editTarget(itemId))
            viewModel.onEdit(preset.style)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.editEvents.collect {
            fullscreen = false
            openEditorFor()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.noticeEvents.collect { messageRes ->
            Toast.makeText(context, resources.getString(messageRes), Toast.LENGTH_SHORT).show()
        }
    }

    fun closeEditor() {
        fullscreen = false
        viewModel.onEditDone()
    }

    BackHandler(enabled = editing) {
        when {
            fullscreen && sheetUp -> sheetUp = false
            fullscreen -> fullscreen = false
            else -> closeEditor()
        }
    }

    BackHandler(enabled = (state.activeTarget != null || state.sheetOpen) && !editing) {
        if (facesExpanded) facesExpanded = false
        else viewModel.onTargetSelected(null)
    }

    var previewHeight by remember { mutableStateOf(0.dp) }
    var previewWidth by remember { mutableStateOf(0.dp) }
    val fitScale = if (previewHeight > 0.dp && previewWidth > 0.dp) {
        ((previewWidth - PREVIEW_SIDE_GAP * 2) / (previewHeight * aspect)).coerceIn(0.2f, 1f)
    } else 1f
    val reservedDock = MAXIMIZED_DOCK_HEIGHT - 12.dp
    val homeScale = if (previewHeight > 0.dp) {
        ((previewHeight - (SHEET_MARGIN + facesHeight - reservedDock)) / previewHeight).coerceIn(0.25f, fitScale)
    } else fitScale
    val editScale = {
        if (previewHeight > 0.dp) {
            ((previewHeight - (SHEET_MARGIN + editSheetHeight() + lift - reservedDock)) / previewHeight).coerceIn(0.25f, fitScale)
        } else fitScale
    }
    val previewScale = {
        val openScale = homeScale + (editScale() - homeScale) * morphAnim.value
        val sheetAdjusted = fitScale + (openScale - fitScale) * sheetProgressAnim.value
        minOf(sheetAdjusted + (1f - sheetAdjusted) * fullAnim.value, fitScale)
    }

    val styleName = state.clockPreset?.face?.name ?: stringResource(R.string.menu_style_name)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(Modifier.fillMaxSize()) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 66.dp, bottom = navBottom + MAXIMIZED_DOCK_HEIGHT)
                    .graphicsLayer { alpha = revealAnim.value },
                contentAlignment = Alignment.TopCenter
            ) {
                val available = maxHeight
                val availableWidth = maxWidth
                SideEffect {
                    if (previewHeight != available) previewHeight = available
                    if (previewWidth != availableWidth) previewWidth = availableWidth
                }
                Crossfade(
                    targetState = editing,
                    animationSpec = tween(300),
                    modifier = Modifier
                        .requiredSize(available * aspect * fitScale, available * fitScale)
                        .graphicsLayer {
                            val scale = previewScale() / fitScale
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        },
                    label = "preview"
                ) { edit ->
                    if (edit) {
                        EditPreview(editorState, editor, playToken, guides)
                    } else {
                        HomePreview(state, viewModel)
                    }
                }
            }

            AnimatedContent(
                targetState = Pair(editorState.grouping != null, editing),
                transitionSpec = {
                    (fadeIn(tween(240, delayMillis = 60)) + slideInVertically(tween(MORPH_MS, easing = Emphasized)) { -it / 2 }) togetherWith
                        (fadeOut(tween(150)) + slideOutVertically(tween(MORPH_MS, easing = Emphasized)) { -it / 2 })
                },
                label = "topBar"
            ) { (_, edit) ->
                if (edit && editorState.grouping != null) {
                    val groupName = stringResource(R.string.group_name)
                    GroupingBar(
                        count = editorState.grouping?.size ?: 0,
                        onCancel = editor::cancelGrouping,
                        onGroup = { editor.confirmGrouping(groupName) }
                    )
                } else if (edit) {
                    EditorTopBar(
                        canUndo = editorState.canUndo,
                        canRedo = editorState.canRedo,
                        fullscreen = fullscreen,
                        guides = guides,
                        onCancel = ::closeEditor,
                        onUndo = editor::undo,
                        onRedo = editor::redo,
                        onSave = { editor.save(::closeEditor) },
                        onToggleFullscreen = { fullscreen = !fullscreen },
                        onToggleGuides = { guides = !guides }
                    )
                } else {
                    TopBar(
                        status = state.moduleStatus,
                        previewOnly = previewOnly,
                        selecting = state.activeTarget != null || state.sheetOpen,
                        unsaved = styleStatus.changed,
                        menuOpen = menuOpen,
                        onDismissMenu = { menuOpen = false },
                        onOpenMenu = { menuOpen = true },
                        onDoneSelection = { viewModel.onTargetSelected(null) },
                        onOpenStatus = onOpenStatus,
                        menuContent = {
                            val updatable = styleStatus.presetStyle
                            if (styleStatus.changed && updatable != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_update_preset)) },
                                    leadingIcon = { AppIcon(R.drawable.ic_save, tint = MaterialTheme.colorScheme.onSurface, size = 20.dp) },
                                    trailingIcon = { Badge(containerColor = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.updatePreset(updatable)
                                    }
                                )
                            }
                            if (styleStatus.changed) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_save_preset)) },
                                    leadingIcon = { AppIcon(R.drawable.ic_plus, tint = MaterialTheme.colorScheme.onSurface, size = 20.dp) },
                                    trailingIcon = if (updatable == null) {
                                        { Badge(containerColor = MaterialTheme.colorScheme.primary) }
                                    } else null,
                                    onClick = {
                                        menuOpen = false
                                        savingStyle = styleName
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tab_settings)) },
                                leadingIcon = { AppIcon(R.drawable.ic_settings, tint = MaterialTheme.colorScheme.onSurface, size = 20.dp) },
                                onClick = {
                                    menuOpen = false
                                    onOpenSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_about)) },
                                leadingIcon = { AppIcon(R.drawable.ic_info, tint = MaterialTheme.colorScheme.onSurface, size = 20.dp) },
                                onClick = {
                                    menuOpen = false
                                    onOpenAbout()
                                }
                            )
                        }
                    )
                }
            }

            val showSideModeTabs by remember(previewHeight, previewWidth) {
                derivedStateOf {
                    sheetProgressAnim.value > 0.01f && morphAnim.value < 1f && fullAnim.value < 1f && previewHeight > 0.dp && previewWidth > 0.dp
                }
            }
            if (showSideModeTabs) {
                var barSize by remember { mutableStateOf(IntSize.Zero) }
                val previewCardWidth = previewHeight * homeScale * aspect
                val gutter = ((previewWidth - previewCardWidth) / 2f).coerceAtLeast(0.dp)
                val maxBarWidth = (gutter - MODE_TABS_GAP * 2).coerceIn(MODE_TABS_MIN_WIDTH, MODE_TABS_MAX_WIDTH)
                if (gutter >= MODE_TABS_MIN_WIDTH + MODE_TABS_GAP) {
                    VerticalPreviewModeTabs(
                        mode = state.previewMode,
                        onModeChange = viewModel::onPreviewModeChange,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(top = 66.dp)
                            .offset {
                                val scaledHeight = previewHeight.toPx() * previewScale()
                                val scaledWidth = scaledHeight * aspect
                                val previewRight = previewWidth.toPx() / 2f + scaledWidth / 2f
                                val availableGutter = (previewWidth.toPx() - previewRight).coerceAtLeast(0f)
                                val x = (previewRight + (availableGutter - barSize.width) / 2f)
                                    .roundToInt()
                                    .coerceIn(0, (previewWidth.toPx() - barSize.width - MODE_TABS_GAP.toPx()).roundToInt().coerceAtLeast(0))
                                val y = (scaledHeight - barSize.height).roundToInt().coerceAtLeast(0)
                                IntOffset(x, y)
                            }
                            .onSizeChanged { barSize = it }
                            .width(maxBarWidth)
                            .graphicsLayer {
                                alpha = revealAnim.value * sheetProgressAnim.value * (1f - morphAnim.value) * (1f - expandAnim.value) * (1f - fullAnim.value)
                            }
                    )
                }
            }

            AnimatedVisibility(
                visible = liveWallpaper && !editing && !facesExpanded && previewHeight > 0.dp,
                enter = fadeIn(tween(260)) + scaleIn(tween(320, easing = Emphasized), initialScale = 0.85f),
                exit = fadeOut(tween(180)) + scaleOut(tween(220), targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 66.dp + BADGE_TOP)
                    .offset {
                        val half = previewHeight.toPx() * aspect * previewScale() / 2f
                        IntOffset((half - BADGE_INSET.toPx() - LIVE_BADGE_SIZE.toPx() / 2f).roundToInt(), 0)
                    }
            ) {
                LiveWallpaperBadge(onClick = { liveInfo = true })
            }

            AnimatedVisibility(
                visible = !sheetOpen && state.preview != null,
                enter = fadeIn(tween(280, delayMillis = 80)) + slideInVertically(tween(360, easing = Emphasized)) { it / 2 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(240, easing = Emphasized)) { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                HorizontalPreviewModeTabs(
                    mode = state.previewMode,
                    onModeChange = viewModel::onPreviewModeChange,
                    modifier = Modifier.widthIn(max = 320.dp)
                )
            }

            val sheetVisible by remember { derivedStateOf { sheetProgressAnim.value > 0.01f } }
            if (sheetVisible) {
                Surface(
                    shape = SheetShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 3.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, bottom = SHEET_MARGIN)
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val sheetH = activeSheetHeight().roundToPx().coerceIn(0, constraints.maxHeight)
                            val placeable = measurable.measure(constraints.copy(minHeight = sheetH, maxHeight = sheetH))
                            layout(placeable.width, placeable.height) {
                                placeable.place(0, 0)
                            }
                        }
                        .offset { IntOffset(0, -lift.roundToPx()) }
                        .graphicsLayer {
                            val progress = sheetProgressAnim.value
                            val morphVal = morphAnim.value
                            val fullVal = fullAnim.value
                            val openVal = openAnim.value
                            val sheetH = activeSheetHeight()
                            alpha = revealAnim.value * progress * (1f - fullVal * (1f - morphVal))
                            val closedOffset = (1f - progress) * (sheetH + navBottom + SHEET_MARGIN).toPx()
                            val hidden = fullVal * (sheetH + navBottom + SHEET_MARGIN).toPx()
                            val peeking = fullVal * (1f - openVal) * (sheetH - SHEET_PEEK + SHEET_MARGIN).toPx().coerceAtLeast(0f)
                            translationY = closedOffset + hidden + (peeking - hidden) * morphVal
                        }
                ) {
                    AnimatedContent(
                        targetState = editing,
                        transitionSpec = {
                            fadeIn(tween(240, delayMillis = 120)) togetherWith fadeOut(tween(140))
                        },
                        contentAlignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                        label = "sheetContent"
                    ) { edit ->
                        if (edit) {
                            EditorPanel(
                                state = editorState,
                                viewModel = editor,
                                onPlayChange = { playToken++ },
                                onPickColor = {
                                    colorTarget = it
                                    pickingColor = it
                                },
                                onPickIcon = { pickingIcon = true },
                                onPickFont = { pickingFont = true },
                                modifier = Modifier.fillMaxSize(),
                                sheetHandle = fullscreen,
                                onSheetDrag = { sheetUp = it },
                                onSheetTap = { sheetUp = !sheetUp }
                            )
                        } else {
                            TargetCustomizationSheet(
                                state = state,
                                expanded = facesExpanded,
                                onExpandedChange = { facesExpanded = it },
                                onDismissSheet = { viewModel.onTargetSelected(null) },
                                onFaceSelected = viewModel::onFaceSelected,
                                onRemoveWidget = viewModel::onRemoveWidget,
                                onAdd = { addingElement = true },
                                onLongPress = { actionsFor = it },
                                onEditItem = ::openEditorFor,
                                onShortcutSlotSelected = viewModel::onShortcutSlotSelected,
                                onShortcutSelected = viewModel::onShortcutSelected
                            )
                        }
                    }
                }
            }

            AddElementDialog(
                visible = addingElement && !editing,
                onAdd = { kind ->
                    addingElement = false
                    if (kind == ElementKind.IMAGE) {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } else {
                        viewModel.onAddElement(kind, elementNames.getValue(kind), defaultText)
                    }
                },
                onDismiss = { addingElement = false }
            )
            val iconLayer = editorState.primary?.let { id -> editorState.face?.layers?.firstOrNull { it.id == id } }
            IconPickerDialog(
                visible = pickingIcon && editing && iconLayer != null,
                font = iconLayer?.font.orEmpty(),
                selected = iconLayer?.format,
                onPick = { glyph ->
                    iconLayer?.let { layer -> editor.updateLayer(layer.id, "icon") { it.copy(format = glyph) } }
                    pickingIcon = false
                },
                onDismiss = { pickingIcon = false }
            )
            FontPickerDialog(
                visible = pickingFont && editing,
                selected = editorState.primary?.let { id -> editorState.face?.layers?.firstOrNull { it.id == id } }?.font,
                userFonts = userFonts,
                onPick = { family ->
                    editor.setFont(family)
                    pickingFont = false
                },
                onAdd = { fontPicker.launch(FONT_TYPES) },
                onDismiss = { pickingFont = false }
            )
            ColorPickerDialog(
                visible = pickingColor != null && editing,
                initial = if (colorTarget == ColorTarget.BORDER) editor.borderColor() else editor.color(),
                resolve = { FaceColors.resolve(context, it) },
                onApply = { hex ->
                    if (colorTarget == ColorTarget.BORDER) editor.setBorder(hex) else editor.setColor(hex)
                    pickingColor = null
                },
                onDismiss = { pickingColor = null },
            )
            FaceActionsDialog(
                preset = actionsFor,
                onDuplicate = { naming = it },
                onDelete = viewModel::onDelete,
                onDismiss = { actionsFor = null }
            )
            SavePresetDialog(
                initialName = naming?.let { stringResource(R.string.face_copy_name, it.face.name) },
                onSave = { name -> naming?.let { viewModel.onDuplicate(it, name) } },
                onDismiss = { naming = null }
            )
            SavePresetDialog(
                initialName = savingStyle,
                onSave = { name -> viewModel.saveAsPreset(name) },
                onDismiss = { savingStyle = null }
            )
            ExpressiveDialog(
                visible = liveInfo,
                title = stringResource(R.string.live_wallpaper_title),
                message = stringResource(R.string.live_wallpaper_message),
                confirmLabel = stringResource(R.string.action_got_it),
                onDismiss = { liveInfo = false }
            )
            ExpressiveDialog(
                visible = previewInfo,
                title = stringResource(R.string.preview_mode_title),
                message = stringResource(R.string.preview_mode_message),
                confirmLabel = stringResource(R.string.action_got_it),
                onDismiss = { previewInfo = false }
            )
        }
    }
}

@Composable
private fun HomePreview(state: HomeUiState, viewModel: HomeViewModel) {
    val geometry = LocalClockGeometry.current
    val spec = tween<Float>(durationMillis = 700, easing = Emphasized)
    var aodLarge by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(state.previewMode) {
        if (state.previewMode != PreviewMode.AOD) aodLarge = state.previewMode == PreviewMode.LARGE
    }
    val showLarge = if (state.previewMode == PreviewMode.AOD) aodLarge else state.previewMode == PreviewMode.LARGE
    val itemState = when {
        state.previewMode == PreviewMode.AOD && showLarge -> ItemState.AOD_LARGE
        state.previewMode == PreviewMode.AOD -> ItemState.AOD_SMALL
        showLarge -> ItemState.LARGE
        else -> ItemState.SMALL
    }
    val largeAnim = animateFloatAsState(if (showLarge) 1f else 0f, spec, label = "large")
    val sleepAnim = animateFloatAsState(if (state.previewMode == PreviewMode.AOD) 1f else 0f, spec, label = "sleep")
    val settled by remember(state.dragging, showLarge, state.previewMode) {
        derivedStateOf {
            !state.dragging &&
                largeAnim.value == (if (showLarge) 1f else 0f) &&
                sleepAnim.value == (if (state.previewMode == PreviewMode.AOD) 1f else 0f)
        }
    }
    var extents by remember { mutableStateOf(emptyMap<String, RectF>()) }
    val gridIds = remember(state.gridWidgets) { state.gridWidgets.mapTo(HashSet()) { it.id } }
    val inlineIds = remember(state.inlineWidgets) { state.inlineWidgets.mapTo(HashSet()) { it.id } }

    LockscreenPreview(
        face = state.preview,
        modifier = Modifier.fillMaxSize(),
        largeProgress = { largeAnim.value },
        sleepProgress = { sleepAnim.value },
        onExtents = { extents = it },
        animateChanges = !state.dragging,
        overlay = {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val frame = geometry.frame(largeAnim.value)
                ItemOverlay(
                    extents = extents,
                    composition = state.composition,
                    itemState = itemState,
                    gridWidgetIds = gridIds,
                    inlineWidgetIds = inlineIds,
                    activeTarget = state.activeTarget,
                    activeShortcutSlot = state.activeShortcutSlot,
                    shortcutLeft = state.shortcutLeft,
                    shortcutRight = state.shortcutRight,
                    isAod = state.previewMode == PreviewMode.AOD,
                    unit = constraints.maxWidth * frame.width,
                    frameLeft = constraints.maxWidth * frame.left,
                    frameTop = constraints.maxHeight * frame.top,
                    width = constraints.maxWidth.toFloat(),
                    height = constraints.maxHeight.toFloat(),
                    settled = settled,
                    onGestureStart = viewModel::onGestureStart,
                    onSelectTarget = viewModel::onTargetSelected,
                    onDrag = { deltas, gridDocked, inlineDocked ->
                        viewModel.onTargetsDrag(itemState, deltas, gridDocked, inlineDocked)
                    },
                    onClockTransform = viewModel::onClockTransform,
                    onGridSizeChange = viewModel::onGridSizeChange,
                    onInlineRowsChange = viewModel::onInlineRowsChange,
                    onDragEnd = viewModel::onTargetDragEnd,
                    onToggleWidgetsPosition = viewModel::onToggleWidgetsPosition,
                    onMeasured = { bounds, maxY, maxYByState ->
                        viewModel.onItemsMeasured(itemState, bounds, maxY, maxYByState)
                    }
                )
            }
        }
    )
}

@Composable
private fun HorizontalPreviewModeTabs(
    mode: PreviewMode,
    onModeChange: (PreviewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PreviewMode.entries.forEach { entry ->
                val isSelected = entry == mode
                val interaction = remember { MutableInteractionSource() }
                val shape = expressivePillShape(interaction, restCorner = 22.dp, pressedCorner = 12.dp)
                val containerColor by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    tween(220),
                    label = "hModeContainer"
                )
                val contentColor by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    tween(220),
                    label = "hModeContent"
                )
                Surface(
                    onClick = { onModeChange(entry) },
                    shape = shape,
                    color = containerColor,
                    contentColor = contentColor,
                    interactionSource = interaction,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .semantics {
                            selected = isSelected
                            role = Role.Tab
                        }
                        .testTag("preview_mode_${entry.name.lowercase()}")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        FitText(
                            text = stringResource(entry.label),
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalPreviewModeTabs(
    mode: PreviewMode,
    onModeChange: (PreviewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PreviewMode.entries.forEach { entry ->
                val isSelected = entry == mode
                val interaction = remember { MutableInteractionSource() }
                val shape = expressivePillShape(interaction, restCorner = 20.dp, pressedCorner = 12.dp)
                val containerColor by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    tween(220),
                    label = "vModeContainer"
                )
                val contentColor by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    tween(220),
                    label = "vModeContent"
                )
                Surface(
                    onClick = { onModeChange(entry) },
                    shape = shape,
                    color = containerColor,
                    contentColor = contentColor,
                    interactionSource = interaction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
                        .semantics {
                            selected = isSelected
                            role = Role.Tab
                        }
                        .testTag("preview_mode_${entry.name.lowercase()}")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        FitText(
                            text = stringResource(entry.label),
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditPreview(state: EditorUiState, editor: EditorViewModel, playToken: Int, guides: Boolean) {
    val face = state.face ?: return
    val spec = tween<Float>(durationMillis = 700, easing = Emphasized)
    val showLarge = if (state.scope == EditScope.AOD) state.aodLarge else state.scope == EditScope.LARGE
    val large by animateFloatAsState(if (showLarge) 1f else 0f, spec, label = "large")
    val aod by animateFloatAsState(if (state.scope == EditScope.AOD) 1f else 0f, spec, label = "aod")
    EditorCanvas(
        face = face,
        selectedId = state.selectedId,
        large = large,
        sleep = aod,
        playToken = playToken,
        onSelect = editor::select,
        onMove = editor::move,
        onMoveAll = editor::moveAll,
        onResizeStart = editor::axes,
        onResize = editor::resize,
        onTransformStart = editor::transformBase,
        onTransform = editor::transform,
        onGestureEnd = editor::endGesture,
        modifier = Modifier.fillMaxSize(),
        onBounds = editor::reportBounds,
        showGuides = guides,
        grouping = state.grouping?.mapTo(HashSet()) { ItemGroups.keyOfItem(it, false) },
        onToggleGrouping = editor::toggleGrouping,
        canRemove = editor::canRemoveTarget,
        onRemove = editor::removeTarget,
        alignOf = editor::alignOf,
        onAlign = editor::alignTarget
    )
}

@Composable
private fun TopBar(
    status: ModuleStatus,
    previewOnly: Boolean,
    selecting: Boolean,
    unsaved: Boolean,
    menuOpen: Boolean,
    onDismissMenu: () -> Unit,
    onOpenMenu: () -> Unit,
    onDoneSelection: () -> Unit,
    onOpenStatus: () -> Unit,
    menuContent: @Composable () -> Unit
) {
    Row(
        Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        AnimatedVisibility(
            visible = !selecting,
            enter = fadeIn(tween(200)) + scaleIn(tween(240, easing = Emphasized), initialScale = 0.88f),
            exit = fadeOut(tween(150)) + scaleOut(tween(180), targetScale = 0.88f)
        ) {
            val showPreview = previewOnly && status != ModuleStatus.ACTIVE
            StatusPill(
                text = stringResource(
                    when {
                        showPreview -> R.string.module_preview
                        status == ModuleStatus.ACTIVE -> R.string.module_active
                        status == ModuleStatus.ATTENTION -> R.string.module_attention
                        else -> R.string.module_inactive
                    }
                ),
                tone = when {
                    showPreview -> StatusTone.WARNING
                    status == ModuleStatus.ACTIVE -> StatusTone.OK
                    status == ModuleStatus.ATTENTION -> StatusTone.WARNING
                    else -> StatusTone.ERROR
                },
                onClick = onOpenStatus
            )
        }
        AnimatedVisibility(
            visible = selecting,
            enter = fadeIn(tween(200)) + scaleIn(tween(240, easing = Emphasized), initialScale = 0.88f),
            exit = fadeOut(tween(150)) + scaleOut(tween(180), targetScale = 0.88f)
        ) {
            ExpressiveButton(
                onClick = onDoneSelection,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.testTag("home_done_button")
            ) {
                AppIcon(R.drawable.ic_check, tint = MaterialTheme.colorScheme.onPrimary, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_done), style = MaterialTheme.typography.labelLarge)
            }
        }
        val dot by animateFloatAsState(if (unsaved) 1f else 0f, tween(260, easing = Emphasized), label = "unsavedDot")
        Box {
            ExpressiveIconButton(
                icon = R.drawable.ic_more,
                onClick = onOpenMenu,
                description = stringResource(R.string.action_more),
                modifier = Modifier.testTag("home_more_button")
            )
            StatusDot(
                MaterialTheme.colorScheme.primary,
                size = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .graphicsLayer {
                        alpha = dot
                        scaleX = dot
                        scaleY = dot
                    }
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = onDismissMenu,
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                menuContent()
            }
        }
    }
}

@Composable
private fun TargetCustomizationSheet(
    state: HomeUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDismissSheet: () -> Unit,
    onFaceSelected: (Int) -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onAdd: () -> Unit,
    onLongPress: (FacePreset) -> Unit,
    onEditItem: (Int?) -> Unit,
    onShortcutSlotSelected: (ShortcutSlot) -> Unit,
    onShortcutSelected: (ShortcutSlot, String) -> Unit
) {
    val target = state.activeTarget ?: return
    val density = LocalDensity.current
    val threshold = with(density) { DRAG_THRESHOLD.toPx() }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .pointerInput(expanded) {
                    var travel = 0f
                    detectVerticalDragGestures(
                        onDragStart = { travel = 0f },
                        onDragEnd = {
                            when {
                                travel < -threshold -> onExpandedChange(true)
                                travel > threshold && expanded -> onExpandedChange(false)
                                travel > threshold && !expanded -> onDismissSheet()
                            }
                        }
                    ) { change, amount ->
                        change.consume()
                        travel += amount
                    }
                }
        ) {
            Surface(
                onClick = { onExpandedChange(!expanded) },
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(GRABBER_AREA)
                    .testTag("sheet_grabber")
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(
                        Modifier
                            .padding(top = GRABBER_TOP)
                            .size(GRABBER_WIDTH, GRABBER_HEIGHT)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f))
                    )
                }
            }

            AnimatedContent(
                targetState = target,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
                label = "sheetHeader"
            ) { current ->
                when (current) {
                    LockTarget.CLOCK -> ClockSheetHeader(
                        presetName = state.clockPreset?.face?.name,
                        canEdit = state.clockPreset != null,
                        onFontAndColor = { onEditItem(state.composition.clockItem?.id) }
                    )

                    LockTarget.WIDGET_GRID -> WidgetSheetHeader(
                        title = stringResource(R.string.sheet_widgets_1x4),
                        usage = stringResource(R.string.slots_used_grid, state.gridCellsUsed, state.composition.gridCapacity),
                        hasItems = state.gridWidgets.isNotEmpty(),
                        onAddCustom = onAdd,
                        onEditFirst = { onEditItem(state.selectedItem ?: state.gridWidgets.firstOrNull()?.id) }
                    )

                    LockTarget.INLINE_GRID -> WidgetSheetHeader(
                        title = stringResource(R.string.sheet_inline_1x2),
                        usage = stringResource(R.string.slots_used_inline, state.inlineCount),
                        hasItems = state.inlineWidgets.isNotEmpty(),
                        onAddCustom = onAdd,
                        onEditFirst = { onEditItem(state.selectedItem ?: state.inlineWidgets.firstOrNull()?.id) }
                    )

                    LockTarget.SHORTCUTS -> ShortcutsSheetHeader(
                        activeSlot = state.activeShortcutSlot,
                        leftShortcut = state.shortcutLeft,
                        rightShortcut = state.shortcutRight,
                        onSlotSelected = onShortcutSlotSelected
                    )
                }
            }
        }

        AnimatedContent(
            targetState = target,
            transitionSpec = { fadeIn(tween(220, delayMillis = 50)) togetherWith fadeOut(tween(140)) },
            modifier = Modifier.weight(1f),
            label = "sheetBody"
        ) { current ->
            when (current) {
                LockTarget.CLOCK -> ClockCarouselsBody(
                    sections = remember(state.faces) { state.clockCarousels() },
                    previews = state.presetPreviews,
                    checked = state::isChecked,
                    onSelect = onFaceSelected,
                    onLongPress = onLongPress
                )

                LockTarget.WIDGET_GRID -> WidgetCarouselsBody(
                    zone = WidgetZone.GRID_1X4,
                    placedItems = state.gridWidgets,
                    sections = remember(state.faces) { state.widgetCarousels(WidgetZone.GRID_1X4) },
                    faceOf = state::faceOf,
                    checked = state::isChecked,
                    onSelectPreset = onFaceSelected,
                    onEditPlaced = onEditItem,
                    onRemovePlaced = onRemoveWidget
                )

                LockTarget.INLINE_GRID -> WidgetCarouselsBody(
                    zone = WidgetZone.INLINE_1X2,
                    placedItems = state.inlineWidgets,
                    sections = remember(state.faces) { state.widgetCarousels(WidgetZone.INLINE_1X2) },
                    faceOf = state::faceOf,
                    checked = state::isChecked,
                    onSelectPreset = onFaceSelected,
                    onEditPlaced = onEditItem,
                    onRemovePlaced = onRemoveWidget
                )

                LockTarget.SHORTCUTS -> {
                    val currentSelected = if (state.activeShortcutSlot == ShortcutSlot.LEFT) {
                        state.shortcutLeft
                    } else {
                        state.shortcutRight
                    }
                    ShortcutsSheetBody(
                        affordances = state.availableAffordances,
                        currentShortcut = currentSelected,
                        onSelect = { affordanceId -> onShortcutSelected(state.activeShortcutSlot, affordanceId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClockSheetHeader(
    presetName: String?,
    canEdit: Boolean,
    onFontAndColor: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.sheet_clock_style),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!presetName.isNullOrBlank()) {
                Text(
                    text = presetName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        ExpressiveTonalButton(
            onClick = onFontAndColor,
            enabled = canEdit,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier.testTag("clock_font_color_button")
        ) {
            AppIcon(R.drawable.ic_edit, tint = MaterialTheme.colorScheme.onSecondaryContainer, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.action_font_color),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WidgetSheetHeader(
    title: String,
    usage: String,
    hasItems: Boolean,
    onAddCustom: () -> Unit,
    onEditFirst: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = usage,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        ExpressiveTonalButton(
            onClick = onAddCustom,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier.testTag("widget_add_custom_button")
        ) {
            AppIcon(R.drawable.ic_plus, tint = MaterialTheme.colorScheme.onSecondaryContainer, size = 18.dp)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.action_custom), style = MaterialTheme.typography.labelLarge)
        }
        if (hasItems) {
            ExpressiveIconButton(
                icon = R.drawable.ic_edit,
                onClick = onEditFirst,
                size = 44.dp,
                description = stringResource(R.string.action_edit_selected),
                filled = true,
                modifier = Modifier.testTag("widget_edit_button")
            )
        }
    }
}

@Composable
private fun ShortcutsSheetHeader(
    activeSlot: ShortcutSlot,
    leftShortcut: LockscreenAffordance,
    rightShortcut: LockscreenAffordance,
    onSlotSelected: (ShortcutSlot) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.sheet_shortcuts),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    Triple(ShortcutSlot.LEFT, R.string.shortcut_slot_left, leftShortcut),
                    Triple(ShortcutSlot.RIGHT, R.string.shortcut_slot_right, rightShortcut)
                ).forEach { (slot, labelRes, shortcut) ->
                    val isSelected = activeSlot == slot
                    val interaction = remember { MutableInteractionSource() }
                    val shape = expressivePillShape(interaction, restCorner = 24.dp, pressedCorner = 14.dp)
                    val containerColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        tween(200),
                        label = "shortcutTabBg"
                    )
                    val contentColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        tween(200),
                        label = "shortcutTabFg"
                    )
                    Surface(
                        onClick = { onSlotSelected(slot) },
                        shape = shape,
                        color = containerColor,
                        contentColor = contentColor,
                        interactionSource = interaction,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .semantics {
                                selected = isSelected
                                role = Role.Tab
                            }
                            .testTag("shortcut_slot_tab_${slot.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppIcon(
                                icon = shortcut.iconRes,
                                tint = contentColor,
                                size = 18.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = contentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = affordanceLabel(shortcut),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentColor.copy(alpha = 0.78f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutsSheetBody(
    affordances: List<LockscreenAffordance>,
    currentShortcut: LockscreenAffordance,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(affordances, key = { it.id }) { affordance ->
            val isSelected = affordance.id.equals(currentShortcut.id, ignoreCase = true)
            val isEnabled = affordance.isEnabled
            val interaction = remember { MutableInteractionSource() }
            val shape = expressivePillShape(interaction, restCorner = 22.dp, pressedCorner = 14.dp)
            val containerColor by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                tween(200),
                label = "shortcutRowContainer"
            )
            val contentColor by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                tween(200),
                label = "shortcutRowContent"
            )
            val iconCircleColor by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                tween(200),
                label = "shortcutIconBg"
            )
            val iconTint by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                tween(200),
                label = "shortcutIconFg"
            )
            val explanation = affordance.explanation?.takeIf { it.isNotBlank() }
            Surface(
                onClick = {
                    if (isEnabled) {
                        onSelect(affordance.id)
                    } else if (explanation != null) {
                        Toast.makeText(context, explanation, Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = isEnabled || explanation != null,
                shape = shape,
                color = containerColor,
                contentColor = contentColor,
                interactionSource = interaction,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .alpha(if (isEnabled) 1f else 0.55f)
                    .semantics {
                        selected = isSelected
                        role = Role.RadioButton
                    }
                    .testTag("shortcut_option_${affordance.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(iconCircleColor),
                        contentAlignment = Alignment.Center
                    ) {
                        AppIcon(
                            icon = affordance.iconRes,
                            tint = iconTint,
                            size = 20.dp
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = affordanceLabel(affordance),
                            style = MaterialTheme.typography.titleSmall,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!isEnabled && explanation != null) {
                            Text(
                                text = explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            AppIcon(
                                icon = R.drawable.ic_check,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                size = 14.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun affordanceLabel(affordance: LockscreenAffordance): String {
    val fallback = affordance.knownShortcut?.let { stringResource(it.labelRes) }
    return affordance.displayLabel(fallback)
}

@Composable
private fun ClockCarouselsBody(
    sections: List<FaceSection>,
    previews: Map<Int, ClockFace>,
    checked: (FacePreset) -> Boolean,
    onSelect: (Int) -> Unit,
    onLongPress: (FacePreset) -> Unit
) {
    CompositionLocalProvider(LocalFaceData provides LocalSampleFaceData.current) {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 6.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(sections, key = { it.category }) { section ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CarouselSectionHeader(
                        title = categoryLabel(section.category),
                        count = section.faces.size
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(section.faces, key = { it.style }) { preset ->
                            ClockCarouselCard(
                                preset = preset,
                                face = previews[preset.style] ?: preset.face,
                                selected = checked(preset),
                                onClick = { onSelect(preset.style) },
                                onLongClick = { onLongPress(preset) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetCarouselsBody(
    zone: WidgetZone,
    placedItems: List<CompositionItem>,
    sections: List<FaceSection>,
    faceOf: (Int) -> ClockFace?,
    checked: (FacePreset) -> Boolean,
    onSelectPreset: (Int) -> Unit,
    onEditPlaced: (Int) -> Unit,
    onRemovePlaced: (Int) -> Unit
) {
    CompositionLocalProvider(LocalFaceData provides LocalSampleFaceData.current) {
        val isInline = zone == WidgetZone.INLINE_1X2
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (placedItems.isNotEmpty()) {
                item(key = "placed_strip") {
                    PlacedSlotsRow(
                        items = placedItems,
                        faceOf = faceOf,
                        onEdit = onEditPlaced,
                        onRemove = onRemovePlaced
                    )
                }
            }
            items(sections, key = { it.category }) { section ->
                val visibleFaces = remember(section.faces) {
                    section.faces.filterNot { it.face.category == CustomFaces.ELEMENT_CATEGORY }
                }
                if (visibleFaces.isEmpty()) return@items
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CarouselSectionHeader(
                        title = categoryLabel(section.category),
                        count = visibleFaces.size
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(visibleFaces, key = { it.style }) { preset ->
                            WidgetCarouselCard(
                                preset = preset,
                                inline = isInline,
                                selected = checked(preset),
                                onClick = { onSelectPreset(preset.style) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlacedSlotsRow(
    items: List<CompositionItem>,
    faceOf: (Int) -> ClockFace?,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) { item ->
            val label = faceOf(item.style)?.name ?: stringResource(R.string.panel_item)
            val removeDesc = stringResource(R.string.action_remove)
            Surface(
                onClick = { onEdit(item.id) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("placed_slot_${item.id}")
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 128.dp)
                    )
                    IconButton(
                        onClick = { onRemove(item.id) },
                        modifier = Modifier
                            .size(40.dp)
                            .semantics { contentDescription = "$removeDesc $label" }
                            .testTag("remove_slot_${item.id}")
                    ) {
                        AppIcon(
                            icon = R.drawable.ic_cross,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            size = 16.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CarouselSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ClockCarouselCard(
    preset: FacePreset,
    face: ClockFace,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val labelColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(200),
        label = "clockLabel"
    )
    val ring by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        tween(200),
        label = "clockRing"
    )
    val shape = RoundedCornerShape(CARD_RADIUS)
    Column(
        modifier = Modifier
            .width(CLOCK_CARD_WIDTH)
            .clip(shape)
            .combinedClickable(
                interactionSource = null,
                indication = ripple(),
                role = Role.RadioButton,
                onLongClick = onLongClick,
                onClick = onClick
            )
            .testTag("clock_card_${preset.style}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LABEL_GAP)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(ThumbInk)
                .border(if (selected) CARD_RING else 1.dp, ring, shape)
                .padding(CLOCK_PADDING),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalPalette provides DarkPalette) {
                ClockFacePreview(face = face, modifier = Modifier.fillMaxWidth())
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(R.drawable.ic_check, tint = MaterialTheme.colorScheme.onPrimary, size = 12.dp)
                }
            }
        }
        MarqueeText(
            preset.face.name,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun WidgetCarouselCard(
    preset: FacePreset,
    inline: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val labelColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(200),
        label = "widgetLabel"
    )
    val ring by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        tween(200),
        label = "widgetRing"
    )
    val span = preset.face.span.coerceIn(1, FaceGrid.ROW_CELLS)
    val cardWidth = when {
        inline -> INLINE_CARD_WIDTH
        span >= 2 -> WIDGET_CARD_WIDE
        else -> WIDGET_CARD_UNIT
    }
    val previewSpan = if (inline) 2 else span
    val shape = RoundedCornerShape(CARD_RADIUS)

    Column(
        modifier = Modifier
            .width(cardWidth)
            .clip(shape)
            .combinedClickable(
                interactionSource = null,
                indication = ripple(),
                role = Role.Checkbox,
                onClick = onClick
            )
            .testTag("widget_card_${preset.style}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LABEL_GAP)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .clip(shape)
                .background(ThumbInk)
                .border(if (selected) CARD_RING else 1.dp, ring, shape)
                .padding(BLOCK_PADDING),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalPalette provides DarkPalette) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds(),
                    contentAlignment = Alignment.Center
                ) {
                    ClockFacePreview(
                        face = preset.face,
                        modifier = Modifier.requiredWidth(maxWidth / FaceGrid.width(previewSpan)),
                        fit = false
                    )
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(R.drawable.ic_check, tint = MaterialTheme.colorScheme.onPrimary, size = 12.dp)
                }
            }
        }
        MarqueeText(
            preset.face.name,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private val KEYBOARD_SHEET = 280.dp
private const val KEYBOARD_MS = 280
private val MODE_TABS_GAP = 10.dp
private val MODE_TABS_MIN_WIDTH = 76.dp
private val MODE_TABS_MAX_WIDTH = 96.dp

private val FONT_TYPES = arrayOf(
    "font/*",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/font-sfnt",
    "application/vnd.ms-opentype",
    "application/octet-stream"
)
