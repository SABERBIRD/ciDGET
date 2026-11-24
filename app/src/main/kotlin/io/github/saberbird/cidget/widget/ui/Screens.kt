package io.github.saberbird.cidget.widget.ui

import android.content.Context
import io.github.saberbird.cidget.R
import io.github.saberbird.cidget.core.AppSettings
import io.github.saberbird.cidget.core.ColumnId
import io.github.saberbird.cidget.core.SortKey
import io.github.saberbird.cidget.core.Theme
import io.github.saberbird.cidget.core.expiryWarningDays
import io.github.saberbird.cidget.core.ConfirmKind
import io.github.saberbird.cidget.core.DisplaySettings
import io.github.saberbird.cidget.core.GlyphStyle
import io.github.saberbird.cidget.core.Status
import io.github.saberbird.cidget.core.TinySource
import io.github.saberbird.cidget.core.TinyTap
import io.github.saberbird.cidget.core.WidgetConfig
import io.github.saberbird.cidget.core.WidgetView
import io.github.saberbird.cidget.core.cellsFor
import io.github.saberbird.cidget.core.displayWidth
import io.github.saberbird.cidget.core.fitColumns
import io.github.saberbird.cidget.core.formatAge
import io.github.saberbird.cidget.core.renderRow
import io.github.saberbird.cidget.core.rowUrl
import io.github.saberbird.cidget.core.selectBranches
import io.github.saberbird.cidget.core.shareText
import io.github.saberbird.cidget.data.RepoList
import io.github.saberbird.cidget.data.RepoSnapshot
import io.github.saberbird.cidget.data.TokenState
import io.github.saberbird.cidget.data.TokenSummary
import io.github.saberbird.cidget.widget.Glyphs
import io.github.saberbird.cidget.widget.expiryText
import io.github.saberbird.cidget.widget.metaLine
import io.github.saberbird.cidget.widget.widgetText
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val TOKEN_HELP_URL = "https://github.com/SABERBIRD/ciDGET/blob/main/docs/user/TOKEN.md"
private const val REPO_URL = "https://github.com/SABERBIRD/ciDGET"

/** Everything a screen is built from. [ctx] already uses the widget's language; [cpl] is the line length. */
internal class UiEnv(
    val ctx: Context,
    val appWidgetId: Int,
    val config: WidgetConfig,
    val tokens: List<TokenSummary>,
    val tokenState: TokenState?,
    val repoList: RepoList,
    val snapshot: RepoSnapshot?,
    val now: Instant,
    val glyphs: Glyphs,
    val cpl: Int,
    /** App-wide settings (refresh, expiry warning). */
    val app: AppSettings = AppSettings(),
) {
    val display: DisplaySettings get() = config.display

    /** Expiry of the token this widget uses (the one [TokenStore.resolveId] picks), if known. */
    val tokenExpiresAt: Instant?
        get() = (tokens.firstOrNull { it.id == config.tokenId } ?: tokens.firstOrNull { it.state == TokenState.OK } ?: tokens.firstOrNull())?.expiresAt
    fun str(res: Int): String = ctx.getString(res)
    fun str(res: Int, vararg args: Any): String = ctx.getString(res, *args)
}

private fun go(v: WidgetView) = UiAction.Do(Cmd.VIEW, v.name)

private fun columnName(e: UiEnv, id: ColumnId): String = e.str(
    when (id) {
        ColumnId.STATUS -> R.string.col_status
        ColumnId.BRANCH -> R.string.col_branch
        ColumnId.AGE -> R.string.col_age
        ColumnId.RUN_NUMBER -> R.string.col_run_number
        ColumnId.COPY -> R.string.col_copy
        ColumnId.SHARE -> R.string.col_share
        ColumnId.WORKFLOW -> R.string.col_workflow
        ColumnId.SHA -> R.string.col_sha
        ColumnId.DURATION -> R.string.col_duration
        ColumnId.TITLE -> R.string.col_title
        ColumnId.ACTOR -> R.string.col_actor
        ColumnId.EVENT -> R.string.col_event
    },
)

/** The column the order and priority screens act on: the stored selection if still enabled, else the first. */
internal fun selectedColumn(config: WidgetConfig): ColumnId? {
    val enabled = config.display.columns.filter { it.enabled }
    return enabled.firstOrNull { it.id == config.selected }?.id ?: enabled.firstOrNull()?.id
}

/** Builds the screen of [UiEnv.config]'s view. */
internal fun buildScreen(e: UiEnv): UiScreen = when (e.config.view) {
    WidgetView.BRANCHES -> branchesScreen(e)
    WidgetView.REPOS -> reposScreen(e)
    WidgetView.SETTINGS -> settingsScreen(e)
    WidgetView.SHOWN -> shownScreen(e)
    WidgetView.ORDER -> orderScreen(e)
    WidgetView.WIDTH -> widthScreen(e)
    WidgetView.PRIORITY -> priorityScreen(e)
    WidgetView.DISPLAY -> displayScreen(e)
    WidgetView.PINNED -> pinnedScreen(e)
    WidgetView.TINY -> tinyScreen(e)
    WidgetView.FILTERS -> filtersScreen(e)
    WidgetView.REFRESH -> refreshScreen(e)
}

private fun statusRole(s: Status): UiRole = when (s) {
    Status.RED -> UiRole.RED
    Status.YELLOW -> UiRole.YELLOW
    Status.GREEN -> UiRole.GREEN
    Status.GREY -> UiRole.GREY
}

/**
 * The foot of the branch and repo lists: the prompt on the left, the buttons on the right. The age or error text
 * goes into the same (lowest) line when it fits between them, else into a line of its own above.
 */
private fun footWithMeta(prompt: UiSpan, buttons: List<UiSpan>, meta: String, cpl: Int): List<UiLine> {
    if (meta.isEmpty()) return listOf(UiLine(listOf(prompt), buttons))
    val used = displayWidth(prompt.text) + 1 + displayWidth(meta) + 1 + buttons.sumOf { displayWidth(it.text) }
    return if (used <= cpl) {
        listOf(UiLine(listOf(prompt), listOf(span(meta, UiRole.DIM), span(" ")) + buttons))
    } else {
        listOf(line(meta, UiRole.DIM), UiLine(listOf(prompt), buttons))
    }
}

private fun branchesScreen(e: UiEnv): UiScreen {
    val repo = e.config.repo
    val tokenPresent = e.tokenState != null
    val tokenInvalid = e.tokenState == TokenState.INVALID
    val name = repo?.substringAfter('/') ?: ""
    val prompt = span("~/$name $ ci", UiRole.PROMPT, if (tokenPresent && !tokenInvalid) go(WidgetView.REPOS) else go(WidgetView.SETTINGS))
    val refresh = if (repo != null && tokenPresent) {
        listOf(span(if (e.snapshot?.refreshing == true) "[...]" else "[${e.glyphs.refresh}]", UiRole.DIM, UiAction.Do(Cmd.REFRESH)))
    } else {
        emptyList()
    }
    val meta = metaLine(e.ctx, e.snapshot, tokenPresent, tokenInvalid, e.now, e.app, e.tokenExpiresAt)
    val foot = footWithMeta(prompt, refresh, meta, e.cpl)
    val snapshot = e.snapshot
    val body = when {
        repo == null -> listOf(line(e.str(R.string.w_pick_repo), UiRole.DIM, go(WidgetView.REPOS)))
        !tokenPresent -> listOf(line(e.str(R.string.w_set_token), UiRole.DIM, go(WidgetView.SETTINGS)))
        snapshot == null || (snapshot.fetchedAt == null && snapshot.branches.isEmpty()) ->
            listOf(line(e.str(if (snapshot?.error == null) R.string.w_loading else R.string.w_no_data), UiRole.DIM))
        else -> branchRows(e, repo, snapshot)
    }
    return UiScreen(body, foot)
}

private fun branchRows(e: UiEnv, repo: String, snapshot: RepoSnapshot): List<UiLine> {
    val display = e.display
    val rows = selectBranches(snapshot.branches, display.viewOptions(), e.now).rows
    if (rows.isEmpty()) return listOf(line(e.str(R.string.w_no_runs), UiRole.DIM))
    val cellGlyphs = e.glyphs.cellGlyphs(display.glyphStyle)
    val cells = rows.map { cellsFor(it, e.now, cellGlyphs) }
    val fitted = fitColumns(display.columns, cells, e.cpl, fillLine = true)
    if (fitted.isEmpty()) return emptyList()
    return rows.mapIndexed { i, b ->
        val url = rowUrl(repo, b)
        val rendered = renderRow(fitted, cells[i])
        val spans = fitted.mapIndexed { n, col ->
            // One fixed cell per column, with the separator inside the cell (except after the last one).
            val width = col.width + if (n < fitted.lastIndex) 1 else 0
            val text = rendered[n] + if (n < fitted.lastIndex) " " else ""
            when (col.id) {
                ColumnId.STATUS -> UiSpan(text, statusRole(b.status), cells = width)
                ColumnId.BRANCH -> UiSpan(text, UiRole.FG, cells = width)
                ColumnId.COPY -> UiSpan(text, UiRole.DIM, UiAction.Copy(b.branch, url), cells = width)
                ColumnId.SHARE -> UiSpan(text, UiRole.DIM, UiAction.Share(b.branch, shareText(repo, b)), cells = width)
                else -> UiSpan(text, UiRole.DIM, cells = width)
            }
        }
        UiLine(spans, action = UiAction.Open(url))
    }
}

private fun reposScreen(e: UiEnv): UiScreen {
    val list = e.repoList
    val meta = buildList {
        list.fetchedAt?.let { add(e.str(R.string.w_age_ago, formatAge(it, e.now))) }
        if (list.refreshing) add("[...]")
        list.error?.let { add(it.widgetText(e.ctx)) }
    }.joinToString(" · ")
    val foot = footWithMeta(
        span(e.str(R.string.w_ls_prompt), UiRole.PROMPT),
        listOf(span("[${e.glyphs.settings}]", UiRole.DIM, go(WidgetView.SETTINGS)), span(" "), span("[x]", UiRole.DIM, go(WidgetView.BRANCHES))),
        meta,
        e.cpl,
    )
    val body = when {
        e.tokenState == null -> listOf(line(e.str(R.string.w_set_token), UiRole.DIM, go(WidgetView.SETTINGS)))
        list.repos.isEmpty() ->
            listOf(line(e.str(if (list.refreshing || list.fetchedAt == null && list.error == null) R.string.w_loading else R.string.w_no_repos), UiRole.DIM))
        else -> list.repos.map { r ->
            val current = r.fullName == e.config.repo
            val role = when {
                current -> UiRole.PROMPT
                r.archived -> UiRole.DIM
                else -> UiRole.FG
            }
            line((if (current) "> " else "  ") + r.fullName, role, UiAction.Do(Cmd.PICK, r.fullName, r.tokenId))
        }
    }
    return UiScreen(body, foot)
}

private fun expiryText(e: UiEnv, t: TokenSummary): String {
    val exp = t.expiresAt ?: return e.str(R.string.token_no_expiry)
    val locale = Locale.forLanguageTag(e.display.language.tag)
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(ZoneId.systemDefault()).format(exp.toJavaInstant())
    return e.str(R.string.token_expires, date)
}

private fun settingsScreen(e: UiEnv): UiScreen {
    val body = mutableListOf<UiLine>()
    body += section(e.str(R.string.ui_sec_tokens))
    if (e.tokens.isEmpty()) body += line(e.str(R.string.w_no_token), UiRole.DIM)
    val confirm = e.config.confirm
    e.tokens.forEach { t ->
        body += UiLine(
            listOf(span(e.str(R.string.ui_token_line, t.last4))),
            listOf(span(e.str(R.string.ui_btn_delete), UiRole.RED, UiAction.Do(Cmd.ASK, ConfirmKind.DELETE_TOKEN.name, t.id))),
        )
        if (confirm?.kind == ConfirmKind.DELETE_TOKEN && confirm.target == t.id) body += confirmLine(e, e.str(R.string.ui_ask_delete_token, t.last4))
        body += line(expiryText(e, t), UiRole.DIM)
        if (t.state == TokenState.INVALID) body += line(e.str(R.string.w_err_token), UiRole.RED)
        expiryWarningDays(t.expiresAt, e.now, e.app)?.let { body += line(expiryText(e.ctx, it), UiRole.YELLOW) }
    }
    // The last refresh error of this widget's repo, so it can be read without leaving the settings.
    e.snapshot?.error?.let { body += line(e.str(R.string.ui_last_error, it.widgetText(e.ctx)), UiRole.RED) }
    body += wrapItems(
        listOf(span(e.str(R.string.ui_btn_paste), UiRole.FG, UiAction.Paste), span(e.str(R.string.ui_btn_howto), UiRole.LINK, UiAction.Open(TOKEN_HELP_URL))),
        e.cpl,
    )
    body += rule()
    body += section(e.str(R.string.ui_sec_columns))
    body += wrapItems(
        listOf(
            span(e.str(R.string.ui_btn_shown), UiRole.FG, go(WidgetView.SHOWN)),
            span(e.str(R.string.ui_btn_order), UiRole.FG, go(WidgetView.ORDER)),
            span(e.str(R.string.ui_btn_width), UiRole.FG, go(WidgetView.WIDTH)),
            span(e.str(R.string.ui_btn_priority), UiRole.FG, go(WidgetView.PRIORITY)),
        ),
        e.cpl,
    )
    body += section(e.str(R.string.ui_sec_list))
    body += wrapItems(
        listOf(
            span(e.str(R.string.ui_btn_filters), UiRole.FG, go(WidgetView.FILTERS)),
            span(e.str(R.string.ui_btn_pinned), UiRole.FG, go(WidgetView.PINNED)),
        ),
        e.cpl,
    )
    body += section(e.str(R.string.ui_sec_look))
    body += wrapItems(
        listOf(
            span(e.str(R.string.ui_btn_look), UiRole.FG, go(WidgetView.DISPLAY)),
            span(e.str(R.string.ui_btn_tiny), UiRole.FG, go(WidgetView.TINY)),
            span(e.str(R.string.ui_btn_lang), UiRole.FG, UiAction.Do(Cmd.LANG)),
        ),
        e.cpl,
    )
    body += section(e.str(R.string.ui_sec_refresh))
    body += line(e.str(R.string.ui_btn_refresh), UiRole.FG, go(WidgetView.REFRESH))
    body += rule()
    body += line(e.str(R.string.ui_btn_reset), UiRole.FG, UiAction.Do(Cmd.ASK, ConfirmKind.RESET.name))
    if (confirm?.kind == ConfirmKind.RESET) body += confirmLine(e, e.str(R.string.ui_ask_reset))
    body += line(e.str(R.string.ui_btn_repo), UiRole.LINK, UiAction.Open(REPO_URL))
    body += line(e.str(R.string.ui_btn_uninstall), UiRole.RED, UiAction.Uninstall)
    val back = if (e.config.repo == null) go(WidgetView.BRANCHES) else go(WidgetView.REPOS)
    return UiScreen(body, subFoot(e.str(R.string.ui_t_settings), back, go(WidgetView.BRANCHES), e.cpl))
}

/** `Delete token …1c05?` with `[Yes]` and `[No]`, shown under the item after the first tap. */
private fun confirmLine(e: UiEnv, question: String): List<UiLine> = wrapItems(
    listOf(
        span(question, UiRole.RED),
        span(e.str(R.string.ui_btn_yes), UiRole.RED, UiAction.Do(Cmd.CONFIRM)),
        span(e.str(R.string.ui_btn_no), UiRole.FG, UiAction.Do(Cmd.CANCEL)),
    ),
    e.cpl,
)

private fun subFootOf(e: UiEnv, titleRes: Int) =
    subFoot(e.str(titleRes), go(WidgetView.SETTINGS), go(WidgetView.BRANCHES), e.cpl)

private fun shownScreen(e: UiEnv): UiScreen {
    val body = mutableListOf(line(e.str(R.string.ui_h_shown), UiRole.DIM), rule())
    e.display.columns.forEach { c -> body += toggle(c.enabled, columnName(e, c.id), UiAction.Do(Cmd.COL_TOGGLE, c.id.name)) }
    return UiScreen(body, subFootOf(e, R.string.ui_t_columns))
}

private fun upDown(e: UiEnv, cmd: Cmd): List<UiLine> = wrapItems(
    listOf(span(e.str(R.string.ui_btn_up), UiRole.FG, UiAction.Do(cmd, "-1")), span(e.str(R.string.ui_btn_down), UiRole.FG, UiAction.Do(cmd, "1"))),
    e.cpl,
)

private fun orderScreen(e: UiEnv): UiScreen {
    val sel = selectedColumn(e.config)
    val body = mutableListOf(line(e.str(R.string.ui_h_select), UiRole.DIM))
    body += upDown(e, Cmd.COL_MOVE)
    body += rule()
    e.display.columns.filter { it.enabled }.forEachIndexed { i, c ->
        val on = c.id == sel
        body += line((if (on) "> " else "  ") + "${i + 1} " + columnName(e, c.id), if (on) UiRole.PROMPT else UiRole.FG, UiAction.Do(Cmd.COL_SELECT, c.id.name))
    }
    return UiScreen(body, subFootOf(e, R.string.ui_t_order))
}

private fun widthScreen(e: UiEnv): UiScreen {
    val body = mutableListOf(line(e.str(R.string.ui_h_width1), UiRole.DIM), line(e.str(R.string.ui_h_width2), UiRole.DIM), rule())
    e.display.columns.filter { it.enabled }.forEach { c ->
        val name = columnName(e, c.id)
        val cap = c.maxChars
        val controls = if (cap == null) {
            listOf(span(e.str(R.string.ui_auto), UiRole.FG, UiAction.Do(Cmd.COL_CAP, c.id.name, "1")))
        } else {
            listOf(
                span("[−]", UiRole.FG, UiAction.Do(Cmd.COL_CAP, c.id.name, "-1")),
                span(" "),
                span(cap.toString(), UiRole.FG, UiAction.Do(Cmd.COL_CAP, c.id.name, "0")),
                span(" "),
                span("[+]", UiRole.FG, UiAction.Do(Cmd.COL_CAP, c.id.name, "1")),
            )
        }
        val controlsWidth = controls.sumOf { displayWidth(it.text) }
        if (displayWidth(name) + 1 + controlsWidth <= e.cpl) {
            body += UiLine(listOf(span(name)), controls)
        } else {
            body += line(name)
            body += UiLine(emptyList(), controls)
        }
    }
    return UiScreen(body, subFootOf(e, R.string.ui_t_width))
}

private fun priorityScreen(e: UiEnv): UiScreen {
    val sel = selectedColumn(e.config)
    val body = mutableListOf(line(e.str(R.string.ui_h_select), UiRole.DIM))
    body += upDown(e, Cmd.PRIO_MOVE)
    body += rule()
    e.display.priorityRanking().forEachIndexed { i, c ->
        val on = c.id == sel
        body += line((if (on) "> " else "  ") + "${i + 1} " + columnName(e, c.id), if (on) UiRole.PROMPT else UiRole.FG, UiAction.Do(Cmd.COL_SELECT, c.id.name))
    }
    body += rule()
    body += line(e.str(R.string.ui_h_prio_note), UiRole.DIM)
    return UiScreen(body, subFootOf(e, R.string.ui_t_priority))
}

private fun displayScreen(e: UiEnv): UiScreen {
    val d = e.display
    val body = mutableListOf<UiLine>()
    body += wrapItems(
        listOf(
            span("[−]", UiRole.FG, UiAction.Do(Cmd.FONT, "-1")),
            span("[+]", UiRole.FG, UiAction.Do(Cmd.FONT, "1")),
            span(e.str(R.string.ui_font, d.fontSizeSp)),
        ),
        e.cpl,
    )
    body += rule()
    body += line(e.str(R.string.ui_theme), UiRole.DIM)
    body += radio(d.theme == Theme.DARK, e.str(R.string.ui_theme_dark), UiAction.Do(Cmd.THEME, Theme.DARK.name))
    body += radio(d.theme == Theme.LIGHT, e.str(R.string.ui_theme_light), UiAction.Do(Cmd.THEME, Theme.LIGHT.name))
    body += radio(d.theme == Theme.SYSTEM, e.str(R.string.ui_theme_system), UiAction.Do(Cmd.THEME, Theme.SYSTEM.name))
    body += rule()
    body += line(e.str(R.string.ui_sym), UiRole.DIM)
    body += radio(d.glyphStyle == GlyphStyle.UNICODE, "✓ ✗ ● ○", UiAction.Do(Cmd.GLYPH, GlyphStyle.UNICODE.name))
    body += radio(d.glyphStyle == GlyphStyle.BRACKET, "[O] [F] [R] [-]", UiAction.Do(Cmd.GLYPH, GlyphStyle.BRACKET.name))
    body += rule()
    listOf(
        Status.GREEN to R.string.ui_st_ok,
        Status.RED to R.string.ui_st_fail,
        Status.YELLOW to R.string.ui_st_run,
        Status.GREY to R.string.ui_st_none,
    ).forEach { (s, res) ->
        body += UiLine(listOf(span(e.glyphs.statusLabel(s, d.glyphStyle), statusRole(s)), span(" " + e.str(res), UiRole.DIM)))
    }
    return UiScreen(body, subFootOf(e, R.string.ui_t_look))
}

private fun pinnedScreen(e: UiEnv): UiScreen {
    val d = e.display
    val names = (d.pinned + e.snapshot?.branches.orEmpty().map { it.branch }).distinct()
    val body = mutableListOf(line(e.str(R.string.ui_h_pinned), UiRole.DIM), rule())
    names.forEach { n -> body += toggle(n in d.pinned, n, UiAction.Do(Cmd.PIN, n)) }
    return UiScreen(body, subFootOf(e, R.string.ui_t_pinned))
}

private fun tinyScreen(e: UiEnv): UiScreen {
    val d = e.display
    val body = mutableListOf<UiLine>()
    body += line(e.str(R.string.ui_shows), UiRole.DIM)
    body += radio(d.tinySource == TinySource.PINNED, e.str(R.string.ui_src_pinned), UiAction.Do(Cmd.TINY_SOURCE, TinySource.PINNED.name))
    body += radio(d.tinySource == TinySource.LAST, e.str(R.string.ui_src_last), UiAction.Do(Cmd.TINY_SOURCE, TinySource.LAST.name))
    body += radio(d.tinySource == TinySource.WORST, e.str(R.string.ui_src_worst), UiAction.Do(Cmd.TINY_SOURCE, TinySource.WORST.name))
    body += rule()
    body += line(e.str(R.string.ui_tap), UiRole.DIM)
    body += radio(d.tinyTap == TinyTap.RUN, e.str(R.string.ui_tap_run), UiAction.Do(Cmd.TINY_TAP, TinyTap.RUN.name))
    body += radio(d.tinyTap == TinyTap.REFRESH, e.str(R.string.ui_tap_refresh), UiAction.Do(Cmd.TINY_TAP, TinyTap.REFRESH.name))
    body += radio(d.tinyTap == TinyTap.COPY, e.str(R.string.ui_tap_copy), UiAction.Do(Cmd.TINY_TAP, TinyTap.COPY.name))
    body += rule()
    body += line(e.str(R.string.ui_h_tiny_note), UiRole.DIM)
    return UiScreen(body, subFootOf(e, R.string.ui_t_tiny))
}

/** `label          [−] value [+]`; the controls go below the label when the line is too short. */
private fun stepper(e: UiEnv, label: String, value: String, cmd: Cmd): List<UiLine> {
    val controls = listOf(
        span("[−]", UiRole.FG, UiAction.Do(cmd, "-1")),
        span(" $value ", UiRole.PROMPT),
        span("[+]", UiRole.FG, UiAction.Do(cmd, "1")),
    )
    val width = controls.sumOf { displayWidth(it.text) }
    return if (displayWidth(label) + 1 + width <= e.cpl) {
        listOf(UiLine(listOf(span(label)), controls))
    } else {
        listOf(line(label), UiLine(emptyList(), controls))
    }
}

private fun filtersScreen(e: UiEnv): UiScreen {
    val d = e.display
    val body = mutableListOf<UiLine>()
    body += line(e.str(R.string.ui_sort), UiRole.DIM)
    body += radio(d.sortKey == SortKey.RECENT, e.str(R.string.ui_sort_recent), UiAction.Do(Cmd.SORT, SortKey.RECENT.name))
    body += radio(d.sortKey == SortKey.STATUS, e.str(R.string.ui_sort_status), UiAction.Do(Cmd.SORT, SortKey.STATUS.name))
    body += radio(d.sortKey == SortKey.NAME, e.str(R.string.ui_sort_name), UiAction.Do(Cmd.SORT, SortKey.NAME.name))
    body += rule()
    body += stepper(e, e.str(R.string.ui_idle), d.hideIdleDays?.let { e.str(R.string.ui_days, it) } ?: e.str(R.string.ui_never), Cmd.IDLE_DAYS)
    body += stepper(e, e.str(R.string.ui_rows), d.maxRows?.toString() ?: e.str(R.string.ui_all), Cmd.MAX_ROWS)
    body += toggle(d.hideBranchesWithoutRuns, e.str(R.string.ui_hide_no_runs), UiAction.Do(Cmd.HIDE_NO_RUNS))
    return UiScreen(body, subFootOf(e, R.string.ui_t_filters))
}

private fun refreshScreen(e: UiEnv): UiScreen {
    val a = e.app
    val body = mutableListOf<UiLine>()
    body += stepper(e, e.str(R.string.ui_every), a.refreshMinutes?.let { e.str(R.string.ui_minutes, it) } ?: e.str(R.string.ui_off), Cmd.REFRESH_INTERVAL)
    body += toggle(a.unmeteredOnly, e.str(R.string.ui_wifi_only), UiAction.Do(Cmd.REFRESH_UNMETERED))
    body += toggle(a.batteryNotLow, e.str(R.string.ui_battery), UiAction.Do(Cmd.REFRESH_BATTERY))
    body += rule()
    body += stepper(e, e.str(R.string.ui_expiry_warn), e.str(R.string.ui_days, a.expiryWarnDays), Cmd.EXPIRY_WARN)
    body += rule()
    body += line(e.str(R.string.ui_h_refresh), UiRole.DIM)
    return UiScreen(body, subFootOf(e, R.string.ui_t_refresh))
}
