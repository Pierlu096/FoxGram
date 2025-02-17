package it.foxgram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CreationTextCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.ChatAttachAlertDocumentLayout;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

import it.foxgram.android.CustomEmojiController;
import it.foxgram.android.FoxConfig;
import it.foxgram.android.http.FileDownloader;
import it.foxgram.android.utils.FileUnzip;
import it.foxgram.ui.Cells.EmojiSet;

public class EmojiPackSettings extends BaseSettingsActivity implements NotificationCenter.NotificationCenterDelegate, ChatAttachAlertDocumentLayout.DocumentSelectActivityDelegate {

    private final ArrayList<CustomEmojiController.EmojiPackBase> emojiPacks = new ArrayList<>();
    private final ArrayList<CustomEmojiController.EmojiPackBase> customEmojiPacks = new ArrayList<>();

    private int generalHeaderRow;
    private int emojiDividerRow;
    private int useSystemEmojiRow;
    private int useCustomEmojiHeaderRow;
    private int customEmojiHintRow;
    private int customEmojiStartRow;
    private int customEmojiEndRow;
    private int customEmojiAddRow;
    private int emojiPackHeaderRow;
    private int placeHolderRow;
    private int emojiPacksStartRow;
    private int emojiPacksEndRow;
    private int emojiHintRow;

    private ChatAttachAlert chatAttachAlert;
    private NumberTextView selectedCountTextView;
    private AlertDialog progressDialog;

    private static final int MENU_DELETE = 0;
    private static final int MENU_SHARE = 1;

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        ActionBarMenu actionMode = actionBar.createActionMode();
        selectedCountTextView = new NumberTextView(actionMode.getContext());
        selectedCountTextView.setTextSize(18);
        selectedCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        actionMode.addItemWithWidth(MENU_SHARE, R.drawable.msg_share, AndroidUtilities.dp(54));
        actionMode.addItemWithWidth(MENU_DELETE, R.drawable.msg_delete, AndroidUtilities.dp(54));
        return view;
    }

    @Override
    protected void onMenuItemClick(int id) {
        super.onMenuItemClick(id);
        if (id == MENU_DELETE || id == MENU_SHARE) {
            ListAdapter adapter = (ListAdapter) listAdapter;
            adapter.processSelectionMenu(id);
        }
    }


    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        if (position >= customEmojiStartRow && position < customEmojiEndRow) {
            ((ListAdapter) listAdapter).toggleSelected(position);
            return true;
        }
        return super.onItemLongClick(view, position, x, y);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        super.onItemClick(view, position, x, y);
        ListAdapter adapter = (ListAdapter) listAdapter;
        if (position >= emojiPacksStartRow && position < emojiPacksEndRow) {
            EmojiSet cell = (EmojiSet) view;
            if (cell.isChecked() || adapter.hasSelected()) return;
            String currentDownloading = getCurrentDownloading();
            String currentUnzipping = getCurrentUnzipping();
            boolean isDownloading = FileDownloader.isRunningDownload(cell.packId);
            boolean isUnzipping = FileUnzip.isRunningUnzip(cell.packId);
            if (!isDownloading && currentDownloading != null) {
                FileDownloader.cancel(currentDownloading);
            }
            if (!isUnzipping && currentUnzipping != null) {
                FileUnzip.cancel(currentUnzipping);
            }
            if (isDownloading || isUnzipping) return;
            if (CustomEmojiController.emojiDir(cell.packId, cell.versionWithMD5).exists() || cell.packId.equals("default")) {
                FoxConfig.setEmojiPackSelected(cell.packId);
                cell.setChecked(true, true);
                Emoji.reloadEmoji();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.emojiLoaded);
                if (FoxConfig.useSystemEmoji) {
                    FoxConfig.toggleUseSystemEmoji();
                    listAdapter.notifyItemChanged(useSystemEmojiRow, PARTIAL);
                }
            } else {
                cell.setProgress(true, true);
                CustomEmojiController.mkDirs();
                FileDownloader.downloadFile(ApplicationLoader.applicationContext, cell.packId, CustomEmojiController.emojiTmp(cell.packId), cell.packFileLink);
            }
            adapter.notifyEmojiSetsChanged();
        } else if (position == useSystemEmojiRow) {
            FoxConfig.toggleUseSystemEmoji();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(FoxConfig.useSystemEmoji);
            }
            Emoji.reloadEmoji();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.emojiLoaded);
            if (FoxConfig.useSystemEmoji) {
                FileDownloader.cancel(getCurrentDownloading());
                FileUnzip.cancel(getCurrentUnzipping());
            }
            adapter.notifyEmojiSetsChanged();
        } else if (position == customEmojiAddRow) {
            chatAttachAlert = new ChatAttachAlert(context, EmojiPackSettings.this, false, false);
            chatAttachAlert.setEmojiPicker();
            chatAttachAlert.init();
            chatAttachAlert.show();
        } else if (position >= customEmojiStartRow && position < customEmojiEndRow) {
            EmojiSet cell = (EmojiSet) view;
            if (adapter.hasSelected()) {
                adapter.toggleSelected(position);
            } else {
                if (cell.isChecked()) return;
                cell.setChecked(true, true);
                adapter.notifyEmojiSetsChanged();
                FoxConfig.setEmojiPackSelected(cell.packId);
                FileDownloader.cancel(getCurrentDownloading());
                FileUnzip.cancel(getCurrentUnzipping());
                Emoji.reloadEmoji();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.emojiLoaded);
                if (FoxConfig.useSystemEmoji) {
                    FoxConfig.toggleUseSystemEmoji();
                    listAdapter.notifyItemChanged(useSystemEmojiRow, PARTIAL);
                }
            }
        }
    }

    private String getCurrentUnzipping() {
        return emojiPacks
                .stream()
                .map(CustomEmojiController.EmojiPackBase::getPackId)
                .filter(FileUnzip::isRunningUnzip)
                .findFirst()
                .orElse(null);
    }

    private String getCurrentDownloading() {
        return emojiPacks
                .stream()
                .map(CustomEmojiController.EmojiPackBase::getPackId)
                .filter(FileDownloader::isRunningDownload)
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void attachListeners(EmojiSet cell) {
        FileDownloader.addListener(cell.packId, "emojiCellSettings", new FileDownloader.FileDownloadListener() {
            @Override
            public void onPreStart(String id) {
                if (cell.packId.equals(id)) {
                    cell.setProgress(0, 0, true);
                }
            }

            @Override
            public void onProgressChange(String id, int percentage, long downBytes, long totBytes) {
                if (cell.packId.equals(id)) {
                    cell.setProgress(percentage, downBytes, false);
                }
            }

            @Override
            public void onFinished(String id, boolean isFailed) {
                if (cell.packId.equals(id)) {
                    if (CustomEmojiController.emojiTmpDownloaded(cell.packId)) {
                        FileUnzip.unzipFile(ApplicationLoader.applicationContext, cell.packId, CustomEmojiController.emojiTmp(cell.packId), CustomEmojiController.emojiDir(cell.packId, cell.versionWithMD5));
                    } else {
                        CustomEmojiController.emojiTmp(cell.packId).delete();
                        ((ListAdapter) listAdapter).notifyEmojiSetsChanged();
                        if (isFailed) BulletinFactory.of(EmojiPackSettings.this).createErrorBulletin(LocaleController.getString("EmojiSetErrorDownloading", R.string.EmojiSetErrorDownloading)).show();
                    }
                }
                cell.checkDownloaded(true);
            }
        });
        FileUnzip.addListener(cell.packId, "emojiCellSettings", (id) -> {
            if (cell.packId.equals(id)) {
                CustomEmojiController.emojiTmp(cell.packId).delete();
                if (CustomEmojiController.emojiDir(cell.packId, cell.versionWithMD5).exists()) {
                    FoxConfig.setEmojiPackSelected(cell.packId);
                    CustomEmojiController.deleteOldVersions(cell.packId, cell.versionWithMD5);
                    Emoji.reloadEmoji();
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.emojiLoaded);
                    if (FoxConfig.useSystemEmoji) {
                        FoxConfig.toggleUseSystemEmoji();
                        listAdapter.notifyItemChanged(useSystemEmojiRow, PARTIAL);
                    }
                }
            }
            ((ListAdapter) listAdapter).notifyEmojiSetsChanged();
            cell.checkDownloaded(true);
        });
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiPacksLoaded);
        CustomEmojiController.loadEmojisInfo();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiPacksLoaded);
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString("EmojiSets", R.string.EmojiSets);
    }

    @Override
    protected void updateRowsId() {
        super.updateRowsId();
        updatePacks();
        emojiPacksStartRow = -1;
        emojiPacksEndRow = -1;
        customEmojiStartRow = -1;
        customEmojiEndRow = -1;
        placeHolderRow = -1;

        generalHeaderRow =  rowCount++;
        useSystemEmojiRow = rowCount++;
        emojiDividerRow = rowCount++;
        useCustomEmojiHeaderRow = rowCount++;
        if (CustomEmojiController.getLoadingStatus() >= CustomEmojiController.LOADED_LOCAL || !customEmojiPacks.isEmpty()) {
            customEmojiStartRow = rowCount;
            rowCount += customEmojiPacks.size();
            customEmojiEndRow = rowCount;
        }
        customEmojiAddRow = rowCount++;
        customEmojiHintRow = rowCount++;

        emojiPackHeaderRow = rowCount++;
        if (CustomEmojiController.getLoadingStatus() == CustomEmojiController.LOADED_REMOTE || !emojiPacks.isEmpty()) {
            emojiPacksStartRow = rowCount;
            rowCount += emojiPacks.size();
            emojiPacksEndRow = rowCount;
        } else {
            placeHolderRow = rowCount++;
        }
        emojiHintRow = rowCount++;
    }

    public void updatePacks() {
        emojiPacks.clear();
        customEmojiPacks.clear();
        emojiPacks.addAll(CustomEmojiController.getEmojiPacksInfo());
        customEmojiPacks.addAll(CustomEmojiController.getEmojiCustomPacksInfo());
    }

    @Override
    protected BaseListAdapter createAdapter() {
        return new ListAdapter();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiPacksLoaded) {
            if (CustomEmojiController.getLoadingStatus() == CustomEmojiController.FAILED) {
                AndroidUtilities.runOnUIThread(CustomEmojiController::loadEmojisInfo, 1000);
            } else {
                updateListAnimated();
            }
        }
    }

    @Override
    public void didSelectFiles(ArrayList<String> files, String caption, ArrayList<MessageObject> fMessages, boolean notify, int scheduleDate) {
        ArrayList<File> filesToUpload = new ArrayList<>();
        for (String file : files) {
            File f = new File(file);
            if (f.exists()) {
                filesToUpload.add(f);
            }
        }
        processFiles(filesToUpload);
    }

    private class ListAdapter extends BaseListAdapter {
        private final SparseBooleanArray selectedItems = new SparseBooleanArray();

        @Override
        protected void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (ViewType.fromInt(holder.getItemViewType())) {
                case HEADER:
                    HeaderCell headerViewHolder = (HeaderCell) holder.itemView;
                    if (position == emojiPackHeaderRow) {
                        headerViewHolder.setText(LocaleController.getString("EmojiSets", R.string.EmojiSets));
                    } else if (position == generalHeaderRow) {
                        headerViewHolder.setText(LocaleController.getString("General", R.string.General));
                    } else if (position == useCustomEmojiHeaderRow) {
                        headerViewHolder.setText(LocaleController.getString("MyEmojiSets", R.string.MyEmojiSets));
                    }
                    break;
                case EMOJI_PACK_SET_CELL:
                    EmojiSet emojiPackSetCell = (EmojiSet) holder.itemView;
                    CustomEmojiController.EmojiPackBase emojiPackInfo = null;
                    if (position >= emojiPacksStartRow && position < emojiPacksEndRow) {
                        emojiPackInfo = emojiPacks.get(position - emojiPacksStartRow);
                    } else if (position >= customEmojiStartRow && position < customEmojiEndRow) {
                        emojiPackInfo = customEmojiPacks.get(position - customEmojiStartRow);
                    }
                    emojiPackSetCell.setSelected(selectedItems.get(position, false), partial);
                    if (emojiPackInfo != null) {
                        emojiPackSetCell.setChecked(!hasSelected() && emojiPackInfo.getPackId().equals(CustomEmojiController.getSelectedEmojiPackId()) && getCurrentDownloading() == null && getCurrentUnzipping() == null && !FoxConfig.useSystemEmoji, partial);
                        emojiPackSetCell.setData(
                                emojiPackInfo,
                                partial,
                                true
                        );
                        if (emojiPackInfo instanceof CustomEmojiController.EmojiPackInfo) {
                            attachListeners(emojiPackSetCell);
                        }
                    }
                    break;
                case TEXT_HINT_WITH_PADDING:
                    TextInfoPrivacyCell textInfoPrivacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == emojiHintRow) {
                        textInfoPrivacyCell.setText(LocaleController.getString("EmojiSetHint", R.string.EmojiSetHint));
                    } else if (position == customEmojiHintRow) {
                        textInfoPrivacyCell.setText(LocaleController.getString("CustomEmojiSetHint", R.string.CustomEmojiSetHint));
                    }
                    break;
                case SWITCH:
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    if (position == useSystemEmojiRow) {
                        if (!partial) textCheckCell.setTextAndValueAndCheck(LocaleController.getString("UseSystemEmojis", R.string.UseSystemEmojis), LocaleController.getString("UseSystemEmojisDesc", R.string.UseSystemEmojisDesc), FoxConfig.useSystemEmoji, true, false);
                        if (partial) textCheckCell.setChecked(FoxConfig.useSystemEmoji);
                    }
                    break;
                case CREATION_TEXT_CELL:
                    CreationTextCell creationTextCell = (CreationTextCell) holder.itemView;
                    if (position == customEmojiAddRow) {
                        Drawable drawable1 = creationTextCell.getContext().getResources().getDrawable(R.drawable.poll_add_circle);
                        Drawable drawable2 = creationTextCell.getContext().getResources().getDrawable(R.drawable.poll_add_plus);
                        drawable1.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_switchTrackChecked), PorterDuff.Mode.MULTIPLY));
                        drawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_checkboxCheck), PorterDuff.Mode.MULTIPLY));
                        CombinedDrawable combinedDrawable = new CombinedDrawable(drawable1, drawable2);
                        creationTextCell.setTextAndIcon(LocaleController.getString("AddEmojiSet", R.string.AddEmojiSet), combinedDrawable, false);
                    }
                    break;
                case PLACEHOLDER:
                    FlickerLoadingView flickerLoadingView = (FlickerLoadingView) holder.itemView;
                    flickerLoadingView.setViewType(FlickerLoadingView.STICKERS_TYPE);
                    flickerLoadingView.setIsSingleCell(true);
                    break;
            }
        }

        @Override
        protected ViewType getViewType(int position) {
            if (position == emojiPackHeaderRow || position == generalHeaderRow || position == useCustomEmojiHeaderRow) {
                return ViewType.HEADER;
            } else if ((position >= emojiPacksStartRow && position < emojiPacksEndRow) || (position >= customEmojiStartRow && position < customEmojiEndRow)) {
                return ViewType.EMOJI_PACK_SET_CELL;
            } else if (position == emojiHintRow || position == customEmojiHintRow) {
                return ViewType.TEXT_HINT_WITH_PADDING;
            } else if (position == useSystemEmojiRow) {
                return ViewType.SWITCH;
            } else if (position == emojiDividerRow) {
                return ViewType.SHADOW;
            } else if (position == customEmojiAddRow) {
                return ViewType.CREATION_TEXT_CELL;
            } else if (position == placeHolderRow) {
                return ViewType.PLACEHOLDER;
            }
            throw new IllegalArgumentException("Invalid position");
        }

        @Override
        protected boolean isEnabled(ViewType viewType, int position) {
            return viewType == ViewType.EMOJI_PACK_SET_CELL || viewType == ViewType.SWITCH || viewType == ViewType.CREATION_TEXT_CELL;
        }

        public void toggleSelected(int position) {
            selectedItems.put(position, !selectedItems.get(position, false));
            notifyEmojiSetsChanged();
            checkActionMode();
        }

        public boolean hasSelected() {
            return selectedItems.indexOfValue(true) != -1;
        }

        public void clearSelected() {
            selectedItems.clear();
            notifyEmojiSetsChanged();
            checkActionMode();
        }

        public int getSelectedCount() {
            int count = 0;
            for (int i = 0, size = selectedItems.size(); i < size; i++) {
                if (selectedItems.valueAt(i)) {
                    count++;
                }
            }
            return count;
        }

        private void notifyEmojiSetsChanged() {
            notifyItemRangeChanged(customEmojiStartRow, customEmojiEndRow - customEmojiStartRow, PARTIAL);
            notifyItemRangeChanged(emojiPacksStartRow, emojiPacksEndRow - emojiPacksStartRow, PARTIAL);
        }

        private void checkActionMode() {
            int selectedCount = getSelectedCount();
            boolean actionModeShowed = actionBar.isActionModeShowed();
            if (selectedCount > 0) {
                selectedCountTextView.setNumber(selectedCount, actionModeShowed);
                if (!actionModeShowed) {
                    actionBar.showActionMode();
                }
            } else if (actionModeShowed) {
                actionBar.hideActionMode();
            }
        }

        private void processSelectionMenu(int which) {

            if (which == MENU_DELETE || which == MENU_SHARE) {
                ArrayList<CustomEmojiController.EmojiPackBase> stickerSetList = new ArrayList<>(selectedItems.size());
                ArrayList<CustomEmojiController.EmojiPackBase> packs = customEmojiPacks;
                for (int i = 0, size = packs.size(); i < size; i++) {
                    CustomEmojiController.EmojiPackBase pack = packs.get(i);
                    if (selectedItems.get(customEmojiStartRow + i, false)) {
                        stickerSetList.add(pack);
                    }
                }
                int count = stickerSetList.size();
                switch (count) {
                    case 0:
                        break;
                    case 1:
                        CustomEmojiController.EmojiPackBase pack = stickerSetList.get(0);
                        if (which == MENU_SHARE) {
                            Intent intent = new Intent(context, LaunchActivity.class);
                            intent.setAction(Intent.ACTION_SEND);
                            Uri uri = Uri.fromFile(new File(pack.getFileLocation()));
                            intent.putExtra(Intent.EXTRA_STREAM, uri);
                            context.startActivity(intent);
                            clearSelected();
                        } else {
                            CustomEmojiController.cancelableDelete(EmojiPackSettings.this, pack, new CustomEmojiController.OnBulletinAction() {
                                @Override
                                public void onPreStart() {
                                    notifyItemRemoved(customEmojiStartRow + packs.indexOf(pack));
                                    notifyEmojiSetsChanged();
                                    updateRowsId();
                                    clearSelected();
                                }

                                @Override
                                public void onUndo() {
                                    updateRowsId();
                                    notifyItemInserted(customEmojiStartRow + customEmojiPacks.indexOf(pack));
                                    notifyEmojiSetsChanged();
                                }
                            });
                        }
                        break;
                    default:
                        if (which == MENU_SHARE) {
                            Intent intent = new Intent(context, LaunchActivity.class);
                            intent.setAction(Intent.ACTION_SEND_MULTIPLE);
                            ArrayList<Uri> uriList = new ArrayList<>();
                            for (CustomEmojiController.EmojiPackBase packTmp : stickerSetList) {
                                uriList.add(Uri.fromFile(new File(packTmp.getFileLocation())));
                            }
                            if (!uriList.isEmpty()) {
                                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);
                                context.startActivity(intent);
                            }
                        } else {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.formatString("DeleteStickerSetsAlertTitle", R.string.DeleteStickerSetsAlertTitle, LocaleController.formatString("DeleteEmojiSets", R.string.DeleteEmojiSets, count)));
                            builder.setMessage(LocaleController.getString("DeleteEmojiSetsMessage", R.string.DeleteEmojiSetsMessage));
                            builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialog, which1) -> {
                                AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
                                new Thread() {
                                    @Override
                                    public void run() {
                                        for (int i = 0, size = stickerSetList.size(); i < size; i++) {
                                            CustomEmojiController.deleteEmojiPack(stickerSetList.get(i));
                                        }
                                        AndroidUtilities.runOnUIThread(() -> {
                                            progressDialog.dismiss();
                                            clearSelected();
                                            updateListAnimated();
                                            Emoji.reloadEmoji();
                                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.emojiLoaded);
                                        });
                                    }
                                }.start();
                                progressDialog.setCanCancel(false);
                                progressDialog.showDelayed(300);
                            });
                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            AlertDialog dialog = builder.create();
                            showDialog(dialog);
                            TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                            if (button != null) {
                                button.setTextColor(Theme.getColor(Theme.key_dialogTextRed));
                            }
                        }
                        break;
                }
            }
        }
    }

    public void startDocumentSelectActivity() {
        try {
            Intent photoPickerIntent = new Intent(Intent.ACTION_GET_CONTENT);
            photoPickerIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            photoPickerIntent.setType("font/*");
            startActivityForResult(photoPickerIntent, 21);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        progressDialog = new AlertDialog(getParentActivity(), 3);
        new Thread(() -> {
            if (requestCode == 21) {
                if (data == null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (progressDialog != null) {
                            progressDialog.dismiss();
                            progressDialog = null;
                        }
                    });
                    return;
                }

                if (chatAttachAlert != null) {
                    ArrayList<File> files = CustomEmojiController.getFilesFromActivityResult(data);
                    AndroidUtilities.runOnUIThread(() -> {
                        boolean apply = files.stream().allMatch(file -> chatAttachAlert.getDocumentLayout().isEmojiFont(file));
                        if (apply && !files.isEmpty()) {
                            chatAttachAlert.dismiss();
                            processFiles(files);
                        } else {
                            progressDialog.dismiss();
                            progressDialog = null;
                        }
                    });
                }
            }
        }).start();
        progressDialog.setCanCancel(false);
        progressDialog.showDelayed(300);
    }

    public void processFiles(ArrayList<File> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        if (progressDialog == null) {
            progressDialog = new AlertDialog(getParentActivity(), 3);
            progressDialog.setCanCancel(false);
            progressDialog.showDelayed(300);
        }
        new Thread(() -> {
            int count = 0;
            for (File file : files) {
                try {
                    if (CustomEmojiController.installEmoji(file) != null) {
                        count++;
                    }
                } catch (Exception e) {
                    FileLog.e("Emoji Font install failed", e);
                } finally {
                    CustomEmojiController.deleteTempFile(file);
                }
            }
            int finalCount = count;
            AndroidUtilities.runOnUIThread(() -> {
                progressDialog.dismiss();
                progressDialog = null;
                listAdapter.notifyItemRangeInserted(customEmojiEndRow, finalCount);
                updateRowsId();
            });
        }).start();
    }

    @Override
    public boolean onBackPressed() {
        ListAdapter adapter = (ListAdapter) listAdapter;
        if (adapter.hasSelected()) {
            adapter.clearSelected();
            return false;
        }
        return super.onBackPressed();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateListAnimated() {
        if (listAdapter == null) {
            updateRowsId();
            return;
        }
        DiffCallback diffCallback = new DiffCallback();
        diffCallback.oldRowCount = rowCount;
        diffCallback.fillPositions(diffCallback.oldPositionToItem);
        diffCallback.oldEmojiPacks.clear();
        diffCallback.oldCustomPacks.clear();
        diffCallback.oldEmojiPacks.addAll(emojiPacks);
        diffCallback.oldCustomPacks.addAll(customEmojiPacks);
        diffCallback.oldEmojiPacksStartRow = emojiPacksStartRow;
        diffCallback.oldEmojiPacksEndRow = emojiPacksEndRow;
        diffCallback.oldCustomEmojiStartRow = customEmojiStartRow;
        diffCallback.oldCustomEmojiEndRow = customEmojiEndRow;
        updateRowsId();
        diffCallback.fillPositions(diffCallback.newPositionToItem);
        try {
            DiffUtil.calculateDiff(diffCallback).dispatchUpdatesTo(listAdapter);
        } catch (Exception e) {
            FileLog.e(e);
            listAdapter.notifyDataSetChanged();
        }
        AndroidUtilities.updateVisibleRows(listView);
    }

    private class DiffCallback extends DiffUtil.Callback {

        int oldRowCount;

        SparseIntArray oldPositionToItem = new SparseIntArray();
        SparseIntArray newPositionToItem = new SparseIntArray();
        ArrayList<CustomEmojiController.EmojiPackBase> oldEmojiPacks = new ArrayList<>();
        ArrayList<CustomEmojiController.EmojiPackBase> oldCustomPacks = new ArrayList<>();
        int oldEmojiPacksStartRow;
        int oldEmojiPacksEndRow;
        int oldCustomEmojiStartRow;
        int oldCustomEmojiEndRow;

        @Override
        public int getOldListSize() {
            return oldRowCount;
        }

        @Override
        public int getNewListSize() {
            return rowCount;
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            if (newItemPosition >= emojiPacksStartRow && newItemPosition < emojiPacksEndRow) {
                if (oldItemPosition >= oldEmojiPacksStartRow && oldItemPosition < oldEmojiPacksEndRow) {
                    CustomEmojiController.EmojiPackBase oldItem = oldEmojiPacks.get(oldItemPosition - oldEmojiPacksStartRow);
                    CustomEmojiController.EmojiPackBase newItem = emojiPacks.get(newItemPosition - emojiPacksStartRow);
                    return Objects.equals(oldItem.getPackId(), newItem.getPackId());
                }
            }
            if (newItemPosition >= customEmojiStartRow && newItemPosition < customEmojiEndRow) {
                if (oldItemPosition >= oldCustomEmojiStartRow && oldItemPosition < oldCustomEmojiEndRow) {
                    CustomEmojiController.EmojiPackBase oldItem = oldCustomPacks.get(oldItemPosition - oldCustomEmojiStartRow);
                    CustomEmojiController.EmojiPackBase newItem = customEmojiPacks.get(newItemPosition - customEmojiStartRow);
                    return Objects.equals(oldItem.getPackId(), newItem.getPackId());
                }
            }
            int oldIndex = oldPositionToItem.get(oldItemPosition, -1);
            int newIndex = newPositionToItem.get(newItemPosition, -1);
            return oldIndex == newIndex && oldIndex >= 0;
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return areItemsTheSame(oldItemPosition, newItemPosition);
        }

        public void fillPositions(SparseIntArray sparseIntArray) {
            sparseIntArray.clear();
            int pointer = 0;

            put(++pointer, generalHeaderRow, sparseIntArray);
            put(++pointer, useSystemEmojiRow, sparseIntArray);
            put(++pointer, emojiDividerRow, sparseIntArray);
            put(++pointer, useCustomEmojiHeaderRow, sparseIntArray);
            put(++pointer, placeHolderRow, sparseIntArray);
            put(++pointer, customEmojiAddRow, sparseIntArray);
            put(++pointer, customEmojiHintRow, sparseIntArray);
            put(++pointer, emojiPackHeaderRow, sparseIntArray);
            put(++pointer, emojiHintRow, sparseIntArray);
        }

        private void put(int id, int position, SparseIntArray sparseIntArray) {
            if (position >= 0) {
                sparseIntArray.put(position, id);
            }
        }
    }
}
