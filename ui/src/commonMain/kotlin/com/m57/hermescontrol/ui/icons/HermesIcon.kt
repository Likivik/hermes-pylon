package com.m57.hermescontrol.ui.icons

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Every symbol the app can draw, as a closed set.
 *
 * Screens used to pass snake_case strings (`MaterialSymbol(name = "send")`), so a
 * typo produced a silently blank icon: the font lookup simply returns nothing.
 * Enumerating the names moves that failure to compile time and gives completion.
 *
 * The entries are the names this codebase uses, taken from
 * `composeResources/files/material_symbols.codepoints`. Server-driven names still
 * arrive as strings at runtime; resolve those with [fromSymbol].
 */
enum class HermesIcon(val symbol: String) {
    AccountBalanceWallet("account_balance_wallet"),
    AccountCircle("account_circle"),
    AccountTree("account_tree"),
    Add("add"),
    Apps("apps"),
    ArrowBack("arrow_back"),
    ArrowForward("arrow_forward"),
    ArrowUpward("arrow_upward"),
    AttachFile("attach_file"),
    AutoStories("auto_stories"),
    Backup("backup"),
    BarChart("bar_chart"),
    Bolt("bolt"),
    Build("build"),
    CallSplit("call_split"),
    Chat("chat"),
    ChatBubble("chat_bubble"),
    ChatBubbleOutline("chat_bubble_outline"),
    Check("check"),
    CheckCircle("check_circle"),
    Checklist("checklist"),
    ChevronRight("chevron_right"),
    Clarify("clarify"),
    Clear("clear"),
    Close("close"),
    Cloud("cloud"),
    CloudDownload("cloud_download"),
    CloudOff("cloud_off"),
    Code("code"),
    Computer("computer"),
    ContentCopy("content_copy"),
    CreateNewFolder("create_new_folder"),
    Dashboard("dashboard"),
    Delete("delete"),
    DeleteForever("delete_forever"),
    DeleteSweep("delete_sweep"),
    Description("description"),
    Download("download"),
    Edit("edit"),
    EmojiEvents("emoji_events"),
    Extension("extension"),
    FileUpload("file_upload"),
    Folder("folder"),
    FormatListBulleted("format_list_bulleted"),
    Forum("forum"),
    Groups("groups"),
    HealthAndSafety("health_and_safety"),
    Help("help"),
    History("history"),
    HistoryEdu("history_edu"),
    Image("image"),
    Info("info"),
    InsertDriveFile("insert_drive_file"),
    InstallDesktop("install_desktop"),
    Keep("keep"),
    Key("key"),
    Keyboard("keyboard"),
    KeyboardArrowDown("keyboard_arrow_down"),
    KeyboardArrowRight("keyboard_arrow_right"),
    KeyboardArrowUp("keyboard_arrow_up"),
    KeyboardDoubleArrowDown("keyboard_double_arrow_down"),
    Language("language"),
    Link("link"),
    ListAlt("list_alt"),
    Lock("lock"),
    Memory("memory"),
    Menu("menu"),
    MenuBook("menu_book"),
    MoreVert("more_vert"),
    Movie("movie"),
    OpenInBrowser("open_in_browser"),
    Palette("palette"),
    Pause("pause"),
    Photo("photo"),
    PlayArrow("play_arrow"),
    Psychology("psychology"),
    Public("public"),
    PushPin("push_pin"),
    QrCode("qr_code"),
    Refresh("refresh"),
    RestartAlt("restart_alt"),
    Save("save"),
    Schedule("schedule"),
    Science("science"),
    Search("search"),
    SelectAll("select_all"),
    Send("send"),
    Settings("settings"),
    Share("share"),
    SmartToy("smart_toy"),
    Speed("speed"),
    Star("star"),
    Stop("stop"),
    Storage("storage"),
    Terminal("terminal"),
    TextToSpeech("text_to_speech"),
    ThumbUp("thumb_up"),
    TouchApp("touch_app"),
    Tune("tune"),
    Update("update"),
    Upload("upload"),
    VerifiedUser("verified_user"),
    Visibility("visibility"),
    VisibilityOff("visibility_off"),
    VolumeUp("volume_up"),
    Warning("warning"),
    Webhook("webhook"),
    Work("work"), ;

    companion object {
        private val bySymbol: Map<String, HermesIcon> = entries.associateBy { it.symbol }

        /** Null when the font has no glyph for this name; callers pick a fallback. */
        fun fromSymbol(name: String?): HermesIcon? = name?.let(bySymbol::get)
    }
}

/** Draws [icon]; [MaterialSymbol] remains for names only known at runtime. */
@Composable
fun HermesSymbol(
    icon: HermesIcon,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    filled: Boolean = true,
    contentDescription: String? = null,
) {
    MaterialSymbol(
        name = icon.symbol,
        modifier = modifier,
        size = size,
        tint = tint,
        filled = filled,
        contentDescription = contentDescription,
    )
}
