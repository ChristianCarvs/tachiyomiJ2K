package eu.kanade.tachiyomi.ui.catalogue

import android.view.View
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.source.online.LoginSource
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.base.holder.SlicedHolder
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.roundTextIcon
import eu.kanade.tachiyomi.util.view.visible
import io.github.mthli.slice.Slice
import kotlinx.android.synthetic.main.catalogue_main_controller_card_item.*

class SourceHolder(view: View, override val adapter: CatalogueAdapter) :
        BaseFlexibleViewHolder(view, adapter),
        SlicedHolder {

    override val slice = Slice(card).apply {
        setColor(adapter.cardBackground)
    }

    override val viewToSlice: View
        get() = card

    init {
        source_browse.setOnClickListener {
            adapter.browseClickListener.onBrowseClick(adapterPosition)
        }

        source_latest.setOnClickListener {
            adapter.latestClickListener.onLatestClick(adapterPosition)
        }
    }

    fun bind(item: SourceItem) {
        val source = item.source
        setCardEdges(item)

        // Set source name
        title.text = source.name

        // Set circle letter image.
        itemView.post {
            val icon = source.icon()
            if (icon != null) edit_button.setImageDrawable(source.icon())
            else edit_button.roundTextIcon(source.name)
        }

        // If source is login, show only login option
        if (source is LoginSource && !source.isLogged()) {
            source_browse.setText(R.string.login)
            source_latest.gone()
        } else {
            source_browse.setText(R.string.browse)
            if (source.supportsLatest) {
                source_latest.visible()
            } else {
                source_latest.gone()
            }
        }
    }
}
