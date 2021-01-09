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
package org.cog.hymnchtv.webview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.view.View.OnKeyListener;
import android.webkit.*;
import android.widget.ProgressBar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import org.cog.hymnchtv.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Stack;

import timber.log.Timber;

/**
 * The class displays the content accessed via given web link
 *
 * @author Eng Chong Meng
 */
@SuppressLint("SetJavaScriptEnabled")
public class WebViewFragment extends Fragment implements OnKeyListener
{
    private WebView webview;
    private ProgressBar progressbar;
    private static final Stack<String> urlStack = new Stack<>();

    // stop webView.goBack() once we have started reload from urlStack
    private boolean isLoadFromStack = false;

    private String webUrl = null;
    private ValueCallback<Uri[]> mUploadMessageArray;

    @SuppressLint("JavascriptInterface")
    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        // init webUrl with urlStack.pop() if non-empty, else load from default in DB
        if (urlStack.isEmpty()) {
            webUrl = ((ContentHandler) getActivity()).getWebUrl();
            urlStack.push(webUrl);
        }
        else {
            webUrl = urlStack.pop();
        }

        View contentView = inflater.inflate(R.layout.webview_main, container, false);
        progressbar = contentView.findViewById(R.id.progress);
        progressbar.setIndeterminate(true);

        webview = contentView.findViewById(R.id.webview);

        final WebSettings webSettings = webview.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        webview.addJavascriptInterface(HymnsApp.getGlobalContext(), "Android");
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        webSettings.setMixedContentMode(0);
        webSettings.setAllowUniversalAccessFromFileURLs(true);

        ActivityResultLauncher<String> mGetContents = getFileUris();
        webview.setWebChromeClient(new WebChromeClient()
        {
            public void onProgressChanged(WebView view, int progress)
            {
                progressbar.setProgress(progress);
                if (progress < 100 && progress > 0 && progressbar.getVisibility() == ProgressBar.GONE) {
                    progressbar.setIndeterminate(true);
                    progressbar.setVisibility(ProgressBar.VISIBLE);
                }
                if (progress == 100) {
                    progressbar.setVisibility(ProgressBar.GONE);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> uploadMessageArray,
                    FileChooserParams fileChooserParams)
            {
                if (mUploadMessageArray != null)
                    mUploadMessageArray.onReceiveValue(null);

                mUploadMessageArray = uploadMessageArray;
                mGetContents.launch("*/*");
                return true;
            }
        });

        webview.setWebViewClient(new MyWebViewClient(this));
        if (!TextUtils.isEmpty(webUrl))
            webview.loadUrl(webUrl);
        return contentView;
    }

    @Override
    public void onResume()
    {
        super.onResume();

        // setup keyPress listener - must re-enable every time on resume
        webview.setFocusableInTouchMode(true);
        webview.requestFocus();
        webview.setOnKeyListener(this);
    }

    /**
     * Opens a FileChooserDialog to let the user pick files for upload
     */
    private ActivityResultLauncher<String> getFileUris()
    {
        return registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
            if (uris != null) {
                if (mUploadMessageArray == null)
                    return;

                Uri[] uriArray = new Uri[uris.size()];
                uriArray = uris.toArray(uriArray);

                mUploadMessageArray.onReceiveValue(uriArray);
                mUploadMessageArray = null;
            }
            else {
                HymnsApp.showToastMessage(R.string.gui_file_DOES_NOT_EXIST);
            }
        });
    }

    // Prevent the webView from reloading on device rotation
    @Override
    public void onConfigurationChanged(@NotNull Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
    }

    /**
     * Init webView to download a new web page if it is not the same link
     */
    public void initWebView()
    {
        String tmp = ((ContentHandler) getActivity()).getWebUrl();
        if (webUrl == null || !webUrl.equals(tmp)) {
            urlStack.clear();
            webUrl = tmp;
            urlStack.push(webUrl);
            webview.loadUrl(webUrl);
        }
    }

    /**
     * Add the own loaded url page to stack for later retrieval (goBack)
     *
     * @param url loaded url
     */
    public void addUrl(String url)
    {
        urlStack.push(url);
        isLoadFromStack = false;
    }

    public static Bitmap getBitmapFromURL(String src)
    {
        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Handler for user enter Back Key
     * User Back Key entry will return to previous web access pages until root; before return to caller
     *
     * @param v view
     * @param keyCode the entered key keycode
     * @param event the key Event
     *
     * @return true if proccess
     */
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event)
    {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            // android OS will not pass in KEYCODE_MENU???
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                webview.loadUrl("javascript:MovimTpl.toggleMenu()");
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (!isLoadFromStack && webview.canGoBack()) {
                    if (!urlStack.isEmpty())
                        urlStack.pop();
                    webview.goBack();
                    return true;
                }
                // else continue to reload url from urlStack if non-empty.
                else if (!urlStack.isEmpty()) {
                    isLoadFromStack = true;
                    webUrl = urlStack.pop();
                    Timber.w("urlStack pop(): %s", webUrl);
                    webview.loadUrl(webUrl);
                    return true;
                }
            }
        }
        return false;
    }
}