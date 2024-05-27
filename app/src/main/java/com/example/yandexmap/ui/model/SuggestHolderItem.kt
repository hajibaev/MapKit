package com.example.yandexmap.ui.model

import com.yandex.mapkit.SpannableString

data class SuggestHolderItem(
    val title: SpannableString,
    val subtitle: SpannableString?,
    val onClick: () -> Unit,
)