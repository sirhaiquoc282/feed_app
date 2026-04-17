package com.danghung.nhungapp


import android.media.MediaPlayer

object MediaManager {

    private var songPlayer: MediaPlayer? = null

    fun playSong(song: Int, event: MediaPlayer.OnCompletionListener?) {
        playSong(song, false, event)
    }

    fun playSong(song: Int, isLooping: Boolean, event: MediaPlayer.OnCompletionListener?) {
        songPlayer?.reset()
        songPlayer = play(song, isLooping)
        songPlayer?.setOnCompletionListener(event)
    }

    fun playSong() {
        play(songPlayer)
    }

    fun stopSong() {
        songPlayer = stop(songPlayer)
    }

    fun pauseSong() {
        pause(songPlayer)
    }

    private fun stop(player: MediaPlayer?): MediaPlayer? {
        player?.reset()
        return null
    }

    private fun play(song: Int, isLooping: Boolean): MediaPlayer {
        val player = MediaPlayer.create(App.instance, song)
        player.isLooping = isLooping
        player.start()
        return player
    }

    private fun play(player: MediaPlayer?) {
        if (player != null && !player.isPlaying) {
            player.start()
        }
    }

    private fun pause(player: MediaPlayer?) {
        if (player != null && player.isPlaying) {
            player.pause()
        }
    }
}