package com.example.yandexmap.ui.adapter


import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.yandexmap.databinding.ItemSuggestBinding
import com.example.yandexmap.ui.model.SuggestHolderItem

class SuggestsListAdapter :
    ListAdapter<SuggestHolderItem, SuggestHolder>(SuggestHolderDiffCallBack()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestHolder {
        return SuggestHolder(
            ItemSuggestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: SuggestHolder, position: Int) {
        holder.bind(getItem(position))
    }
}


class SuggestHolder(
    private val binding: ItemSuggestBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: SuggestHolderItem) = with(binding) {
        textTitle.text = item.title.text
        textSubtitle.text = item.subtitle?.text
        binding.root.setOnClickListener { item.onClick() }
    }
}

class SuggestHolderDiffCallBack : DiffUtil.ItemCallback<SuggestHolderItem>() {

    override fun areItemsTheSame(
        oldItem: SuggestHolderItem,
        newItem: SuggestHolderItem
    ): Boolean {
        return oldItem.hashCode() == newItem.hashCode()
    }

    override fun areContentsTheSame(
        oldItem: SuggestHolderItem,
        newItem: SuggestHolderItem
    ): Boolean {
        return oldItem == newItem
    }
}

