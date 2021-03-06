package eu.kanade.tachiyomi.ui.manga

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.IconCompat
import androidx.core.math.MathUtils
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.ChangeImageTransform
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.reddit.indicatorfastscroll.FastScrollerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.catalogue.CatalogueController
import eu.kanade.tachiyomi.ui.library.ChangeMangaCategoriesDialog
import eu.kanade.tachiyomi.ui.library.LibraryController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.SearchActivity
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterHolder
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
import eu.kanade.tachiyomi.ui.manga.chapter.ChaptersSortBottomSheet
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.ui.manga.track.TrackingBottomSheet
import eu.kanade.tachiyomi.ui.migration.manga.design.PreMigrationController
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.ThemeUtil
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.pxToDp
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsets
import eu.kanade.tachiyomi.util.view.getText
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import eu.kanade.tachiyomi.util.view.setStyle
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.updateLayoutParams
import eu.kanade.tachiyomi.util.view.updatePaddingRelative
import jp.wasabeef.glide.transformations.CropSquareTransformation
import jp.wasabeef.glide.transformations.MaskTransformation
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.android.synthetic.main.manga_details_controller.*
import kotlinx.android.synthetic.main.manga_header_item.*
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class MangaDetailsController : BaseController,
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    ActionMode.Callback,
    MangaDetailsAdapter.MangaDetailsInterface,
    FlexibleAdapter.OnItemMoveListener,
    ChangeMangaCategoriesDialog.Listener {

    constructor(
        manga: Manga?,
        fromCatalogue: Boolean = false,
        smartSearchConfig: CatalogueController.SmartSearchConfig? = null,
        update: Boolean = false
    ) : super(Bundle().apply {
        putLong(MANGA_EXTRA, manga?.id ?: 0)
        putBoolean(FROM_CATALOGUE_EXTRA, fromCatalogue)
        putParcelable(SMART_SEARCH_CONFIG_EXTRA, smartSearchConfig)
        putBoolean(UPDATE_EXTRA, update)
    }) {
        this.manga = manga
        if (manga != null) {
            source = Injekt.get<SourceManager>().getOrStub(manga.source)
        }
        presenter = MangaDetailsPresenter(this, manga!!, source!!)
    }

    constructor(mangaId: Long) : this(
        Injekt.get<DatabaseHelper>().getManga(mangaId).executeAsBlocking())

    constructor(bundle: Bundle) : this(bundle.getLong(MANGA_EXTRA)) {
        val notificationId = bundle.getInt("notificationId", -1)
        val context = applicationContext ?: return
        if (notificationId > -1) NotificationReceiver.dismissNotification(
            context, notificationId, bundle.getInt("groupId", 0)
        )
    }

    private var manga: Manga? = null
    private var source: Source? = null
    var colorAnimator: ValueAnimator? = null
    val presenter: MangaDetailsPresenter
    var coverColor: Int? = null
    var toolbarIsColored = false
    private var snack: Snackbar? = null
    val fromCatalogue = args.getBoolean(FROM_CATALOGUE_EXTRA, false)
    var coverDrawable: Drawable? = null
    private var trackingBottomSheet: TrackingBottomSheet? = null
    private var startingDLChapterPos: Int? = null
    private var editMangaDialog: EditMangaDialog? = null
    var refreshTracker: Int? = null
    private var textAnim: ViewPropertyAnimator? = null
    private var scrollAnim: ViewPropertyAnimator? = null
    var isTablet = false
    var chapterPopupMenu: Pair<Int, PopupMenu>? = null

    // Tablet Layout
    var tabletRecycler: RecyclerView? = null

    /**
     * Adapter containing a list of chapters.
     */
    private var tabletAdapter: MangaDetailsAdapter? = null

    /**
     * Library search query.
     */
    private var query = ""

    /**
     * Adapter containing a list of chapters.
     */
    private var adapter: MangaDetailsAdapter? = null

    /**
     * Action mode for selections.
     */
    private var actionMode: ActionMode? = null

    // Hold a reference to the current animator, so that it can be canceled mid-way.
    private var currentAnimator: Animator? = null

    var showScroll = false
    var headerHeight = 0

    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle(): String? {
        return if (toolbarIsColored && !isTablet) manga?.title else null
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        coverColor = null

        // Init RecyclerView and adapter
        adapter = MangaDetailsAdapter(this, view.context)
        isTablet = isTabletSize()
        if (isTablet) {
            tabletRecycler = RecyclerView(view.context)
            linear_recycler_layout.addView(tabletRecycler, 0)
            tabletRecycler?.updateLayoutParams<LinearLayout.LayoutParams> {
                weight = 0.4f
                height = ViewGroup.LayoutParams.MATCH_PARENT
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
            tabletRecycler?.clipToPadding = false
            tabletAdapter = MangaDetailsAdapter(this, view.context)
            tabletRecycler?.adapter = tabletAdapter
            tabletRecycler?.layoutManager = LinearLayoutManager(view.context)
            val divider = View(view.context)
            divider.setBackgroundColor(ContextCompat.getColor(view.context, R.color.divider))
            linear_recycler_layout.addView(divider, 1)
            divider.updateLayoutParams<LinearLayout.LayoutParams> {
                height = ViewGroup.LayoutParams.MATCH_PARENT
                width = 1.dpToPx
            }
        }

        recycler.adapter = adapter
        adapter?.isSwipeEnabled = true
        recycler.layoutManager = LinearLayoutManager(view.context)
        recycler.addItemDecoration(
            DividerItemDecoration(
                view.context,
                DividerItemDecoration.VERTICAL
            )
        )
        recycler.setHasFixedSize(true)
        val attrsArray = intArrayOf(android.R.attr.actionBarSize)
        val array = view.context.obtainStyledAttributes(attrsArray)
        val appbarHeight = array.getDimensionPixelSize(0, 0)
        array.recycle()
        val offset = 10.dpToPx
        var statusBarHeight = -1
        swipe_refresh.setStyle()
        swipe_refresh.setDistanceToTriggerSync(70.dpToPx)
        activity!!.appbar.elevation = 0f

        recycler.doOnApplyWindowInsets { v, insets, _ ->
            v.updatePaddingRelative(bottom = insets.systemWindowInsetBottom)
            tabletRecycler?.updatePaddingRelative(bottom = insets.systemWindowInsetBottom)
            headerHeight = appbarHeight + insets.systemWindowInsetTop
            statusBarHeight = insets.systemWindowInsetTop
            swipe_refresh.setProgressViewOffset(false, (-40).dpToPx, headerHeight + offset)
            if (isTablet) v.updatePaddingRelative(top = headerHeight + 1.dpToPx)
            getHeader()?.setTopHeight(headerHeight)
            fast_scroll_layout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = headerHeight
                bottomMargin = insets.systemWindowInsetBottom
            }
            v.updatePaddingRelative(bottom = insets.systemWindowInsetBottom)
        }

        presenter.onCreate()
        fast_scroller.translationX = if (showScroll || isTablet) 0f else 25f.dpToPx
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val atTop = !recycler.canScrollVertically(-1)
                if (!isTablet) {
                    val tY = getHeader()?.backdrop?.translationY ?: 0f
                    getHeader()?.backdrop?.translationY = max(0f, tY + dy * 0.25f)
                    if (router?.backstack?.lastOrNull()
                            ?.controller() == this@MangaDetailsController && statusBarHeight > -1 && activity != null && activity!!.appbar.height > 0
                    ) {
                        activity!!.appbar.y -= dy
                        activity!!.appbar.y = MathUtils.clamp(
                            activity!!.appbar.y, -activity!!.appbar.height.toFloat(), 0f
                        )
                    }
                    val appBarY = activity?.appbar?.y ?: 0f
                    if ((!atTop && !toolbarIsColored && (appBarY < (-headerHeight + 1) || (dy < 0 && appBarY == 0f))) || (atTop && toolbarIsColored)) {
                        colorToolbar(!atTop)
                    }
                } else {
                    if ((!atTop && !toolbarIsColored) || (atTop && toolbarIsColored)) {
                        colorToolbar(!atTop)
                    }
                }
                if (atTop) {
                    getHeader()?.backdrop?.translationY = 0f
                    activity!!.appbar.y = 0f
                }
                if (!isTablet) {
                    val fPosition =
                        (recycler.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
                    if (fPosition > 0 && !showScroll) {
                        showScroll = true
                        scrollAnim?.cancel()
                        scrollAnim = fast_scroller.animate().setDuration(100).translationX(0f)
                        scrollAnim?.start()
                    } else if (fPosition <= 0 && showScroll) {
                        showScroll = false
                        scrollAnim?.cancel()
                        scrollAnim =
                            fast_scroller.animate().setDuration(100).translationX(25f.dpToPx)
                        scrollAnim?.start()
                    }
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                val atTop = !recycler.canScrollVertically(-1)
                if (newState == RecyclerView.SCROLL_STATE_IDLE && !isTablet) {
                    if (router?.backstack?.lastOrNull()
                            ?.controller() == this@MangaDetailsController && statusBarHeight > -1 && activity != null &&
                        activity!!.appbar.height > 0
                    ) {
                        val halfWay = abs((-activity!!.appbar.height.toFloat()) / 2)
                        val shortAnimationDuration = resources?.getInteger(
                            android.R.integer.config_shortAnimTime
                        ) ?: 0
                        val closerToTop = abs(activity!!.appbar.y) - halfWay > 0
                        activity!!.appbar.animate().y(
                            if (closerToTop && !atTop) (-activity!!.appbar.height.toFloat())
                            else 0f
                        ).setDuration(shortAnimationDuration.toLong()).start()
                        if (!closerToTop && !atTop && !toolbarIsColored)
                            colorToolbar(true)
                    }
                }
                if (atTop && toolbarIsColored) colorToolbar(false)
                if (atTop) {
                    getHeader()?.backdrop?.translationY = 0f
                    activity!!.appbar.y = 0f
                }
            }
        })
        setPaletteColor()

        fast_scroller.setupWithRecyclerView(recycler, { position ->
            val letter = adapter?.getSectionText(position)
            when {
                presenter.scrollType == 0 -> null
                letter != null -> FastScrollItemIndicator.Text(letter)
                else -> FastScrollItemIndicator.Icon(R.drawable.ic_star_24dp)
            }
        })
        fast_scroller.useDefaultScroller = false
        fast_scroller.itemIndicatorSelectedCallbacks += object :
            FastScrollerView.ItemIndicatorSelectedCallback {
            override fun onItemIndicatorSelected(
                indicator: FastScrollItemIndicator,
                indicatorCenterY: Int,
                itemPosition: Int
            ) {
                textAnim?.cancel()
                textAnim = text_view_m.animate().alpha(0f).setDuration(250L).setStartDelay(1000)
                textAnim?.start()

                text_view_m.translationY = indicatorCenterY.toFloat() - text_view_m.height / 2
                text_view_m.alpha = 1f
                text_view_m.text = adapter?.getFullText(itemPosition)
                val appbar = activity?.appbar
                appbar?.y = 0f
                (recycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                    itemPosition, headerHeight
                )
                colorToolbar(itemPosition > 0, false)
            }
        }

        swipe_refresh.isRefreshing = presenter.isLoading
        if (manga?.initialized != true)
            swipe_refresh.post { swipe_refresh.isRefreshing = true }

        swipe_refresh.setOnRefreshListener { presenter.refreshAll() }
    }

    fun colorToolbar(isColor: Boolean, animate: Boolean = true) {
        if (isColor == toolbarIsColored) return
        toolbarIsColored = isColor
        val isCurrentController =
            router?.backstack?.lastOrNull()?.controller() == this@MangaDetailsController
        if (isCurrentController) setTitle()
        if (actionMode != null) {
            (activity as MainActivity).toolbar.setBackgroundColor(Color.TRANSPARENT)
            return
        }
        val color =
            coverColor ?: activity!!.getResourceColor(R.attr.colorPrimaryVariant)
        val colorFrom =
            if (colorAnimator?.isRunning == true) activity?.window?.statusBarColor
                ?: color
            else ColorUtils.setAlphaComponent(
                color, if (toolbarIsColored) 0 else 175
            )
        val colorTo = ColorUtils.setAlphaComponent(
            color, if (toolbarIsColored) 175 else 0
        )
        colorAnimator?.cancel()
        if (animate) {
        colorAnimator = ValueAnimator.ofObject(
            android.animation.ArgbEvaluator(), colorFrom, colorTo
        )
        colorAnimator?.duration = 250 // milliseconds
        colorAnimator?.addUpdateListener { animator ->
            (activity as MainActivity).toolbar.setBackgroundColor(animator.animatedValue as Int)
            activity?.window?.statusBarColor = (animator.animatedValue as Int)
        }
        colorAnimator?.start()
        } else {
            (activity as MainActivity).toolbar.setBackgroundColor(colorTo)
            activity?.window?.statusBarColor = colorTo
        }
    }

    fun setPaletteColor() {
        val view = view ?: return
        GlideApp.with(view.context).load(manga)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .signature(ObjectKey(MangaImpl.getLastCoverFetch(manga!!.id!!).toString()))
            .into(object : CustomTarget<Drawable>() {
                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable>?
                ) {
                    coverDrawable = resource
                    val bitmapCover = resource as? BitmapDrawable ?: return
                    Palette.from(bitmapCover.bitmap).generate {
                        if (recycler == null) return@generate
                        val currentNightMode =
                            recycler.resources!!.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                        val colorBack = view.context.getResourceColor(
                            android.R.attr.colorBackground
                        )
                        val backDropColor =
                            (if (currentNightMode == Configuration.UI_MODE_NIGHT_NO) it?.getLightVibrantColor(
                                colorBack
                            )
                            else it?.getDarkVibrantColor(colorBack)) ?: colorBack
                        coverColor = backDropColor
                        getHeader()?.setBackDrop(backDropColor)
                        if (toolbarIsColored) {
                            val translucentColor = ColorUtils.setAlphaComponent(backDropColor, 175)
                            (activity as MainActivity).toolbar.setBackgroundColor(translucentColor)
                            activity?.window?.statusBarColor = translucentColor
                        }
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) { }
            })
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        presenter.isLockedFromSearch = SecureActivityDelegate.shouldBeLocked()
        presenter.headerItem.isLocked = presenter.isLockedFromSearch
        presenter.fetchChapters(refreshTracker == null)
        if (refreshTracker != null) {
            trackingBottomSheet?.refreshItem(refreshTracker ?: 0)
            presenter.refreshTrackers()
            refreshTracker = null
        }
        val isCurrentController = router?.backstack?.lastOrNull()?.controller() ==
            this
        if (isCurrentController) {
            setStatusBarAndToolbar()
        }
    }

    fun showError(message: String) {
        swipe_refresh?.isRefreshing = presenter.isLoading
        view?.snack(message)
    }

    fun updateChapterDownload(download: Download) {
        getHolder(download.chapter)?.notifyStatus(download.status, presenter.isLockedFromSearch,
            download.progress)
    }

    private fun isTabletSize(): Boolean {
        val activity = activity ?: return false
        if ((activity.resources.configuration.screenLayout and Configuration
                .SCREENLAYOUT_SIZE_MASK) < Configuration.SCREENLAYOUT_SIZE_LARGE)
            return false
        val displayMetrics = DisplayMetrics()
        activity.windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        return displayMetrics.widthPixels.pxToDp >= 720
    }

    fun hasTabletHeight(): Boolean {
        val activity = activity ?: return false
        if ((activity.resources.configuration.screenLayout and Configuration
                .SCREENLAYOUT_SIZE_MASK) < Configuration.SCREENLAYOUT_SIZE_LARGE) return false
        val displayMetrics = DisplayMetrics()
        activity.windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        return displayMetrics.heightPixels.pxToDp >= 720
    }

    private fun getHolder(chapter: Chapter): ChapterHolder? {
        return recycler?.findViewHolderForItemId(chapter.id!!) as? ChapterHolder
    }

    private fun getHeader(): MangaHeaderHolder? {
        return if (isTablet) tabletRecycler?.findViewHolderForAdapterPosition(0) as? MangaHeaderHolder
        else recycler.findViewHolderForAdapterPosition(0) as? MangaHeaderHolder
    }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        if (type == ControllerChangeType.PUSH_ENTER || type == ControllerChangeType.POP_ENTER) {
            setActionBar(true)
            setStatusBarAndToolbar()
        } else if (type == ControllerChangeType.PUSH_EXIT || type == ControllerChangeType.POP_EXIT) {
            if (router.backstack.lastOrNull()?.controller() is DialogController)
                return
            if (type == ControllerChangeType.POP_EXIT) {
                setActionBar(false)
                presenter.cancelScope()
            }
            colorAnimator?.cancel()

            val colorSecondary = activity?.getResourceColor(
                R.attr.colorSecondary
            ) ?: Color.BLACK
            (activity as MainActivity).appbar.setBackgroundColor(colorSecondary)
            (activity as MainActivity).toolbar.setBackgroundColor(colorSecondary)

            activity?.window?.statusBarColor = activity?.getResourceColor(android.R.attr
                .statusBarColor) ?: colorSecondary
        }
    }

    private fun setActionBar(forThis: Boolean) {
        val currentNightMode =
            activity!!.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        // if the theme is using inverted toolbar color
        if (currentNightMode == Configuration.UI_MODE_NIGHT_NO && ThemeUtil.isBlueTheme(
                presenter.preferences.theme()
            )
        ) {
            if (forThis)
            (activity as MainActivity).appbar.context.setTheme(R.style
                .ThemeOverlay_AppCompat_DayNight_ActionBar)
            else
                (activity as MainActivity).appbar.context.setTheme(R.style
                    .Theme_ActionBar_Dark_DayNight)

            val iconPrimary = view?.context?.getResourceColor(
                if (forThis) android.R.attr.textColorPrimary
                else R.attr.actionBarTintColor
            ) ?: Color.BLACK
            (activity as MainActivity).toolbar.setTitleTextColor(iconPrimary)
            (activity as MainActivity).drawerArrow?.color = iconPrimary
            (activity as MainActivity).toolbar.overflowIcon?.setTint(iconPrimary)
            if (forThis) activity!!.main_content.systemUiVisibility =
                activity!!.main_content.systemUiVisibility.or(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                )
            else activity!!.main_content.systemUiVisibility =
                activity!!.main_content.systemUiVisibility.rem(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                )
        }
    }

    fun setRefresh(enabled: Boolean) {
        swipe_refresh.isRefreshing = enabled
    }

    fun updateHeader() {
        swipe_refresh?.isRefreshing = presenter.isLoading
        adapter?.setChapters(presenter.chapters)
        addMangaHeader()
        activity?.invalidateOptionsMenu()
    }

    fun updateChapters(chapters: List<ChapterItem>) {
        swipe_refresh?.isRefreshing = presenter.isLoading
        if (presenter.chapters.isEmpty() && fromCatalogue && !presenter.hasRequested) {
            launchUI { swipe_refresh?.isRefreshing = true }
            presenter.fetchChaptersFromSource()
        }
        adapter?.setChapters(chapters)
        addMangaHeader()
        activity?.invalidateOptionsMenu()
    }

    fun refreshAdapter() = adapter?.notifyDataSetChanged()

    override fun onItemClick(view: View?, position: Int): Boolean {
        val chapter = (adapter?.getItem(position) as? ChapterItem)?.chapter ?: return false
        if (actionMode != null) {
            if (startingDLChapterPos == null) {
                adapter?.addSelection(position)
                (recycler.findViewHolderForAdapterPosition(position) as? BaseFlexibleViewHolder)
                    ?.toggleActivation()
                (recycler.findViewHolderForAdapterPosition(position) as? ChapterHolder)
                    ?.notifyStatus(Download.CHECKED, false, 0)
                startingDLChapterPos = position
                actionMode?.invalidate()
            } else {
                val startingPosition = startingDLChapterPos ?: return false
                var chapterList = listOf<ChapterItem>()
                when {
                    startingPosition > position ->
                        chapterList = presenter.chapters.subList(position - 1, startingPosition)
                    startingPosition <= position ->
                        chapterList = presenter.chapters.subList(startingPosition - 1, position)
                }
                downloadChapters(chapterList)
                adapter?.removeSelection(startingPosition)
                (recycler.findViewHolderForAdapterPosition(startingPosition) as? BaseFlexibleViewHolder)
                    ?.toggleActivation()
                startingDLChapterPos = null
                destroyActionModeIfNeeded()
            }
            return false
        }
        openChapter(chapter)
        return false
    }

    override fun onItemLongClick(position: Int) {
        val adapter = adapter ?: return
        val item = (adapter.getItem(position) as? ChapterItem) ?: return
        val itemView = getHolder(item)?.itemView ?: return
        val popup = PopupMenu(itemView.context, itemView)
        chapterPopupMenu = position to popup

        // Inflate our menu resource into the PopupMenu's Menu
        popup.menuInflater.inflate(R.menu.chapter_single, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            val chapters = listOf(item)
            when (menuItem.itemId) {
                R.id.action_mark_previous_as_read -> markPreviousAsRead(item)
            }
            chapterPopupMenu = null
            true
        }

        // Finally show the PopupMenu
        popup.show()
    }

    fun dismissPopup(position: Int) {
        if (chapterPopupMenu != null && chapterPopupMenu?.first == position) {
            chapterPopupMenu?.second?.dismiss()
            chapterPopupMenu = null
        }
    }

    private fun markPreviousAsRead(chapter: ChapterItem) {
        val adapter = adapter ?: return
        val chapters = if (presenter.sortDescending()) adapter.items.reversed() else adapter.items
        val chapterPos = chapters.indexOf(chapter)
        if (chapterPos != -1) {
            markAsRead(chapters.take(chapterPos))
        }
    }

    fun bookmarkChapter(position: Int) {
        val item = adapter?.getItem(position) as? ChapterItem ?: return
        val chapter = item.chapter
        val bookmarked = item.bookmark
        bookmarkChapters(listOf(item), !bookmarked)
        snack?.dismiss()
        snack = view?.snack(
            if (bookmarked) R.string.removed_bookmark
            else R.string.bookmarked, Snackbar.LENGTH_INDEFINITE
        ) {
            setAction(R.string.undo) {
                bookmarkChapters(listOf(item), bookmarked)
            }
        }
        (activity as? MainActivity)?.setUndoSnackBar(snack)
    }

    fun toggleReadChapter(position: Int) {
        val item = adapter?.getItem(position) as? ChapterItem ?: return
        val chapter = item.chapter
        val lastRead = chapter.last_page_read
        val pagesLeft = chapter.pages_left
        val read = item.chapter.read
        presenter.markChaptersRead(listOf(item), !read, false)
        snack?.dismiss()
        snack = view?.snack(
            if (read) R.string.marked_as_unread
            else R.string.marked_as_read, Snackbar.LENGTH_INDEFINITE
        ) {
            var undoing = false
            setAction(R.string.undo) {
                presenter.markChaptersRead(listOf(item), read, true, lastRead, pagesLeft)
                undoing = true
            }
            addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    super.onDismissed(transientBottomBar, event)
                    if (!undoing && !read && presenter.preferences.removeAfterMarkedAsRead()) {
                        presenter.deleteChapters(listOf(item))
                    }
                }
            })
        }
        (activity as? MainActivity)?.setUndoSnackBar(snack)
    }

    private fun bookmarkChapters(chapters: List<ChapterItem>, bookmarked: Boolean) {
        presenter.bookmarkChapters(chapters, bookmarked)
    }

    private fun markAsRead(chapters: List<ChapterItem>) {
        presenter.markChaptersRead(chapters, true)
    }

    private fun markAsUnread(chapters: List<ChapterItem>) {
        presenter.markChaptersRead(chapters, false)
    }

    private fun openChapter(chapter: Chapter) {
        val activity = activity ?: return
        val intent = ReaderActivity.newIntent(activity, manga!!, chapter)
        startActivity(intent)
    }

    override fun onDestroyView(view: View) {
        snack?.dismiss()
        presenter.onDestroy()
        adapter = null
        trackingBottomSheet = null
        super.onDestroyView(view)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.manga_details, menu)
        val editItem = menu.findItem(R.id.action_edit)
        editItem.isVisible = presenter.manga.favorite && !presenter.isLockedFromSearch
        editItem.title = view?.context?.getString(if (manga?.source == LocalSource.ID)
            R.string.edit else R.string.edit_cover)
        menu.findItem(R.id.action_download).isVisible = !presenter.isLockedFromSearch &&
            manga?.source != LocalSource.ID
        menu.findItem(R.id.action_add_to_home_screen).isVisible = !presenter.isLockedFromSearch
        menu.findItem(R.id.action_mark_all_as_read).isVisible =
            presenter.getNextUnreadChapter() != null && !presenter.isLockedFromSearch
        menu.findItem(R.id.action_mark_all_as_unread).isVisible =
            presenter.anyUnread() && !presenter.isLockedFromSearch
        menu.findItem(R.id.action_remove_downloads).isVisible =
            presenter.hasDownloads() && !presenter.isLockedFromSearch &&
                manga?.source != LocalSource.ID
        menu.findItem(R.id.remove_non_bookmarked).isVisible =
            presenter.hasBookmark() && !presenter.isLockedFromSearch
        menu.findItem(R.id.action_migrate).isVisible = !presenter.isLockedFromSearch &&
            manga?.source != LocalSource.ID && presenter.manga.favorite
        menu.findItem(R.id.action_migrate).title = view?.context?.getString(R.string.migrate_,
            presenter.manga.mangaType(view!!.context))
        val iconPrimary = view?.context?.getResourceColor(android.R.attr.textColorPrimary)
            ?: Color.BLACK
        menu.findItem(R.id.action_download).icon?.mutate()?.setTint(iconPrimary)
        editItem.icon?.mutate()?.setTint(iconPrimary)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = resources?.getString(R.string.search_chapters)
        searchItem.icon?.mutate()?.setTint(iconPrimary)
        searchItem.collapseActionView()
        if (query.isNotEmpty()) {
            searchItem.expandActionView()
            searchView.setQuery(query, true)
            searchView.clearFocus()
        }

        setOnQueryTextChangeListener(searchView) {
            query = it ?: ""
            if (!isTablet) {
                if (query.isNotEmpty()) getHeader()?.collapse()
                else getHeader()?.expand()
            }

            adapter?.setFilter(query)
            adapter?.performFilter()
            true
        }
        searchItem.fixExpand(onExpand = { invalidateMenuOnExpand() })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_edit -> {
                if (manga?.source == LocalSource.ID) {
                    editMangaDialog = EditMangaDialog(
                        this, presenter.manga
                    )
                    editMangaDialog?.showDialog(router)
                } else {
                    if (manga?.hasCustomCover() == true) {
                        MaterialDialog(activity!!).listItems(items = listOf(
                            view!!.context.getString(
                                R.string.edit_cover
                            ), view!!.context.getString(
                                R.string.reset_cover
                            )
                        ), waitForPositiveButton = false, selection = { _, index, _ ->
                            when (index) {
                                0 -> changeCover()
                                else -> presenter.clearCover()
                            }
                        }).show()
                    } else {
                        changeCover()
                    }
                }
            }
            R.id.action_open_in_web_view -> openInWebView()
            R.id.action_add_to_home_screen -> addToHomeScreen()
            R.id.action_refresh_tracking -> presenter.refreshTrackers()
            R.id.action_migrate ->
                PreMigrationController.navigateToMigration(
                    presenter.preferences.skipPreMigration().getOrDefault(),
                    router,
                    listOf(manga!!.id!!))
            R.id.action_mark_all_as_read -> {
                MaterialDialog(view!!.context).message(R.string.mark_all_chapters_as_read)
                    .positiveButton(R.string.mark_as_read) {
                        markAsRead(presenter.chapters)
                    }.negativeButton(android.R.string.cancel).show()
            }
            R.id.remove_all, R.id.remove_read, R.id.remove_non_bookmarked -> massDeleteChapters(item.itemId)
            R.id.action_mark_all_as_unread -> markAsUnread(presenter.chapters)
            R.id.download_next, R.id.download_next_5, R.id.download_custom, R.id.download_unread, R.id.download_all -> downloadChapters(
                item.itemId
            )
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /**
     * Called to run Intent with [Intent.ACTION_SEND], which show share dialog.
     */
    override fun prepareToShareManga() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && coverDrawable != null)
            GlideApp.with(activity!!).asBitmap().load(presenter.manga).into(object :
                CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    presenter.shareManga(resource)
                }
                override fun onLoadCleared(placeholder: Drawable?) {}

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    shareManga()
                }
            })
        else shareManga()
    }

    /**
     * Called to run Intent with [Intent.ACTION_SEND], which show share dialog.
     */
    fun shareManga(cover: File? = null) {
        val context = view?.context ?: return

        val source = presenter.source as? HttpSource ?: return
        val stream = cover?.getUriCompat(context)
        try {
            val url = source.mangaDetailsRequest(presenter.manga).url.toString()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/*"
                putExtra(Intent.EXTRA_TEXT, url)
                putExtra(Intent.EXTRA_TITLE, presenter.manga.title)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                if (stream != null) {
                    clipData = ClipData.newRawUri(null, stream)
                }
            }
            startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    override fun openInWebView() {
        val source = presenter.source as? HttpSource ?: return

        val url = try {
            source.mangaDetailsRequest(presenter.manga).url.toString()
        } catch (e: Exception) {
            return
        }

        val activity = activity ?: return
        val intent = WebViewActivity.newIntent(activity.applicationContext, source.id, url, presenter.manga
            .title)
        startActivity(intent)
    }

    private fun massDeleteChapters(choice: Int) {
        val chaptersToDelete = when (choice) {
            R.id.remove_all -> presenter.chapters
            R.id.remove_non_bookmarked -> presenter.chapters.filter { !it.bookmark }
            R.id.remove_read -> presenter.chapters.filter { it.read }
            else -> emptyList()
        }.filter { it.isDownloaded }
        if (chaptersToDelete.isNotEmpty()) {
            massDeleteChapters(chaptersToDelete)
        }
    }

    private fun massDeleteChapters(chapters: List<ChapterItem>) {
        val context = view?.context ?: return
        MaterialDialog(context).message(
            text = context.resources.getQuantityString(
                R.plurals.remove_n_chapters, chapters.size, chapters.size
            )
        ).positiveButton(R.string.remove) {
                presenter.deleteChapters(chapters)
            }.negativeButton(android.R.string.cancel).show()
    }

    private fun downloadChapters(choice: Int) {
        val chaptersToDownload = when (choice) {
            R.id.download_next -> presenter.getUnreadChaptersSorted().take(1)
            R.id.download_next_5 -> presenter.getUnreadChaptersSorted().take(5)
            R.id.download_custom -> {
                createActionModeIfNeeded()
                return
            }
            R.id.download_unread -> presenter.chapters.filter { !it.read }
            R.id.download_all -> presenter.chapters
            else -> emptyList()
        }
        if (chaptersToDownload.isNotEmpty()) {
            downloadChapters(chaptersToDownload)
        }
    }

    override fun startDownloadNow(position: Int) {
        val chapter = (adapter?.getItem(position) as? ChapterItem) ?: return
        presenter.startDownloadingNow(chapter)
    }

    private fun downloadChapters(chapters: List<ChapterItem>) {
        val view = view ?: return
        presenter.downloadChapters(chapters)
        val text = view.context.getString(R.string.add_x_to_library, presenter.manga.mangaType
            (view.context).toLowerCase(Locale.ROOT))
        if (!presenter.manga.favorite && (snack == null ||
                snack?.getText() != text)) {
            snack = view.snack(text, Snackbar.LENGTH_INDEFINITE) {
                setAction(R.string.add) {
                    presenter.setFavorite(true)
                }
                addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        super.onDismissed(transientBottomBar, event)
                        if (snack == transientBottomBar) snack = null
                    }
                })
            }
            (activity as? MainActivity)?.setUndoSnackBar(snack)
        }
    }

    /**
     * Add a shortcut of the manga to the home screen
     */
    private fun addToHomeScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // TODO are transformations really unsupported or is it just the Pixel Launcher?
            createShortcutForShape()
        } else {
            ChooseShapeDialog(this).showDialog(router)
        }
    }

    /**
     * Retrieves the bitmap of the shortcut with the requested shape and calls [createShortcut] when
     * the resource is available.
     *
     * @param i The shape index to apply. Defaults to circle crop transformation.
     */
    fun createShortcutForShape(i: Int = 0) {
        if (activity == null) return
        GlideApp.with(activity!!)
            .asBitmap()
            .load(presenter.manga)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .apply {
                when (i) {
                    0 -> circleCrop()
                    1 -> transform(RoundedCorners(5))
                    2 -> transform(CropSquareTransformation())
                    3 -> centerCrop().transform(MaskTransformation(R.drawable.mask_star))
                }
            }
            .into(object : CustomTarget<Bitmap>(128, 128) {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    createShortcut(resource)
                }

                override fun onLoadCleared(placeholder: Drawable?) { }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    activity?.toast(R.string.could_not_create_shortcut)
                }
            })
    }

    /**
     * Create shortcut using ShortcutManager.
     *
     * @param icon The image of the shortcut.
     */
    private fun createShortcut(icon: Bitmap) {
        val activity = activity ?: return

        // Create the shortcut intent.
        val shortcutIntent = activity.intent
            .setAction(MainActivity.SHORTCUT_MANGA)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MANGA_EXTRA, presenter.manga.id)

        // Check if shortcut placement is supported
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(activity)) {
            val shortcutId = "manga-shortcut-${presenter.manga.title}-${presenter.source.name}"

            // Create shortcut info
            val shortcutInfo = ShortcutInfoCompat.Builder(activity, shortcutId)
                .setShortLabel(presenter.manga.title)
                .setIcon(IconCompat.createWithBitmap(icon))
                .setIntent(shortcutIntent)
                .build()

            val successCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Create the CallbackIntent.
                val intent = ShortcutManagerCompat.createShortcutResultIntent(activity, shortcutInfo)

                // Configure the intent so that the broadcast receiver gets the callback successfully.
                PendingIntent.getBroadcast(activity, 0, intent, 0)
            } else {
                NotificationReceiver.shortcutCreatedBroadcast(activity)
            }

            // Request shortcut.
            ShortcutManagerCompat.requestPinShortcut(activity, shortcutInfo,
                successCallback.intentSender)
        }
    }

    override fun startDownloadRange(position: Int) {
        if (actionMode == null) createActionModeIfNeeded()
        onItemClick(null, position)
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.manga_details_controller, container, false)
    }

    override fun coverColor(): Int? = coverColor
    override fun topCoverHeight(): Int = headerHeight

    override fun readNextChapter() {
        if (activity is SearchActivity && presenter.isLockedFromSearch) {
            SecureActivityDelegate.promptLockIfNeeded(activity)
            return
        }
        val item = presenter.getNextUnreadChapter()
        if (item != null) {
                openChapter(item.chapter)
        } else if (snack == null || snack?.getText() != view?.context?.getString(
                R.string.next_chapter_not_found)) {
            snack = view?.snack(R.string.next_chapter_not_found, Snackbar.LENGTH_LONG) {
                addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        super.onDismissed(transientBottomBar, event)
                        if (snack == transientBottomBar) snack = null
                    }
                })
            }
        }
    }

    override fun downloadChapter(position: Int) {
        val view = view ?: return
        val chapter = (adapter?.getItem(position) as? ChapterItem) ?: return
        if (actionMode != null) {
            onItemClick(null, position)
            return
        }
        if (chapter.status != Download.NOT_DOWNLOADED && chapter.status != Download.ERROR) {
            presenter.deleteChapters(listOf(chapter))
        } else {
            if (chapter.status == Download.ERROR)
                DownloadService.start(view.context)
            else
                downloadChapters(listOf(chapter))
        }
    }

    override fun tagClicked(text: String) {
        val firstController = router.backstack.first()?.controller()
        if (firstController is LibraryController && router.backstack.size == 2) {
            router.handleBack()
            firstController.search(text)
        }
    }

    override fun showChapterFilter() {
        ChaptersSortBottomSheet(this).show()
    }

    private fun isLocked(): Boolean {
        if (presenter.isLockedFromSearch) {
            SecureActivityDelegate.promptLockIfNeeded(activity)
            return true
        }
        return false
    }

    override fun favoriteManga(longPress: Boolean) {
        if (isLocked()) return
        val manga = presenter.manga
        val categories = presenter.getCategories()
        if (longPress && categories.isNotEmpty()) {
            if (!manga.favorite) {
                presenter.toggleFavorite()
                showAddedSnack()
            }
            val ids = presenter.getMangaCategoryIds(manga)
            val preselected = ids.mapNotNull { id ->
                categories.indexOfFirst { it.id == id }.takeIf { it != -1 }
            }.toTypedArray()

            ChangeMangaCategoriesDialog(this, listOf(manga), categories, preselected).showDialog(
                    router
                )
        } else {
            if (!manga.favorite) {
                toggleMangaFavorite()
            } else {
                val headerHolder = getHeader() ?: return
                val popup = PopupMenu(view!!.context, headerHolder.favorite_button)
                popup.menu.add(R.string.remove_from_library)

                // Set a listener so we are notified if a menu item is clicked
                popup.setOnMenuItemClickListener {
                    toggleMangaFavorite()
                    true
                }
                popup.show()
            }
        }
    }

    private fun toggleMangaFavorite() {
        val manga = presenter.manga
        if (presenter.toggleFavorite()) {
            val categories = presenter.getCategories()
            val defaultCategoryId = presenter.preferences.defaultCategory()
            val defaultCategory = categories.find { it.id == defaultCategoryId }
            when {
                defaultCategory != null -> presenter.moveMangaToCategory(manga, defaultCategory)
                defaultCategoryId == 0 || categories.isEmpty() -> // 'Default' or no category
                    presenter.moveMangaToCategory(manga, null)
                else -> {
                    val ids = presenter.getMangaCategoryIds(manga)
                    val preselected = ids.mapNotNull { id ->
                        categories.indexOfFirst { it.id == id }.takeIf { it != -1 }
                    }.toTypedArray()

                    ChangeMangaCategoriesDialog(
                        this,
                        listOf(manga),
                        categories,
                        preselected
                    ).showDialog(router)
                }
            }
            showAddedSnack()
        } else {
            showRemovedSnack()
        }
    }

    private fun showAddedSnack() {
        val view = view ?: return
        snack?.dismiss()
        snack = view.snack(view.context.getString(R.string.added_to_library))
    }

    private fun showRemovedSnack() {
        val view = view ?: return
        snack?.dismiss()
        snack = view.snack(
            view.context.getString(R.string.removed_from_library),
            Snackbar.LENGTH_INDEFINITE
        ) {
            setAction(R.string.undo) {
                presenter.setFavorite(true)
            }
            addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    super.onDismissed(transientBottomBar, event)
                    if (!presenter.manga.favorite) presenter.confirmDeletion()
                }
            })
        }
        val favButton = getHeader()?.favorite_button
        (activity as? MainActivity)?.setUndoSnackBar(snack, favButton)
    }

    override fun mangaPresenter(): MangaDetailsPresenter = presenter

    override fun updateCategoriesForMangas(mangas: List<Manga>, categories: List<Category>) {
        val manga = mangas.firstOrNull() ?: return
        presenter.moveMangaToCategories(manga, categories)
    }

    /**
     * Copies a string to clipboard
     *
     * @param content the actual text to copy to the board
     * @param label Label to show to the user describing the content
     */
    override fun copyToClipboard(content: String, label: Int) {
        if (content.isBlank()) return

        val activity = activity ?: return
        val view = view ?: return

        val contentType = view.context.getString(label)
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(contentType, content))

        snack = view.snack(view.context.getString(R.string._copied_to_clipboard, contentType))
    }

    override fun handleBack(): Boolean {
        if (manga_cover_full?.visibility == View.VISIBLE) {
            manga_cover_full?.performClick()
            return true
        }
        return super.handleBack()
    }

    override fun showTrackingSheet() {
        if (isLocked()) return
        trackingBottomSheet =
            TrackingBottomSheet(this)
        trackingBottomSheet?.show()
    }

    fun refreshTracking(trackings: List<TrackItem>) {
        trackingBottomSheet?.onNextTrackings(trackings)
    }

    fun onTrackSearchResults(results: List<TrackSearch>) {
        trackingBottomSheet?.onSearchResults(results)
    }

    fun refreshTracker() {
        getHeader()?.updateTracking()
    }

    fun trackRefreshDone() {
        trackingBottomSheet?.onRefreshDone()
    }

    fun trackRefreshError(error: Exception) {
        Timber.e(error)
        trackingBottomSheet?.onRefreshError(error)
    }

    fun trackSearchError(error: Exception) {
        Timber.e(error)
        trackingBottomSheet?.onSearchResultsError(error)
    }

    /**
     * Creates the action mode if it's not created already.
     */
    private fun createActionModeIfNeeded() {
        if (actionMode == null) {
            actionMode = (activity as AppCompatActivity).startSupportActionMode(this)
            (activity as MainActivity).toolbar.setBackgroundColor(Color.TRANSPARENT)
            val view = activity?.window?.currentFocus ?: return
            val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            if (adapter?.mode != SelectableAdapter.Mode.MULTI) {
                adapter?.mode = SelectableAdapter.Mode.MULTI
            }
        }
    }

    /**
     * Destroys the action mode.
     */
    private fun destroyActionModeIfNeeded() {
        actionMode?.finish()
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        return true
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        actionMode = null
        setStatusBarAndToolbar()
        if (startingDLChapterPos != null) {
            val item = adapter?.getItem(startingDLChapterPos!!) as? ChapterItem
            (recycler.findViewHolderForAdapterPosition(startingDLChapterPos!!) as? ChapterHolder)?.notifyStatus(
                item?.status ?: Download.NOT_DOWNLOADED, false, 0
            )
        }
        startingDLChapterPos = null
        adapter?.mode = SelectableAdapter.Mode.IDLE
        adapter?.clearSelection()
        return
    }

    /**
     * Called to set the last used catalogue at the top of the view.
     */
    private fun addMangaHeader() {
        if (tabletAdapter?.scrollableHeaders?.isEmpty() == true) {
            tabletAdapter?.removeAllScrollableHeaders()
            tabletAdapter?.addScrollableHeader(presenter.headerItem)
            adapter?.removeAllScrollableHeaders()
            adapter?.addScrollableHeader(presenter.tabletChapterHeaderItem!!)
        } else if (!isTablet && adapter?.scrollableHeaders?.isEmpty() == true) {
            adapter?.removeAllScrollableHeaders()
            adapter?.addScrollableHeader(presenter.headerItem)
        }
    }

    private fun setStatusBarAndToolbar() {
        activity?.window?.statusBarColor = if (toolbarIsColored) {
            val translucentColor = ColorUtils.setAlphaComponent(coverColor ?: Color.TRANSPARENT, 175)
            (activity as MainActivity).toolbar.setBackgroundColor(translucentColor)
            translucentColor
        } else Color.TRANSPARENT
            (activity as MainActivity).appbar.setBackgroundColor(Color.TRANSPARENT)
            (activity as MainActivity).toolbar.setBackgroundColor(activity?.window?.statusBarColor
                ?: Color.TRANSPARENT)
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        mode?.title = view?.context?.getString(if (startingDLChapterPos == null)
            R.string.select_starting_chapter else R.string.select_ending_chapter)
        return false
    }

    fun changeCover() {
        if (manga?.favorite == true) {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            startActivityForResult(
                Intent.createChooser(intent,
                    resources?.getString(R.string.select_cover_image)),
                101
            )
        } else {
            activity?.toast(R.string.must_be_in_library_to_edit)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 101) {
            if (data == null || resultCode != Activity.RESULT_OK) return
            val activity = activity ?: return
            try {
                val uri = data.data ?: return
                if (editMangaDialog != null) editMangaDialog?.updateCover(uri)
                else {
                    presenter.editCoverWithStream(uri)
                    setPaletteColor()
                }
            } catch (error: IOException) {
                activity.toast(R.string.failed_to_update_cover)
                Timber.e(error)
            }
        }
    }

    override fun onActionStateChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        swipe_refresh.isEnabled = actionState != ItemTouchHelper.ACTION_STATE_SWIPE
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
    }

    override fun shouldMoveItem(fromPosition: Int, toPosition: Int): Boolean {
        return true
    }

    override fun zoomImageFromThumb(thumbView: View) {
        // If there's an animation in progress, cancel it immediately and proceed with this one.
        currentAnimator?.cancel()

        // Load the high-resolution "zoomed-in" image.
        val expandedImageView = manga_cover_full ?: return
        val fullBackdrop = full_backdrop
        val image = coverDrawable ?: return
        expandedImageView.setImageDrawable(image)

        // Hide the thumbnail and show the zoomed-in view. When the animation
        // begins, it will position the zoomed-in view in the place of the
        // thumbnail.
        thumbView.alpha = 0f
        expandedImageView.visibility = View.VISIBLE
        fullBackdrop.visibility = View.VISIBLE

        // Set the pivot point to 0 to match thumbnail

        swipe_refresh.isEnabled = false

        val rect = Rect()
        thumbView.getGlobalVisibleRect(rect)
        expandedImageView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            height = thumbView.height
            width = thumbView.width
            topMargin = rect.top
            leftMargin = rect.left
            rightMargin = rect.right
            bottomMargin = rect.bottom
        }
        expandedImageView.requestLayout()

        expandedImageView.post {
            val defMargin = 16.dpToPx
            expandedImageView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                height = ViewGroup.LayoutParams.MATCH_PARENT
                width = ViewGroup.LayoutParams.MATCH_PARENT
                topMargin = defMargin + headerHeight
                leftMargin = defMargin
                rightMargin = defMargin
                bottomMargin = defMargin + recycler.paddingBottom
            }
            val shortAnimationDuration = resources?.getInteger(
                android.R.integer.config_shortAnimTime
            ) ?: 0

            // TransitionSet for the full cover because using animation for this SUCKS
            val transitionSet = TransitionSet()
            val bound = ChangeBounds()
            transitionSet.addTransition(bound)
            val changeImageTransform = ChangeImageTransform()
            transitionSet.addTransition(changeImageTransform)
            transitionSet.duration = shortAnimationDuration.toLong()
            TransitionManager.beginDelayedTransition(frame_layout, transitionSet)

            // AnimationSet for backdrop because idk how to use TransitionSet
            currentAnimator = AnimatorSet().apply {
                play(
                    ObjectAnimator.ofFloat(fullBackdrop, View.ALPHA, 0f, 0.5f)
                )
                duration = shortAnimationDuration.toLong()
                interpolator = DecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {

                    override fun onAnimationEnd(animation: Animator) {
                        TransitionManager.endTransitions(frame_layout)
                        currentAnimator = null
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        TransitionManager.endTransitions(frame_layout)
                        currentAnimator = null
                    }
                })
                start()
            }

            expandedImageView.setOnClickListener {
                currentAnimator?.cancel()

                val rect2 = Rect()
                thumbView.getGlobalVisibleRect(rect2)
                expandedImageView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    height = thumbView.height
                    width = thumbView.width
                    topMargin = rect2.top
                    leftMargin = rect2.left
                    rightMargin = rect2.right
                    bottomMargin = rect2.bottom
                }

                // Zoom out back to tc thumbnail
                val transitionSet2 = TransitionSet()
                val bound2 = ChangeBounds()
                transitionSet2.addTransition(bound2)
                val changeImageTransform2 = ChangeImageTransform()
                transitionSet2.addTransition(changeImageTransform2)
                transitionSet2.duration = shortAnimationDuration.toLong()
                TransitionManager.beginDelayedTransition(frame_layout, transitionSet2)

                // Animation to remove backdrop and hide the full cover
                currentAnimator = AnimatorSet().apply {
                    play(ObjectAnimator.ofFloat(fullBackdrop, View.ALPHA, 0f))
                    duration = shortAnimationDuration.toLong()
                    interpolator = DecelerateInterpolator()
                    addListener(object : AnimatorListenerAdapter() {

                        override fun onAnimationEnd(animation: Animator) {
                            thumbView.alpha = 1f
                            expandedImageView.visibility = View.GONE
                            fullBackdrop.visibility = View.GONE
                            swipe_refresh.isEnabled = true
                            currentAnimator = null
                        }

                        override fun onAnimationCancel(animation: Animator) {
                            thumbView.alpha = 1f
                            expandedImageView.visibility = View.GONE
                            fullBackdrop.visibility = View.GONE
                            swipe_refresh.isEnabled = true
                            currentAnimator = null
                        }
                    })
                    start()
                }
            }
        }
    }

    companion object {
        const val UPDATE_EXTRA = "update"
        const val SMART_SEARCH_CONFIG_EXTRA = "smartSearchConfig"

        const val FROM_CATALOGUE_EXTRA = "from_catalogue"
        const val MANGA_EXTRA = "manga"
    }
}
