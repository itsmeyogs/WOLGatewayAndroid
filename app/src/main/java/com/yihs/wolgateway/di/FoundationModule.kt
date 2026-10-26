package com.yihs.wolgateway.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.yihs.wolgateway.data.local.DeviceDao
import com.yihs.wolgateway.data.local.GatewayDatabase
import com.yihs.wolgateway.data.local.MIGRATION_1_2
import com.yihs.wolgateway.data.preferences.gatewayPreferences
import com.yihs.wolgateway.data.repository.RoomDeviceRepository
import com.yihs.wolgateway.domain.repository.DeviceRepository
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
    fun provideDatabase(@ApplicationContext context: Context): GatewayDatabase =
        Room.databaseBuilder(context, GatewayDatabase::class.java, "wol_gateway.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideDeviceDao(database: GatewayDatabase): DeviceDao = database.deviceDao()

    @Provides
    @Singleton
    fun provideDeviceRepository(repository: RoomDeviceRepository): DeviceRepository = repository

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        context.gatewayPreferences()

    @Provides
    @Singleton
    fun provideGatewayServer(server: KtorGatewayServer): GatewayServer = server
}
