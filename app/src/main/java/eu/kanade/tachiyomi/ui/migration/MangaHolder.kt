package eu.kanade.tachiyomi.ui.migration

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.engine.DiskCacheStrategy
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import kotlinx.android.synthetic.main.catalogue_list_item.*

class MangaHolder(
    private val view: View,
    private val adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>
) : BaseFlexibleViewHolder(view, adapter) {

    fun bind(item: MangaItem) {
        // Update the title of the manga.
        title.text = item.manga.title
        subtitle.text = item.manga.author?.trim()

        // Update the cover.
        GlideApp.with(itemView.context).clear(cover_thumbnail)
        GlideApp.with(itemView.context)
                .load(item.manga)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .dontAnimate()
                .into(cover_thumbnail)
    }
}
