package com.gizwits.smart.app.core.data.di

import com.gizwits.smart.app.core.data.util.ConnectivityManagerNetworkMonitor
import com.gizwits.smart.app.core.data.util.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Binds
    fun bindsNetworkMonitor(
        networkMonitor: ConnectivityManagerNetworkMonitor,
    ): NetworkMonitor

}