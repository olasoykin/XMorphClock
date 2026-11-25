package com.drdisagree.xmorphclock.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.drdisagree.xmorphclock.di.AppContainer

fun appViewModelFactory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        AppViewModel(
            observeWallpaper = container.observeWallpaper,
            observeHookReport = container.observeHookReport,
            observeFramework = container.observeFramework,
            requestHookReport = container.requestHookReport,
            observeSettings = container.observeSettings,
            syncAppliedFace = container.syncAppliedFace,
            observeFaceData = container.observeFaceData,
            observeSampleFaceData = container.observeSampleFaceData,
            observeDepthSettings = container.observeDepthSettings,
            observeDepthSubject = container.observeDepthSubject
        )
    }
}
