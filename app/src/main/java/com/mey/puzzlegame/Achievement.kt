package com.mey.puzzlegame

import androidx.annotation.StringRes

enum class Achievement(
    val id: String,
    @StringRes val titleResId: Int,
    @StringRes val descriptionResId: Int
) {
    FIRST_PUZZLE(
        id = "first_puzzle",
        titleResId = R.string.achievement_first_puzzle_title,
        descriptionResId = R.string.achievement_first_puzzle_desc
    ),
    SPEED_DEMON(
        id = "speed_demon",
        titleResId = R.string.achievement_speed_demon_title,
        descriptionResId = R.string.achievement_speed_demon_desc
    ),
    GRANDMASTER(
        id = "grandmaster",
        titleResId = R.string.achievement_grandmaster_title,
        descriptionResId = R.string.achievement_grandmaster_desc
    ),
    COLLECTOR(
        id = "collector",
        titleResId = R.string.achievement_collector_title,
        descriptionResId = R.string.achievement_collector_desc
    );

    companion object {
        fun fromId(id: String): Achievement? = entries.find { it.id == id }
    }
}
