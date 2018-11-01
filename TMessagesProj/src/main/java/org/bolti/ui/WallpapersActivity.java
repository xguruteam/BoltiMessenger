/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.bolti.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.bolti.messenger.AndroidUtilities;
import org.bolti.messenger.DispatchQueue;
import org.bolti.messenger.LocaleController;
import org.bolti.messenger.MessagesController;
import org.bolti.messenger.support.widget.LinearLayoutManager;
import org.bolti.messenger.support.widget.RecyclerView;
import org.bolti.messenger.ApplicationLoader;
import org.bolti.tgnet.ConnectionsManager;
import org.bolti.tgnet.RequestDelegate;
import org.bolti.tgnet.TLObject;
import org.bolti.tgnet.TLRPC;
import org.bolti.messenger.FileLoader;
import org.bolti.messenger.FileLog;
import org.bolti.messenger.MessagesStorage;
import org.bolti.messenger.NotificationCenter;
import org.bolti.messenger.R;

import org.bolti.ui.ActionBar.ActionBar;
import org.bolti.ui.ActionBar.ActionBarMenu;
import org.bolti.ui.ActionBar.Theme;
import org.bolti.ui.ActionBar.ThemeDescription;
import org.bolti.ui.Cells.WallpaperCell;
import org.bolti.ui.ActionBar.BaseFragment;
import org.bolti.ui.Components.LayoutHelper;
import org.bolti.ui.Components.RadialProgressView;
import org.bolti.ui.Components.RecyclerListView;
import org.bolti.ui.Components.WallpaperUpdater;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class WallpapersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private ImageView backgroundImage;
    private FrameLayout progressView;
    private View progressViewBackground;
    private View doneButton;
    private RecyclerListView listView;
    private WallpaperUpdater updater;
    private File wallpaperFile;
    private Drawable themedWallpaper;

    private int selectedBackground;
    private boolean overrideThemeWallpaper;
    private int selectedColor;
    private ArrayList<Integer> wallPapers = new ArrayList<>();
//    private SparseArray<TLRPC.WallPaper> wallpappersByIds = new SparseArray<>();
    public static final int WALLPAPER_STATIC_ID_START = 100;

    private String loadingFile = null;
    private File loadingFileObject = null;
//    private TLRPC.PhotoSize loadingSize = null;

    private final static int done_button = 1;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.FileDidFailedLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.wallpapersDidLoaded);

        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        selectedBackground = preferences.getInt("selectedBackground", 1000001);
        overrideThemeWallpaper = preferences.getBoolean("overrideThemeWallpaper", false);
        selectedColor = preferences.getInt("selectedColor", 0);
        MessagesStorage.getInstance(currentAccount).getWallpapers();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        updater.cleanup();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.FileDidFailedLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.wallpapersDidLoaded);
    }

    @Override
    public View createView(Context context) {
        themedWallpaper = Theme.getThemedWallpaper(true);
        updater = new WallpaperUpdater(getParentActivity(), new WallpaperUpdater.WallpaperUpdaterDelegate() {
            @Override
            public void didSelectWallpaper(File file, Bitmap bitmap) {
                selectedBackground = -1;
                overrideThemeWallpaper = true;
                selectedColor = 0;
                wallpaperFile = file;
                Drawable drawable = backgroundImage.getDrawable();
                backgroundImage.setImageBitmap(bitmap);
            }

            @Override
            public void needOpenColorPicker() {

            }
        });

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("ChatBackground", R.string.ChatBackground));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    boolean done;
                    int wallPaper = selectedBackground;
                    if (wallPaper >= WALLPAPER_STATIC_ID_START && wallPaper != 1000001) {
                        int width = AndroidUtilities.displaySize.x;
                        int height = AndroidUtilities.displaySize.y;
                        if (width > height) {
                            int temp = width;
                            width = height;
                            height = temp;
                        }
//                        TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(wallPaper.sizes, Math.min(width, height));


                        try{
                            int res_id = 0;
                            switch (wallPaper) {
                                case WALLPAPER_STATIC_ID_START:
                                    res_id = R.drawable.chat_bg_1;
                                    break;
                                case WALLPAPER_STATIC_ID_START + 1:
                                    res_id = R.drawable.chat_bg_2;
                                    break;
                                case WALLPAPER_STATIC_ID_START + 2:
                                    res_id = R.drawable.chat_bg_3;
                                    break;
                                case WALLPAPER_STATIC_ID_START + 3:
                                    res_id = R.drawable.chat_bg_4;
                                    break;
                            }
                            InputStream inputStream = getParentActivity().getResources().openRawResource(res_id);
                            File toFile = new File(ApplicationLoader.getFilesDirFixed(), "wallpaper.jpg");
                            OutputStream out = new FileOutputStream(toFile);
                            byte[] buffer = new byte[1024];
                            int read;
                            while((read = inputStream.read(buffer)) != -1){
                                out.write(buffer, 0, read);
                            }

                            out.flush();
                            out.close();
                            done = true;

                        } catch (IOException e) {
                            throw new RuntimeException("Can't create temp file ", e);
                        }
                    } else {
                        if (selectedBackground == -1) {
                            File fromFile = updater.getCurrentWallpaperPath();
                            File toFile = new File(ApplicationLoader.getFilesDirFixed(), "wallpaper.jpg");
                            try {
                                done = AndroidUtilities.copyFile(fromFile, toFile);
                            } catch (Exception e) {
                                done = false;
                                FileLog.e(e);
                            }
                        } else {
                            done = true;
                        }
                    }

                    if (done) {
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putInt("selectedBackground", selectedBackground);
                        editor.putInt("selectedColor", selectedColor);
                        editor.putBoolean("overrideThemeWallpaper", Theme.hasWallpaperFromTheme() && overrideThemeWallpaper);
                        editor.commit();
                        Theme.reloadWallpaper();
                    }
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;

        backgroundImage = new ImageView(context);
        backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frameLayout.addView(backgroundImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        backgroundImage.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        progressView = new FrameLayout(context);
        progressView.setVisibility(View.INVISIBLE);
        frameLayout.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 52));

        progressViewBackground = new View(context);
        progressViewBackground.setBackgroundResource(R.drawable.system_loader);
        progressView.addView(progressViewBackground, LayoutHelper.createFrame(36, 36, Gravity.CENTER));

        RadialProgressView progressBar = new RadialProgressView(context);
        progressBar.setSize(AndroidUtilities.dp(28));
        progressBar.setProgressColor(0xffffffff);
        progressView.addView(progressBar, LayoutHelper.createFrame(32, 32, Gravity.CENTER));

        listView = new RecyclerListView(context);
        listView.setClipToPadding(false);
        listView.setTag(8);
        listView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        listView.setLayoutManager(layoutManager);
        listView.setDisallowInterceptTouchEvents(true);
        listView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
        listView.setAdapter(listAdapter = new ListAdapter(context));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 102, Gravity.LEFT | Gravity.BOTTOM));
        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (position == 0) {
                    updater.showAlert(false);
                } else {
                    if (Theme.hasWallpaperFromTheme()) {
                        if (position == 1) {
                            selectedBackground = -2;
                            overrideThemeWallpaper = false;
                            listAdapter.notifyDataSetChanged();
                            processSelectedBackground();
                            return;
                        } else {
                            position -= 2;
                        }
                    } else {
                        position--;
                    }
                    int wallPaper = wallPapers.get(position).intValue();
                    selectedBackground = wallPaper;
                    overrideThemeWallpaper = true;
                    listAdapter.notifyDataSetChanged();
                    processSelectedBackground();
                }
            }
        });

        processSelectedBackground();

        return fragmentView;
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        updater.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void saveSelfArgs(Bundle args) {
        String currentPicturePath = updater.getCurrentPicturePath();
        if (currentPicturePath != null) {
            args.putString("path", currentPicturePath);
        }
    }

    @Override
    public void restoreSelfArgs(Bundle args) {
        updater.setCurrentPicturePath(args.getString("path"));
    }

    private void processSelectedBackground() {
        if (Theme.hasWallpaperFromTheme() && !overrideThemeWallpaper) {
            backgroundImage.setImageDrawable(Theme.getThemedWallpaper(false));
        } else {
            int wallPaper = selectedBackground;
            if (selectedBackground >= WALLPAPER_STATIC_ID_START && selectedBackground != 1000001) {
                Log.e("xxxx", "wall");
                int width = AndroidUtilities.displaySize.x;
                int height = AndroidUtilities.displaySize.y;
                if (width > height) {
                    int temp = width;
                    width = height;
                    height = temp;
                }
//                TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(wallPaper.sizes, Math.min(width, height));
//                if (size == null) {
//                    return;
//                }

                int res_id = 0;
                switch (wallPaper) {
                    case WALLPAPER_STATIC_ID_START:
                        res_id = R.drawable.chat_bg_1;
                        break;
                    case WALLPAPER_STATIC_ID_START + 1:
                        res_id = R.drawable.chat_bg_2;
                        break;
                    case WALLPAPER_STATIC_ID_START + 2:
                        res_id = R.drawable.chat_bg_3;
                        break;
                    case WALLPAPER_STATIC_ID_START + 3:
                        res_id = R.drawable.chat_bg_4;
                        break;
                }

                backgroundImage.setImageResource(res_id);
                progressView.setVisibility(View.GONE);
                backgroundImage.setBackgroundColor(0);
                doneButton.setEnabled(true);
                loadingFileObject = null;
                loadingFile = null;

//                String fileName = wallPaper + ".jpg";
//                File f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
//                if (!f.exists()) {
//                    Log.e("xxxx", "wall -1");
//                    int result[] = AndroidUtilities.calcDrawableColor(backgroundImage.getDrawable());
//                    progressViewBackground.getBackground().setColorFilter(new PorterDuffColorFilter(result[0], PorterDuff.Mode.MULTIPLY));
//                    loadingFile = fileName;
//                    loadingFileObject = f;
//                    doneButton.setEnabled(false);
//                    progressView.setVisibility(View.VISIBLE);
////                    loadingSize = size;
//                    selectedColor = 0;
//
//                    new DispatchQueue("cache chat background").postRunnable(new Runnable() {
//                        @Override
//                        public void run() {
//                            File f = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
//                            try{
//                                int res_id = 0;
//                                switch (wallPaper) {
//                                    case WALLPAPER_STATIC_ID_START:
//                                        res_id = R.drawable.chat_bg_1;
//                                        break;
//                                    case WALLPAPER_STATIC_ID_START + 1:
//                                        res_id = R.drawable.chat_bg_2;
//                                        break;
//                                    case WALLPAPER_STATIC_ID_START + 2:
//                                        res_id = R.drawable.chat_bg_3;
//                                        break;
//                                    case WALLPAPER_STATIC_ID_START + 3:
//                                        res_id = R.drawable.chat_bg_4;
//                                        break;
//                                }
//                                InputStream inputStream = getParentActivity().getResources().openRawResource(res_id);
//                                File tempFile = File.createTempFile("pre", "suf");
//                                OutputStream out = new FileOutputStream(tempFile);
//                                byte[] buffer = new byte[1024];
//                                int read;
//                                while((read = inputStream.read(buffer)) != -1){
//                                    out.write(buffer, 0, read);
//                                }
//
//                                out.flush();
//                                out.close();
//
//                            } catch (IOException e) {
//                                throw new RuntimeException("Can't create temp file ", e);
//                            }
//
//                        }
//                    });
//                    backgroundImage.setBackgroundColor(0);
//                } else {
//                    Log.e("xxxx", "wall - 3");
//                    if (loadingFile != null) {
////                        FileLoader.getInstance(currentAccount).cancelLoadFile(loadingSize);
//                    }
//                    loadingFileObject = null;
//                    loadingFile = null;
////                    loadingSize = null;
//                    try {
//                        backgroundImage.setImageURI(Uri.fromFile(f));
//                    } catch (Throwable e) {
//                        FileLog.e(e);
//                    }
//                    backgroundImage.setBackgroundColor(0);
//                    selectedColor = 0;
//                    doneButton.setEnabled(true);
//                    progressView.setVisibility(View.GONE);
//                }
            } else {
                Log.e("xxxx", "wall-4");
                if (loadingFile != null) {
//                    FileLoader.getInstance(currentAccount).cancelLoadFile(loadingSize);
                }
                if (selectedBackground == 1000001) {
                    backgroundImage.setImageResource(R.drawable.background_hd);
                    backgroundImage.setBackgroundColor(0);
                    selectedColor = 0;
                } else if (selectedBackground == -1) {
                    File toFile;
                    if (wallpaperFile != null) {
                        toFile = wallpaperFile;
                    } else {
                        toFile = new File(ApplicationLoader.getFilesDirFixed(), "wallpaper.jpg");
                    }
                    if (toFile.exists()) {
                        backgroundImage.setImageURI(Uri.fromFile(toFile));
                    } else {
                        selectedBackground = 1000001;
                        overrideThemeWallpaper = true;
                        processSelectedBackground();
                    }
                } else {
//                    if (wallPaper == null) {
//                        return;
//                    }
//                    if (wallPaper instanceof TLRPC.TL_wallPaperSolid) {
//                        Drawable drawable = backgroundImage.getDrawable();
//                        backgroundImage.setImageBitmap(null);
//                        selectedColor = 0xff000000 | wallPaper.bg_color;
//                        backgroundImage.setBackgroundColor(selectedColor);
//                    }
                }
                loadingFileObject = null;
                loadingFile = null;
//                loadingSize = null;
                doneButton.setEnabled(true);
                progressView.setVisibility(View.GONE);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.FileDidFailedLoad) {
            String location = (String) args[0];
            if (loadingFile != null && loadingFile.equals(location)) {
                loadingFileObject = null;
                loadingFile = null;
//                loadingSize = null;
                progressView.setVisibility(View.GONE);
                doneButton.setEnabled(false);
            }
        } else if (id == NotificationCenter.FileDidLoaded) {
            String location = (String) args[0];
            if (loadingFile != null && loadingFile.equals(location)) {
                backgroundImage.setImageURI(Uri.fromFile(loadingFileObject));
                progressView.setVisibility(View.GONE);
                backgroundImage.setBackgroundColor(0);
                doneButton.setEnabled(true);
                loadingFileObject = null;
                loadingFile = null;
//                loadingSize = null;
            }
        } else if (id == NotificationCenter.wallpapersDidLoaded) {
            wallPapers = (ArrayList<Integer>) args[0];
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            if (!wallPapers.isEmpty() && backgroundImage != null) {
                processSelectedBackground();
            }
            loadWallpapers();
        }
    }

    private void loadWallpapers() {
        wallPapers.clear();
        wallPapers.add(Integer.valueOf(WALLPAPER_STATIC_ID_START));
        wallPapers.add(Integer.valueOf(WALLPAPER_STATIC_ID_START + 1));
        wallPapers.add(Integer.valueOf(WALLPAPER_STATIC_ID_START + 2));
        wallPapers.add(Integer.valueOf(WALLPAPER_STATIC_ID_START + 3));
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (backgroundImage != null) {
            processSelectedBackground();
        }
        MessagesStorage.getInstance(currentAccount).putWallpapers(wallPapers);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        processSelectedBackground();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            int count = 1 + wallPapers.size();
            if (Theme.hasWallpaperFromTheme()) {
                count++;
            }
            return count;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            WallpaperCell view = new WallpaperCell(mContext);
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
            WallpaperCell wallpaperCell = (WallpaperCell) viewHolder.itemView;
            if (i == 0) {
                wallpaperCell.setWallpaper(null, !Theme.hasWallpaperFromTheme() || overrideThemeWallpaper ? selectedBackground : -2, null, false);
            } else {
                if (Theme.hasWallpaperFromTheme()) {
                    if (i == 1) {
                        wallpaperCell.setWallpaper(null, overrideThemeWallpaper ? -1 : -2, themedWallpaper, true);
                        return;
                    } else {
                        i -= 2;
                    }
                } else {
                    i--;
                }
                wallpaperCell.setWallpaper(wallPapers.get(i), !Theme.hasWallpaperFromTheme() || overrideThemeWallpaper ? selectedBackground : -2, null, false);
            }
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),
        };
    }
}
