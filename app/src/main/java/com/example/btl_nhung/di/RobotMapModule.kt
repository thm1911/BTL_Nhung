package com.example.btl_nhung.di

import com.example.btl_nhung.data.repository.MqttRobotMapRepository
import com.example.btl_nhung.data.repository.RobotMapRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RobotMapModule {

    @Binds
    @Singleton
    abstract fun bindRobotMapRepository(impl: MqttRobotMapRepository): RobotMapRepository
}
