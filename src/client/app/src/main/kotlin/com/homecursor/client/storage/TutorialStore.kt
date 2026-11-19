package com.homecursor.client.storage

/** Remembers whether the guided tour of the trackpad has been shown, so it runs by itself only once. */
interface TutorialStore {
    fun isSeen(): Boolean

    fun markSeen()
}
