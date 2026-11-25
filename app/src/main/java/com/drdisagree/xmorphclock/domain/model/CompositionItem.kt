package com.drdisagree.xmorphclock.domain.model

data class CompositionItem(
    val id: Int,
    val clock: Boolean = false,
    val style: Int = -1,
    val large: FacePlacement? = null,
    val small: FacePlacement? = null,
    val aodLarge: FacePlacement? = null,
    val aodSmall: FacePlacement? = null,
    val hidden: Set<ItemState> = emptySet(),
    val largeScale: Float = 1f,
    val smallScale: Float = 1f,
    val largeWidthStretch: Float = 1f,
    val largeHeightStretch: Float = 1f,
    val smallWidthStretch: Float = 1f,
    val smallHeightStretch: Float = 1f
) {
    val isDefault: Boolean
        get() = large == null && small == null && aodLarge == null && aodSmall == null &&
            hidden.isEmpty() && largeScale == 1f && smallScale == 1f &&
            largeWidthStretch == 1f && largeHeightStretch == 1f &&
            smallWidthStretch == 1f && smallHeightStretch == 1f

    fun visible(state: ItemState): Boolean = state !in hidden

    fun scale(state: ItemState): Float = if (state == ItemState.LARGE || state == ItemState.AOD_LARGE) largeScale else smallScale

    fun withScale(state: ItemState, value: Float): CompositionItem =
        if (state == ItemState.LARGE || state == ItemState.AOD_LARGE) copy(largeScale = value) else copy(smallScale = value)

    fun widthStretch(state: ItemState): Float =
        if (state == ItemState.LARGE || state == ItemState.AOD_LARGE) largeWidthStretch else smallWidthStretch

    fun heightStretch(state: ItemState): Float =
        if (state == ItemState.LARGE || state == ItemState.AOD_LARGE) largeHeightStretch else smallHeightStretch

    fun withTransform(state: ItemState, scale: Float, widthStretch: Float, heightStretch: Float): CompositionItem =
        if (state == ItemState.LARGE || state == ItemState.AOD_LARGE) {
            copy(largeScale = scale, largeWidthStretch = widthStretch, largeHeightStretch = heightStretch)
        } else {
            copy(smallScale = scale, smallWidthStretch = widthStretch, smallHeightStretch = heightStretch)
        }

    val hasAod: Boolean
        get() = aodLarge != null || aodSmall != null || ItemState.AOD_LARGE in hidden || ItemState.AOD_SMALL in hidden

    fun placement(state: ItemState): FacePlacement? = when (state) {
        ItemState.LARGE -> large
        ItemState.SMALL -> small
        ItemState.AOD_LARGE -> aodLarge ?: large
        ItemState.AOD_SMALL -> aodSmall ?: small
    }

    fun with(state: ItemState, placement: FacePlacement): CompositionItem = when (state) {
        ItemState.LARGE -> copy(large = placement)
        ItemState.SMALL -> copy(small = placement)
        ItemState.AOD_LARGE -> copy(aodLarge = placement)
        ItemState.AOD_SMALL -> copy(aodSmall = placement)
    }
}
