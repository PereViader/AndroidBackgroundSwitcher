package com.pereviader.backgroundswitcher.di

import com.pereviader.backgroundswitcher.data.repository.AlbumRepositoryImpl
import com.pereviader.backgroundswitcher.data.repository.ScheduleRepositoryImpl
import com.pereviader.backgroundswitcher.data.repository.WallpaperListRepositoryImpl
import com.pereviader.backgroundswitcher.data.repository.WallpaperRepositoryImpl
import com.pereviader.backgroundswitcher.domain.repository.AlbumRepository
import com.pereviader.backgroundswitcher.domain.repository.ScheduleRepository
import com.pereviader.backgroundswitcher.domain.repository.WallpaperListRepository
import com.pereviader.backgroundswitcher.domain.repository.WallpaperRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAlbumRepository(impl: AlbumRepositoryImpl): AlbumRepository

    @Binds
    @Singleton
    abstract fun bindWallpaperRepository(impl: WallpaperRepositoryImpl): WallpaperRepository

    @Binds
    @Singleton
    abstract fun bindWallpaperListRepository(impl: WallpaperListRepositoryImpl): WallpaperListRepository

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(impl: ScheduleRepositoryImpl): ScheduleRepository
}
