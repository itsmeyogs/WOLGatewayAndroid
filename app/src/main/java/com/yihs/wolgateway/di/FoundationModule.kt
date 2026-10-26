package com.yihs.wolgateway.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.yihs.wolgateway.data.local.FoundationDatabase
import com.yihs.wolgateway.data.preferences.gatewayPreferences
import com.yihs.wolgateway.gateway.server.GatewayServer
import com.yihs.wolgateway.gateway.server.KtorGatewayServer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FoundationModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FoundationDatabase =
        Room.databaseBuilder(context, FoundationDatabase::class.java, "wol_gateway.db").build()

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        context.gatewayPreferences()

    @Provides
    @Singleton
    fun provideGatewayServer(server: KtorGatewayServer): GatewayServer = server
}
