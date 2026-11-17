package com.tamerslab.di

import com.tamerslab.battle.online.RetrofitHelper
import com.tamerslab.di.repository.ArenaBattleRepository
import com.tamerslab.di.repository.RoomArenaRosterGateway
import com.tamerslab.di.repository.ContainerChatGateway
import com.tamerslab.di.repository.ContainerFarmGateway
import com.tamerslab.di.repository.ContainerSaveImportRepository
import com.tamerslab.di.repository.ContainerStorageGateway
import com.tamerslab.di.repository.ContainerTransferRepository
import com.tamerslab.di.repository.ContainerVpetRepository
import com.tamerslab.domain.repository.ArenaRosterGateway
import com.tamerslab.domain.repository.BattleRepository
import com.tamerslab.domain.repository.ChatGateway
import com.tamerslab.domain.repository.FarmGateway
import com.tamerslab.domain.repository.SaveImportRepository
import com.tamerslab.domain.repository.StorageGateway
import com.tamerslab.domain.repository.TransferRepository
import com.tamerslab.domain.vpet.VpetRepository
import com.tamerslab.domain.usecase.BattleUseCase
import com.tamerslab.domain.usecase.ChatUseCase
import com.tamerslab.domain.usecase.FarmUseCase
import com.tamerslab.domain.usecase.SaveImportUseCase
import com.tamerslab.domain.usecase.TransferUseCase
import com.tamerslab.battle.ui.arena.TamerArenaViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module as koinModule

val appModule = koinModule {
    single { DefaultAppContainer(androidContext()) }
    single<AppContainer> { get<DefaultAppContainer>() }
}

val databaseModule = koinModule {
    single { get<AppContainer>().db }
}

val networkModule = koinModule {
    single { RetrofitHelper() }
}

val hardwareModule = koinModule {
}

val repositoryModule = koinModule {
    single<TransferRepository> { ContainerTransferRepository(get()) }
    single<BattleRepository> { ArenaBattleRepository(get<AppContainer>().db, get<AppContainer>().currencyRepository) }
    single<SaveImportRepository> { ContainerSaveImportRepository(get()) }
    single<ChatGateway> { ContainerChatGateway(get()) }
    single<FarmGateway> { ContainerFarmGateway(get()) }
    single<StorageGateway> { ContainerStorageGateway(get()) }
    single<ArenaRosterGateway> { RoomArenaRosterGateway(get<AppContainer>().db, androidContext()) }
    single<VpetRepository> { ContainerVpetRepository(get()) }
}

val useCaseModule = koinModule {
    single { TransferUseCase(get()) }
    single { BattleUseCase(get()) }
    single { SaveImportUseCase(get()) }
    single { ChatUseCase(get()) }
    single { FarmUseCase(get()) }
}

val viewModelModule = koinModule {
    factory { TamerArenaViewModel(androidContext() as TamersLabApplication, get(), get()) }
}

val appModules = listOf(
    appModule,
    databaseModule,
    networkModule,
    hardwareModule,
    repositoryModule,
    useCaseModule,
    viewModelModule,
)


