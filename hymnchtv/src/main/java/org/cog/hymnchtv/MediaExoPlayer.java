/*
 * hymnchtv: COG hymns' lyrics viewer and player client
 * Copyright 2020 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cog.hymnchtv;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.google.android.exoplayer2.*;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.util.MimeTypes;

import org.apache.http.util.TextUtils;
import org.cog.hymnchtv.persistance.FileBackend;

import java.util.ArrayList;
import java.util.List;

import at.huber.youtubeExtractor.VideoMeta;
import at.huber.youtubeExtractor.YouTubeExtractor;
import at.huber.youtubeExtractor.YtFile;
import timber.log.Timber;

// import java.lang.reflect.Field;

/**
 * The class handles the actual content source address decoding for the user selected hymn
 * see https://developer.android.com/codelabs/exoplayer-intro#0
 *
 * Orientation change is handled by exoPlayer itself for smooth audio/video playback
 * android:configChanges="keyboardHidden|orientation|screenSize"
 *
 * @author Eng Chong Meng
 */
public class MediaExoPlayer extends FragmentActivity
{
    // Tag for the instance state bundle.
    public static final String ATTR_MEDIA_URL = "mediaUrl";
    public static final String ATTR_MEDIA_URLS = "mediaUrls";

    private static final String sampleUrl = "https://www.learningcontainer.com/wp-content/uploads/2020/05/sample-mp4-file.mp4";

    // Start playback video url
    private String mediaUrl = sampleUrl;
    private ArrayList<String> mediaUrls = null;

    private SimpleExoPlayer mExoPlayer = null;
    private StyledPlayerView mPlayerView;
    private PlaybackStateListener playbackStateListener;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.media_player_exo_ui);
        mPlayerView = findViewById(R.id.exoplayerView);

        // Need to set text color in Hymnchtv; although ExoStyledControls.ButtonText specifies while
        TextView rewindButtonTextView = findViewById(com.google.android.exoplayer2.ui.R.id.exo_rew_with_amount);
        rewindButtonTextView.setTextColor(Color.WHITE);

        TextView fastForwardButtonTextView = findViewById(com.google.android.exoplayer2.ui.R.id.exo_ffwd_with_amount);
        fastForwardButtonTextView.setTextColor(Color.WHITE);

        Bundle bundle = getIntent().getExtras();
        mediaUrl = bundle.getString(ATTR_MEDIA_URL);
        mediaUrls = bundle.getStringArrayList(ATTR_MEDIA_URLS);
        playbackStateListener = new PlaybackStateListener();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        hideSystemUi();
        // Load the media each time onResume() is called.
        initializePlayer();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        releasePlayer();
    }

    private void initializePlayer()
    {
        if (mExoPlayer == null) {
            mExoPlayer = new SimpleExoPlayer.Builder(this).build();
            mExoPlayer.addListener(playbackStateListener);
            mPlayerView.setPlayer(mExoPlayer);
        }

        if ((mediaUrls == null) || mediaUrls.isEmpty()) {
            MediaItem mediaItem = buildMediaItem(mediaUrl);
            if (mediaItem != null)
                playMedia(mediaItem);
        }
        else {
            playVideoUrls();
        }
    }

    /**
     * Media play-back takes a lot of resources, so everything should be stopped and released at this time.
     * Release all media-related resources. In a more complicated app this
     * might involve unregistering listeners or releasing audio focus.
     */
    private void releasePlayer()
    {
        if (mExoPlayer != null) {
            // Timber.d("Media Player stopping: %s", mExoPlayer);
            mExoPlayer.setPlayWhenReady(false);
            mExoPlayer.removeListener(playbackStateListener);
            mExoPlayer.release();
            mExoPlayer = null;
        }
    }

    /**
     * Prepare and play the specified mediaItem
     *
     * @param mediaItem for playback
     */
    private void playMedia(MediaItem mediaItem)
    {
        if (mediaItem != null) {
            mExoPlayer.setMediaItem(mediaItem, 0);
            mExoPlayer.setPlayWhenReady(true);
            mExoPlayer.prepare();
        }
    }

    /**
     * Prepare and playback a list of given video URLs if not empty
     */
    private void playVideoUrls()
    {
        if ((mediaUrls != null) && !mediaUrls.isEmpty()) {
            List<MediaItem> mediaItems = new ArrayList<>();
            for (String tmpUrl : mediaUrls) {
                mediaItems.add(buildMediaItem(tmpUrl));
            }
            mExoPlayer.setMediaItems(mediaItems);
            mExoPlayer.setPlayWhenReady(true);
            mExoPlayer.prepare();
        }
    }

    /**
     * Build and return the mediaItem or
     * Proceed to play if it is a youtube link; return null;
     *
     * @param mediaUrl for building the mediaItem
     * @return built mediaItem
     */
    private MediaItem buildMediaItem(String mediaUrl)
    {
        MediaItem mediaItem = null;

        Uri uri = Uri.parse(mediaUrl);
        String mimeType = FileBackend.getMimeType(this, uri);
        if (!TextUtils.isEmpty(mimeType) && (mimeType.contains("video") || mimeType.contains("audio"))) {
            mediaItem = MediaItem.fromUri(mediaUrl);
        }
        else if (mediaUrl.matches("http[s]*://[w.]*youtu[.]*be.*")) {
            playYoutubeUrl(mediaUrl);
        }
        else {
            mediaItem = new MediaItem.Builder()
                    .setUri(mediaUrl)
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build();
        }
        return mediaItem;
    }

    /**
     * see https://github.com/HaarigerHarald/android-youtubeExtractor
     * see https://github.com/flagbug/YoutubeExtractor
     *
     * @param youtubeLink the given youtube playback link
     */
    @SuppressLint("StaticFieldLeak")
    private void playYoutubeUrl(String youtubeLink)
    {
        //        try {
        //            Field field = YouTubeExtractor.class.getDeclaredField("LOGGING");
        //            field.setAccessible(true);
        //            field.set(field, true);
        //        } catch (NoSuchFieldException | IllegalAccessException e) {
        //            Timber.w("Exception: %s", e.getMessage());
        //        }

        try {
            new YouTubeExtractor(this)
            {
                @Override
                public void onExtractionComplete(SparseArray<YtFile> ytFiles, VideoMeta vMeta)
                {
                    if (ytFiles != null) {
                        int itag = ytFiles.keyAt(0); //22; get the first available itag
                        String downloadUrl = ytFiles.get(itag).getUrl();
                        MediaItem mediaItem = MediaItem.fromUri(downloadUrl);
                        playMedia(mediaItem);
                    }
                    else {
                        HymnsApp.showToastMessage(R.string.gui_error_playback);
                        playVideoUrlExt(youtubeLink);
                    }
                }
            }.extract(youtubeLink, true, true);
        } catch (Exception e) {
            Timber.e("YouTubeExtractor Exception: %s", e.getMessage());
        }
    }

    /**
     * playback in full screen
     */
    private void hideSystemUi()
    {
        mPlayerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    /**
     * ExoPlayer playback state listener
     */
    private static class PlaybackStateListener implements Player.EventListener
    {
        @Override
        public void onPlaybackStateChanged(int playbackState)
        {
            switch (playbackState) {
                case ExoPlayer.STATE_IDLE:
                    HymnsApp.showToastMessage(R.string.gui_error_playback);
                    break;

                case ExoPlayer.STATE_ENDED:
                    HymnsApp.showToastMessage(R.string.gui_playback_completed);
                    break;

                case ExoPlayer.STATE_BUFFERING:
                case ExoPlayer.STATE_READY:
                default:
                    break;
            }
        }
    }

    /**
     * Play the specified videoUrl using android Intent.ACTION_VIEW
     *
     * @param videoUrl videoUrl not playable by ExoPlayer
     */
    private void playVideoUrlExt(String videoUrl)
    {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}

