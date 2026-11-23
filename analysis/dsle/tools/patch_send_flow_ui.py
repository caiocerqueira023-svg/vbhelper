from pathlib import Path

ROOT = Path(r"C:\Users\julye\IdeaProjects\vbhelper")
path = ROOT / "app/src/main/java/com/tamerslab/transfer/ui/TransferGameLibraryScreen.kt"
text = path.read_text(encoding="utf-8")

# 1) Add the two dialog states next to the send page state declarations.
old_state = """    val groups = sources.orEmpty().groupBy { TransferGameCatalog.identify(it.observation?.gameId, it.source.displayName) }
    val games = TransferGame.entries.filter { TransferGameCapabilities.readsNdsStory(it) || groups[it].orEmpty().isNotEmpty() }"""
new_state = """    val groups = sources.orEmpty().groupBy { TransferGameCatalog.identify(it.observation?.gameId, it.source.displayName) }
    val games = TransferGame.entries.filter { TransferGameCapabilities.readsNdsStory(it) || groups[it].orEmpty().isNotEmpty() }
    // Two plain-language steps: the game must be closed for the write, then reopened to read it.
    var readyToSend by remember { mutableStateOf<Pair<LinkedSaveView, String?>?>(null) }
    var sentToGame by remember { mutableStateOf(false) }"""
if old_state not in text:
    raise SystemExit("send page state anchor missing")
text = text.replace(old_state, new_state, 1)

# 2) Destination tap asks for the readiness confirmation instead of sending immediately.
old_click = """                    TextButton(enabled = blocker == null && !sending, onClick = {
                        choosing = false
                        sending = true
                        val id = individualId
                        val revision = view.observation?.contentRevision
                        scope.launch {
                            try {
                                sends.send(requireNotNull(id), view.source.sourceId, requireNotNull(revision))
                                context.showAppFeedback(R.string.transfer_send_completed)
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: WorldDsSendRecoveryRequired) { context.showAppFeedback(R.string.transfer_move_recovery) }
                            catch (e: ImportSourceChangedException) { Log.e("WorldDsSend", "send source changed", e); context.showAppFeedback(R.string.transfer_import_source_changed) }
                            catch (e: Exception) { Log.e("WorldDsSend", "send failed", e); context.showAppFeedback(R.string.transfer_action_failed) }
                            finally { sending = false; epoch++ }
                        }
                    }) {"""
new_click = """                    TextButton(enabled = blocker == null && !sending, onClick = {
                        choosing = false
                        readyToSend = view to view.observation?.contentRevision
                    }) {"""
if old_click not in text:
    raise SystemExit("destination click anchor missing")
text = text.replace(old_click, new_click, 1)

# 3) Confirmation dialog + post-send instructions, appended before the sendBlockerText helper.
anchor = "private fun sendBlockerText(blocker: WorldDsSendBlocker): Int = when (blocker) {"
dialogs = """    readyToSend?.let { (view, revision) ->
        val id = individualId
        AlertDialog(onDismissRequest = { if (!sending) readyToSend = null },
            title = { Text(stringResource(R.string.transfer_send_close_game_title)) },
            text = { Text(stringResource(R.string.transfer_send_close_game_body)) },
            confirmButton = { TextButton(enabled = id != null && revision != null && !sending, onClick = {
                readyToSend = null
                sending = true
                scope.launch {
                    try {
                        sends.send(requireNotNull(id), view.source.sourceId, requireNotNull(revision))
                        sentToGame = true
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: WorldDsSendRecoveryRequired) { context.showAppFeedback(R.string.transfer_move_recovery) }
                    catch (e: ImportSourceChangedException) { Log.e("WorldDsSend", "send source changed", e); context.showAppFeedback(R.string.transfer_import_source_changed) }
                    catch (e: Exception) { Log.e("WorldDsSend", "send failed", e); context.showAppFeedback(R.string.transfer_action_failed) }
                    finally { sending = false; epoch++ }
                }
            }) { Text(stringResource(R.string.transfer_send_confirm)) } },
            dismissButton = { TextButton(enabled = !sending, onClick = { readyToSend = null }) { Text(stringResource(R.string.transfer_close)) } })
    }
    if (sentToGame) {
        AlertDialog(onDismissRequest = { sentToGame = false },
            title = { Text(stringResource(R.string.transfer_send_open_game_title)) },
            text = { Text(stringResource(R.string.transfer_send_open_game_body)) },
            confirmButton = { TextButton(onClick = { sentToGame = false; onOpenGame(TransferGame.LOST_EVOLUTION) }) {
                Text(stringResource(R.string.transfer_send_ok)) } })
    }
}

private fun sendBlockerText(blocker: WorldDsSendBlocker): Int = when (blocker) {"""
if anchor not in text:
    raise SystemExit("dialog anchor missing")
# The helper block sits after the send-page composable closes; replace the final '}' + helper.
old_tail = """    }
}

""" + anchor
new_tail = dialogs
if old_tail not in text:
    raise SystemExit("send page tail anchor missing")
text = text.replace(old_tail, new_tail, 1)
path.write_text(text, encoding="utf-8", newline="\n")
print("send flow dialogs added")
