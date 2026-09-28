package info.cemu.cemu.settings.general

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import info.cemu.cemu.common.settings.GamePadPosition
import info.cemu.cemu.common.ui.components.Button
import info.cemu.cemu.common.ui.components.ScreenContent
import info.cemu.cemu.common.ui.components.SingleSelection
import info.cemu.cemu.common.ui.localization.tr
import info.cemu.cemu.nativeinterface.NativeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GeneralSettingsScreen(
    navigateBack: () -> Unit,
    goToGamePathsSettings: () -> Unit,
    viewModel: GeneralSettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val emulationSettings by viewModel.emulationSettings.collectAsState()
    val guiSettings by viewModel.guiSettings.collectAsState()

    val systemArchiveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) { SystemImport.import(context, uri) }
            val message = when (result) {
                is SystemImportResult.Success -> String.format(
                    tr("System files imported: %s"),
                    result.imported.joinToString(", ")
                )

                is SystemImportResult.Failed -> String.format(
                    tr("Import failed: %s"),
                    result.reason
                )

                SystemImportResult.InvalidArchive -> tr("That file is not a valid .zip archive")
                SystemImportResult.NothingUsefulFound ->
                    tr("No system files found in the archive")
            }
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message, withDismissAction = true)
        }
    }

    ScreenContent(
        appBarText = tr("General settings"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        navigateBack = navigateBack,
    ) {
        Button(
            label = tr("Add game path"),
            description = tr("Add the root directory of your game(s). It will scan all directories in it for games"),
            onClick = dropUnlessResumed { goToGamePathsSettings() },
        )
        Button(
            label = tr("Import system files"),
            description = tr(
                "Install the console system files (mlc01, cafeLibs, otp.bin, seeprom.bin) required to boot the Wii U Menu and Mii Maker. Choose a .zip archive that contains them"
            ),
            onClick = dropUnlessResumed {
                systemArchiveLauncher.launch(
                    arrayOf("application/zip", "application/octet-stream")
                )
            },
        )
        SingleSelection(
            label = tr("Language"),
            choice = guiSettings.language,
            onChoiceChanged = { viewModel.setLanguage(language = it, context) },
            choiceToString = { viewModel.languageToDisplayNameMap[it] ?: it },
            choices = viewModel.languages,
        )
        SingleSelection(
            label = tr("Console language"),
            initialChoice = NativeSettings::getConsoleLanguage,
            onChoiceChanged = NativeSettings::setConsoleLanguage,
            choiceToString = { consoleLanguageToString(it) },
            choices = listOf(
                NativeSettings.ConsoleLanguage.JAPANESE,
                NativeSettings.ConsoleLanguage.ENGLISH,
                NativeSettings.ConsoleLanguage.FRENCH,
                NativeSettings.ConsoleLanguage.GERMAN,
                NativeSettings.ConsoleLanguage.ITALIAN,
                NativeSettings.ConsoleLanguage.SPANISH,
                NativeSettings.ConsoleLanguage.CHINESE,
                NativeSettings.ConsoleLanguage.KOREAN,
                NativeSettings.ConsoleLanguage.DUTCH,
                NativeSettings.ConsoleLanguage.PORTUGUESE,
                NativeSettings.ConsoleLanguage.RUSSIAN,
                NativeSettings.ConsoleLanguage.TAIWANESE,
            ),
        )

        SingleSelection(
            label = tr("GamePad position"),
            choice = emulationSettings.gamePadPosition,
            onChoiceChanged = { viewModel.setGamePadPosition(it) },
            choiceToString = { gamePadPositionToString(it) },
            choices = GamePadPosition.entries,
        )

        SingleSelection(
            label = tr("CPU mode"),
            initialChoice = NativeSettings::getCpuMode,
            onChoiceChanged = NativeSettings::setCpuMode,
            choiceToString = { cpuModeToString(it) },
            choices = listOf(
                NativeSettings.CpuMode.AUTO,
                NativeSettings.CpuMode.MULTICORE_RECOMPILER,
                NativeSettings.CpuMode.SINGLE_CORE_RECOMPILER,
            ),
        )
    }
}

private fun cpuModeToString(mode: Int): String = when (mode) {
    NativeSettings.CpuMode.AUTO -> tr("Automatic")
    NativeSettings.CpuMode.MULTICORE_RECOMPILER -> tr("Multi-core (3 threads, faster)")
    NativeSettings.CpuMode.SINGLE_CORE_RECOMPILER -> tr("Single-core (1 thread, cooler)")
    else -> throw IllegalArgumentException("Invalid CPU mode: $mode")
}

private fun gamePadPositionToString(position: GamePadPosition) = when (position) {
    GamePadPosition.ABOVE -> tr("Above")
    GamePadPosition.BELOW -> tr("Below")
    GamePadPosition.LEFT -> tr("Left")
    GamePadPosition.RIGHT -> tr("Right")
}

private fun consoleLanguageToString(channels: Int): String = when (channels) {
    NativeSettings.ConsoleLanguage.JAPANESE -> tr("Japanese")
    NativeSettings.ConsoleLanguage.ENGLISH -> tr("English")
    NativeSettings.ConsoleLanguage.FRENCH -> tr("French")
    NativeSettings.ConsoleLanguage.GERMAN -> tr("German")
    NativeSettings.ConsoleLanguage.ITALIAN -> tr("Italian")
    NativeSettings.ConsoleLanguage.SPANISH -> tr("Spanish")
    NativeSettings.ConsoleLanguage.CHINESE -> tr("Chinese")
    NativeSettings.ConsoleLanguage.KOREAN -> tr("Korean")
    NativeSettings.ConsoleLanguage.DUTCH -> tr("Dutch")
    NativeSettings.ConsoleLanguage.PORTUGUESE -> tr("Portuguese")
    NativeSettings.ConsoleLanguage.RUSSIAN -> tr("Russian")
    NativeSettings.ConsoleLanguage.TAIWANESE -> tr("Taiwanese")
    else -> throw IllegalArgumentException("Invalid console language: $channels")
}