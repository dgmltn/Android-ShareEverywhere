/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dgmltn.shareeverywhere;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.dgmltn.shareeverywhere.ActivityChooserModel.ActivityChooserModelClient;
import com.dgmltn.shareeverywhere.ActivityChooserModel.ActivityResolveInfo;

/**
 * This class is a view for choosing an activity for handling a given {@link Intent}.
 * <p>
 * The view is composed of two adjacent buttons:
 * <ul>
 * <li>
 * The left button is an immediate action and allows one click activity choosing.
 * Tapping this button immediately executes the intent without requiring any further
 * user input. Long press on this button shows a popup for changing the default
 * activity.
 * </li>
 * <li>
 * The right button is an overflow action and provides an optimized menu
 * of additional activities. Tapping this button shows a popup anchored to this
 * view, listing the most frequently used activities. This list is initially
 * limited to a small number of items in frequency used order. The last item,
 * "Show all..." serves as an affordance to display all available activities.
 * </li>
 * </ul>
 * </p>
 *
 * @hide
 */
public class ShareView extends ViewGroup implements ActivityChooserModelClient {

    /**
     * Called when a share target has been selected. The client can
     * decide whether to perform some action before the sharing is
     * actually performed.
     * <p>
     * <strong>Note:</strong> Modifying the intent is not permitted and
     *     any changes to the latter will be ignored.
     * </p>
     * <p>
     * <strong>Note:</strong> You should <strong>not</strong> handle the
     *     intent here. This callback aims to notify the client that a
     *     sharing is being performed, so the client can update the UI
     *     if necessary.
     * </p>
     *
     * @param source The source of the notification.
     * @param intent The intent for launching the chosen share target.
     */
	public interface OnShareTargetSelectedListener {
		void onShareTargetSelected(ShareView view, Intent intent);
	}

	private static final String TAG = ShareView.class.getSimpleName();

	/**
	 * The default for the maximal number of activities shown in the sub-menu.
	 */
	public static final int DEFAULT_INITIAL_ACTIVITY_COUNT = 4;

	/**
	 * The default name for storing share history.
	 */
	public static final String DEFAULT_SHARE_HISTORY_FILE_NAME = "share_history.xml";

	/**
	 * An adapter for displaying the activities in an {@link AdapterView}.
	 */
	private final ActivityChooserViewAdapter mAdapter;

	private OnShareTargetSelectedListener mListener;

	/**
	 * Implementation of various interfaces to avoid publishing them in the APIs.
	 */
	protected final Callbacks mCallbacks;

	/**
	 * The content of this view.
	 */
	private final IcsLinearLayout mActivityChooserContent;

	/**
	 * Store obtained styles for later use and lazy loading.
	 */
	private final int mBackgroundResId;
	private final int mButtonBackgroundResId;
	private final int mPopupBackgroundResId;
	private final int mPopupDividerResId;
	private final int mPopupSelectorResId;
	private final ColorStateList mPopupTextColor;

	private final Rect mActivityIconBounds;

	/**
	 * The expand activities action button;
	 */
	private final FrameLayout mExpandActivityOverflowButton;

	/**
	 * The image for the expand activities action button;
	 */
	private final ImageView mExpandActivityOverflowButtonImage;

	/**
	 * The default activities action button;
	 */
	private final FrameLayout mDefaultActivityButton;

	/**
	 * The image for the default activities action button;
	 */
	private final ImageView mDefaultActivityButtonImage;

	/**
	 * Whether the default activities action button should be displayed;
	 */
	private final boolean mDisplayDefaultActivityButton;

	/**
	 * The maximal width of the list popup.
	 */
	private final int mListPopupMaxWidth;

	/**
	 * Observer for the model data.
	 */
	private final DataSetObserver mModelDataSetObserver = new DataSetObserver() {

		@Override
		public void onChanged() {
			super.onChanged();
			mAdapter.notifyDataSetChanged();
		}

		@Override
		public void onInvalidated() {
			super.onInvalidated();
			mAdapter.notifyDataSetInvalidated();
		}
	};

	private final OnGlobalLayoutListener mOnGlobalLayoutListener = new OnGlobalLayoutListener() {
		@Override
		public void onGlobalLayout() {
			if (isShowingPopup()) {
				if (!isShown()) {
					getListPopupWindow().dismiss();
				}
				else {
					getListPopupWindow().show();
				}
			}
		}
	};

	/**
	 * Popup window for showing the activity overflow list.
	 */
	private IcsListPopupWindow mListPopupWindow;

	/**
	 * Listener for the dismissal of the popup/alert.
	 */
	private PopupWindow.OnDismissListener mOnDismissListener;

	/**
	 * Flag whether a default activity currently being selected.
	 */
	private boolean mIsSelectingDefaultActivity;

	/**
	 * The count of activities in the popup.
	 */
	private int mInitialActivityCount = DEFAULT_INITIAL_ACTIVITY_COUNT;

	/**
	 * Flag whether this view is attached to a window.
	 */
	private boolean mIsAttachedToWindow;

	/**
	 * String resource for formatting content description of the default target.
	 */
	private int mDefaultActionButtonContentDescription;

	private final Context mContext;

	/**
	 * Create a new instance.
	 *
	 * @param context The application environment.
	 */
	public ShareView(Context context) {
		this(context, null);
	}

	/**
	 * Create a new instance.
	 *
	 * @param context The application environment.
	 * @param attrs A collection of attributes.
	 */
	public ShareView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	/**
	 * Create a new instance.
	 *
	 * @param context The application environment.
	 * @param attrs A collection of attributes.
	 * @param defStyle The default style to apply to this view.
	 */
	public ShareView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mContext = context;

		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ShareView, R.attr.shareViewStyle, 0);

		int expandActivityOverflowResId = a.getResourceId(R.styleable.ShareView_sv_buttonDrawable, 0);
		int minHeight = a.getDimensionPixelSize(R.styleable.ShareView_sv_shareViewHeight, 0);
		int dividerResId = a.getResourceId(R.styleable.ShareView_sv_dividerDrawable, 0);
		mBackgroundResId = a.getResourceId(R.styleable.ShareView_sv_frameBackground, 0);
		mDisplayDefaultActivityButton = a.getBoolean(R.styleable.ShareView_sv_favoriteDisplayed, true);
		mButtonBackgroundResId = a.getResourceId(R.styleable.ShareView_sv_buttonBackground, 0);
		mPopupBackgroundResId = a.getResourceId(R.styleable.ShareView_sv_popupBackground, 0);
		mPopupDividerResId = a.getResourceId(R.styleable.ShareView_sv_popupDivider, 0);
		mPopupSelectorResId = a.getResourceId(R.styleable.ShareView_sv_popupSelector, 0);
		mPopupTextColor = a.getColorStateList(R.styleable.ShareView_sv_popupTextColor);

		a.recycle();

		int activityIconSize = context.getResources().getDimensionPixelSize(R.dimen.sv__activity_icon_size);
		mActivityIconBounds = new Rect();
		mActivityIconBounds.right = activityIconSize;
		mActivityIconBounds.bottom = activityIconSize;

		LayoutInflater inflater = LayoutInflater.from(mContext);
		inflater.inflate(R.layout.sv__share_view_impl, this, true);

		mCallbacks = new Callbacks();

		mActivityChooserContent = (IcsLinearLayout) findViewById(R.id.activity_chooser_view_content);
		mActivityChooserContent.setBackgroundResource(mBackgroundResId);
		if (dividerResId != 0) {
			mActivityChooserContent.setDividerDrawable(context.getResources().getDrawable(dividerResId));
		}
		mActivityChooserContent.setMinimumHeight(minHeight);

		mExpandActivityOverflowButton = (FrameLayout) findViewById(R.id.expand_activities_button);
		mExpandActivityOverflowButton.setBackgroundResource(mButtonBackgroundResId);
		mExpandActivityOverflowButton.setOnClickListener(mCallbacks);
		mExpandActivityOverflowButtonImage = (ImageView) mExpandActivityOverflowButton.findViewById(R.id.image);
		mExpandActivityOverflowButtonImage.setImageResource(expandActivityOverflowResId);
		mExpandActivityOverflowButtonImage.setOnClickListener(mCallbacks);

		mDefaultActivityButton = (FrameLayout) findViewById(R.id.default_activity_button);
		mDefaultActivityButton.setVisibility(mDisplayDefaultActivityButton ? View.VISIBLE : View.GONE);
		mDefaultActivityButtonImage = (ImageView) mDefaultActivityButton.findViewById(R.id.image);
		if (mDisplayDefaultActivityButton) {
			mDefaultActivityButton.setBackgroundResource(mButtonBackgroundResId);
			mDefaultActivityButton.setOnClickListener(mCallbacks);
			mDefaultActivityButton.setOnLongClickListener(mCallbacks);
			mDefaultActivityButtonImage.setOnClickListener(mCallbacks);
		}

		mAdapter = new ActivityChooserViewAdapter();
		mAdapter.registerDataSetObserver(new DataSetObserver() {
			@Override
			public void onChanged() {
				super.onChanged();
				updateAppearance();
			}
		});

		setActivityChooserModel(ActivityChooserModel.get(mContext, DEFAULT_SHARE_HISTORY_FILE_NAME));

		Resources resources = context.getResources();
		mListPopupMaxWidth = Math.max(resources.getDisplayMetrics().widthPixels / 2,
				resources.getDimensionPixelSize(R.dimen.sv__preferred_dialog_width));
	}

	/**
	 * {@inheritDoc}
	 */
	public void setActivityChooserModel(ActivityChooserModel dataModel) {
		mAdapter.setDataModel(dataModel);
		if (isShowingPopup()) {
			dismissPopup();
			showPopup();
		}
	}

	public ActivityChooserModel getActivityChooserModel() {
		return mAdapter.mDataModel;
	}

	/**
	 * Set the intent(s) for this ShareView.
	 * @param intent
	 */
	public void setShareIntent(Intent... intents) {
		if (mAdapter != null && mAdapter.mDataModel != null) {
			mAdapter.mDataModel.setIntent(intents);
		}
	}

	/**
	 * Sets the background for the button that expands the activity
	 * overflow list.
	 *
	 * <strong>Note:</strong> Clients would like to set this drawable
	 * as a clue about the action the chosen activity will perform. For
	 * example, if a share activity is to be chosen the drawable should
	 * give a clue that sharing is to be performed.
	 *
	 * @param drawable The drawable.
	 */
	public void setExpandActivityOverflowButtonDrawable(Drawable drawable) {
		mExpandActivityOverflowButtonImage.setImageDrawable(drawable);
	}

    /**
     * Sets a listener to be notified when a share target has been selected.
     * The listener can optionally decide to handle the selection and
     * not rely on the default behavior which is to launch the activity.
     * <p>
     * <strong>Note:</strong> If you choose the backing share history file
     *     you will still be notified in this callback.
     * </p>
     * @param listener The listener.
     */
	public void setOnShareTargetSelectedListener(OnShareTargetSelectedListener listener) {
		mListener = listener;
	}

	/**
	 * Sets the content description for the button that expands the activity
	 * overflow list.
	 *
	 * description as a clue about the action performed by the button.
	 * For example, if a share activity is to be chosen the content
	 * description should be something like "Share with".
	 *
	 * @param resourceId The content description resource id.
	 */
	public void setExpandActivityOverflowButtonContentDescription(int resourceId) {
		CharSequence contentDescription = mContext.getString(resourceId);
		mExpandActivityOverflowButtonImage.setContentDescription(contentDescription);
		//TODO: CheatSheet.setup(mExpandActivityOverflowButtonImage);
	}

	/**
	 * Shows the popup window with activities.
	 *
	 * @return True if the popup was shown, false if already showing.
	 */
	public boolean showPopup() {
		if (isShowingPopup() || !mIsAttachedToWindow) {
			return false;
		}
		mIsSelectingDefaultActivity = false;
		showPopupUnchecked(mInitialActivityCount);
		return true;
	}

	/**
	 * Shows the popup no matter if it was already showing.
	 *
	 * @param maxActivityCount The max number of activities to display.
	 */
	private void showPopupUnchecked(int maxActivityCount) {
		if (mAdapter.getDataModel() == null) {
			throw new IllegalStateException("No data model. Did you call #setDataModel?");
		}

		getViewTreeObserver().addOnGlobalLayoutListener(mOnGlobalLayoutListener);

		final boolean defaultActivityButtonShown =
				mDefaultActivityButton.getVisibility() == VISIBLE;

		final int activityCount = mAdapter.getActivityCount();
		final int maxActivityCountOffset = defaultActivityButtonShown ? 1 : 0;
		if (maxActivityCount != ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED
				&& activityCount > maxActivityCount + maxActivityCountOffset) {
			mAdapter.setShowFooterView(true);
			mAdapter.setMaxActivityCount(maxActivityCount - 1);
		}
		else {
			mAdapter.setShowFooterView(false);
			mAdapter.setMaxActivityCount(maxActivityCount);
		}

		IcsListPopupWindow popupWindow = getListPopupWindow();
		if (!popupWindow.isShowing()) {
			if (mIsSelectingDefaultActivity || !defaultActivityButtonShown) {
				mAdapter.setShowDefaultActivity(true, defaultActivityButtonShown);
			}
			else {
				mAdapter.setShowDefaultActivity(false, false);
			}
			final int contentWidth = Math.min(mAdapter.measureContentWidth(), mListPopupMaxWidth);
			popupWindow.setContentWidth(contentWidth);
			popupWindow.show();
			popupWindow.getListView().setContentDescription(mContext.getString(R.string.Choose_an_application));
		}
	}

	/**
	 * Dismisses the popup window with activities.
	 *
	 * @return True if dismissed, false if already dismissed.
	 */
	public boolean dismissPopup() {
		if (isShowingPopup()) {
			getListPopupWindow().dismiss();
			ViewTreeObserver viewTreeObserver = getViewTreeObserver();
			if (viewTreeObserver.isAlive()) {
				viewTreeObserver.removeGlobalOnLayoutListener(mOnGlobalLayoutListener);
			}
		}
		return true;
	}

	/**
	 * Gets whether the popup window with activities is shown.
	 *
	 * @return True if the popup is shown.
	 */
	public boolean isShowingPopup() {
		return getListPopupWindow().isShowing();
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		ActivityChooserModel dataModel = mAdapter.getDataModel();
		if (dataModel != null) {
			try {
				dataModel.registerObserver(mModelDataSetObserver);
				dataModel.notifyChanged();
			}
			catch (IllegalStateException e) {
				// Related to #557.
			}
		}
		mIsAttachedToWindow = true;
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		ActivityChooserModel dataModel = mAdapter.getDataModel();
		if (dataModel != null) {
			try {
				dataModel.unregisterObserver(mModelDataSetObserver);
			}
			catch (IllegalStateException e) {
				//Oh, well... fixes issue #557
			}
		}
		ViewTreeObserver viewTreeObserver = getViewTreeObserver();
		if (viewTreeObserver.isAlive()) {
			viewTreeObserver.removeGlobalOnLayoutListener(mOnGlobalLayoutListener);
		}
		mIsAttachedToWindow = false;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		View child = mActivityChooserContent;
		// If the default action is not visible we want to be as tall as the
		// ActionBar so if this widget is used in the latter it will look as
		// a normal action button.
		if (mDefaultActivityButton.getVisibility() != VISIBLE) {
			heightMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec),
					MeasureSpec.EXACTLY);
		}
		measureChild(child, widthMeasureSpec, heightMeasureSpec);
		setMeasuredDimension(child.getMeasuredWidth(), child.getMeasuredHeight());
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		mActivityChooserContent.layout(0, 0, right - left, bottom - top);
		if (getListPopupWindow().isShowing()) {
			showPopupUnchecked(mAdapter.getMaxActivityCount());
		}
		else {
			dismissPopup();
		}
	}

	/**
	 * Sets a listener to receive a callback when the popup is dismissed.
	 *
	 * @param listener The listener to be notified.
	 */
	public void setOnDismissListener(PopupWindow.OnDismissListener listener) {
		mOnDismissListener = listener;
	}

	/**
	 * Sets the initial count of items shown in the activities popup
	 * i.e. the items before the popup is expanded. This is an upper
	 * bound since it is not guaranteed that such number of intent
	 * handlers exist.
	 *
	 * @param itemCount The initial popup item count.
	 */
	public void setInitialActivityCount(int itemCount) {
		mInitialActivityCount = itemCount;
	}

	/**
	 * Sets a content description of the default action button. This
	 * resource should be a string taking one formatting argument and
	 * will be used for formatting the content description of the button
	 * dynamically as the default target changes. For example, a resource
	 * pointing to the string "share with %1$s" will result in a content
	 * description "share with Bluetooth" for the Bluetooth activity.
	 *
	 * @param resourceId The resource id.
	 */
	public void setDefaultActionButtonContentDescription(int resourceId) {
		mDefaultActionButtonContentDescription = resourceId;
	}

	/**
	 * Gets the list popup window which is lazily initialized.
	 *
	 * @return The popup.
	 */
	private IcsListPopupWindow getListPopupWindow() {
		if (mListPopupWindow == null) {
			mListPopupWindow = new IcsListPopupWindow(getContext());
			mListPopupWindow.setAdapter(mAdapter);
			mListPopupWindow.setAnchorView(ShareView.this);
			mListPopupWindow.setModal(true);
			mListPopupWindow.setOnItemClickListener(mCallbacks);
			mListPopupWindow.setOnDismissListener(mCallbacks);
			mListPopupWindow.setBackgroundResource(mPopupBackgroundResId);
			mListPopupWindow.setListDividerResource(mPopupDividerResId);
			mListPopupWindow.setListSelectorResource(mPopupSelectorResId);
		}
		return mListPopupWindow;
	}

	/**
	 * Updates the buttons state.
	 */
	private void updateAppearance() {
		// Expand overflow button.
		if (mAdapter.getCount() > 0) {
			mExpandActivityOverflowButton.setEnabled(true);
		}
		else {
			mExpandActivityOverflowButton.setEnabled(false);
		}
		// Default activity button.
		final int activityCount = mAdapter.getActivityCount();
		final int historySize = mAdapter.getHistorySize();
		if (activityCount > 0 && historySize > 0 && mDisplayDefaultActivityButton) {
			mDefaultActivityButton.setVisibility(VISIBLE);
			ActivityResolveInfo activity = mAdapter.getDefaultActivity();
			mDefaultActivityButtonImage.setImageDrawable(activity.icon);
			if (mDefaultActionButtonContentDescription != 0) {
				String contentDescription = mContext.getString(
						mDefaultActionButtonContentDescription, activity.label);
				mDefaultActivityButton.setContentDescription(contentDescription);
				//TODO: CheatSheet.setup(mDefaultActivityButton);
			}

			// Work-around for #415.
			mAdapter.setShowDefaultActivity(false, false);
		}
		else {
			mDefaultActivityButton.setVisibility(View.GONE);
		}
		// Activity chooser content.
		if (mDefaultActivityButton.getVisibility() == VISIBLE) {
			mActivityChooserContent.setBackgroundResource(mBackgroundResId);
		}
		else {
			mActivityChooserContent.setBackgroundResource(0);
			mActivityChooserContent.setPadding(0, 0, 0, 0);
		}
	}

	/**
	 * Interface implementation to avoid publishing them in the APIs.
	 */
	private class Callbacks implements AdapterView.OnItemClickListener, MenuItem.OnMenuItemClickListener,
			View.OnClickListener, View.OnLongClickListener, PopupWindow.OnDismissListener {

		// AdapterView#OnItemClickListener
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			ActivityChooserViewAdapter adapter = (ActivityChooserViewAdapter) parent.getAdapter();
			final int itemViewType = adapter.getItemViewType(position);
			switch (itemViewType) {
			case ActivityChooserViewAdapter.ITEM_VIEW_TYPE_FOOTER: {
				showPopupUnchecked(ActivityChooserViewAdapter.MAX_ACTIVITY_COUNT_UNLIMITED);
			}
				break;
			case ActivityChooserViewAdapter.ITEM_VIEW_TYPE_ACTIVITY: {
				dismissPopup();
				if (mIsSelectingDefaultActivity) {
					// The item at position zero is the default already.
					if (position > 0) {
						mAdapter.mDataModel.setDefaultActivity(position);
					}
				}
				else {
					// If the default target is not shown in the list, the first
					// item in the model is default action => adjust index
					position = mAdapter.getShowDefaultActivity() ? position : position + 1;
					Intent launchIntent = mAdapter.mDataModel.chooseActivity(position);
					if (launchIntent != null) {
						launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
						mContext.startActivity(launchIntent);

						if (mListener != null) {
							mListener.onShareTargetSelected(ShareView.this, launchIntent);
						}
					}
				}
			}
				break;
			default:
				throw new IllegalArgumentException();
			}
		}

		@Override
		public boolean onMenuItemClick(MenuItem item) {
			final int itemId = item.getItemId();
			Intent launchIntent = mAdapter.mDataModel.chooseActivity(itemId);
			if (launchIntent != null) {
				launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
				mContext.startActivity(launchIntent);
			}
			return true;
		}

		// View.OnClickListener
		@Override
		public void onClick(View view) {
			if (view == mDefaultActivityButton || view == mDefaultActivityButtonImage) {
				dismissPopup();
				ActivityResolveInfo defaultActivity = mAdapter.getDefaultActivity();
				final int index = mAdapter.getDataModel().getActivityIndex(defaultActivity);
				Intent launchIntent = mAdapter.getDataModel().chooseActivity(index);
				if (launchIntent != null) {
					mContext.startActivity(launchIntent);
				}
			}
			else if (view == mExpandActivityOverflowButton || view == mExpandActivityOverflowButtonImage) {
				mIsSelectingDefaultActivity = false;
				showPopupUnchecked(mInitialActivityCount);
			}
			else {
				throw new IllegalArgumentException();
			}
		}

		// OnLongClickListener#onLongClick
		@Override
		public boolean onLongClick(View view) {
			if (view == mDefaultActivityButton) {
				if (mAdapter.getCount() > 0) {
					mIsSelectingDefaultActivity = true;
					showPopupUnchecked(mInitialActivityCount);
				}
			}
			else {
				throw new IllegalArgumentException();
			}
			return true;
		}

		// PopUpWindow.OnDismissListener#onDismiss
		public void onDismiss() {
			notifyOnDismissListener();
		}

		private void notifyOnDismissListener() {
			if (mOnDismissListener != null) {
				mOnDismissListener.onDismiss();
			}
		}
	}

	private static class SetActivated {
		@TargetApi(Build.VERSION_CODES.HONEYCOMB)
		public static void invoke(View view, boolean activated) {
			if (IS_HONEYCOMB) {
				view.setActivated(activated);
			}
		}
	}

	private static final boolean IS_HONEYCOMB = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;

	/**
	 * Adapter for backing the list of activities shown in the popup.
	 */
	private class ActivityChooserViewAdapter extends BaseAdapter {

		public static final int MAX_ACTIVITY_COUNT_UNLIMITED = Integer.MAX_VALUE;

		private static final int ITEM_VIEW_TYPE_ACTIVITY = 0;

		private static final int ITEM_VIEW_TYPE_FOOTER = 1;

		private static final int ITEM_VIEW_TYPE_COUNT = 3;

		private ActivityChooserModel mDataModel;

		private int mMaxActivityCount = DEFAULT_INITIAL_ACTIVITY_COUNT;

		// Work-around for #415.
		private boolean mShowDefaultActivity = true;

		private boolean mHighlightDefaultActivity;

		private boolean mShowFooterView;

		public void setDataModel(ActivityChooserModel dataModel) {
			ActivityChooserModel oldDataModel = mAdapter.getDataModel();
			if (oldDataModel != null && isShown()) {
				try {
					oldDataModel.unregisterObserver(mModelDataSetObserver);
				}
				catch (IllegalStateException e) {
					//Oh, well... fixes issue #557
				}
			}
			mDataModel = dataModel;
			if (dataModel != null && isShown()) {
				try {
					dataModel.registerObserver(mModelDataSetObserver);
				}
				catch (IllegalStateException e) {
					// Related to #557.
				}
			}
			notifyDataSetChanged();
		}

		@Override
		public int getItemViewType(int position) {
			if (mShowFooterView && position == getCount() - 1) {
				return ITEM_VIEW_TYPE_FOOTER;
			}
			else {
				return ITEM_VIEW_TYPE_ACTIVITY;
			}
		}

		@Override
		public int getViewTypeCount() {
			return ITEM_VIEW_TYPE_COUNT;
		}

		public int getCount() {
			int count = 0;
			int activityCount = mDataModel.getActivityCount();
			if (!mShowDefaultActivity && mDataModel.getDefaultActivity() != null) {
				activityCount--;
			}
			count = Math.min(activityCount, mMaxActivityCount);
			if (mShowFooterView) {
				count++;
			}
			return count;
		}

		public Object getItem(int position) {
			final int itemViewType = getItemViewType(position);
			switch (itemViewType) {
			case ITEM_VIEW_TYPE_FOOTER:
				return null;
			case ITEM_VIEW_TYPE_ACTIVITY:
				if (!mShowDefaultActivity && mDataModel.getDefaultActivity() != null) {
					position++;
				}
				return mDataModel.getActivity(position);
			default:
				throw new IllegalArgumentException();
			}
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			final int itemViewType = getItemViewType(position);
			switch (itemViewType) {

			case ITEM_VIEW_TYPE_FOOTER:
				if (convertView == null || convertView.getId() != ITEM_VIEW_TYPE_FOOTER) {
					convertView = LayoutInflater.from(mContext).inflate(
							R.layout.sv__share_view_list_item, parent, false);
					convertView.setId(ITEM_VIEW_TYPE_FOOTER);
					TextView titleView = (TextView) convertView.findViewById(R.id.title);
					titleView.setText(mContext.getString(R.string.See_all___));
					titleView.setTextAppearance(mContext, 0);
					titleView.setTextColor(mPopupTextColor);
				}
				return convertView;

			case ITEM_VIEW_TYPE_ACTIVITY:
				if (convertView == null || convertView.getId() != R.id.list_item) {
					convertView = LayoutInflater.from(mContext).inflate(
							R.layout.sv__share_view_list_item, parent, false);
				}
				ActivityResolveInfo activity = (ActivityResolveInfo) getItem(position);

				// Set the title.
				TextView titleView = (TextView) convertView.findViewById(R.id.title);
				titleView.setText(activity.label);
				titleView.setTextColor(mPopupTextColor);

				// Set the icon
				Drawable icon = activity.icon;
				icon.setBounds(mActivityIconBounds);
				titleView.setCompoundDrawables(icon, null, null, null);

				// Highlight the default.
				if (mShowDefaultActivity && position == 0 && mHighlightDefaultActivity) {
					SetActivated.invoke(convertView, true);
				}
				else {
					SetActivated.invoke(convertView, false);
				}
				return convertView;

			default:
				throw new IllegalArgumentException();
			}
		}

		public int measureContentWidth() {
			// The user may have specified some of the target not to be shown but we
			// want to measure all of them since after expansion they should fit.
			final int oldMaxActivityCount = mMaxActivityCount;
			mMaxActivityCount = MAX_ACTIVITY_COUNT_UNLIMITED;

			int contentWidth = 0;
			View itemView = null;

			final int widthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
			final int heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
			final int count = getCount();

			for (int i = 0; i < count; i++) {
				itemView = getView(i, itemView, null);
				itemView.measure(widthMeasureSpec, heightMeasureSpec);
				contentWidth = Math.max(contentWidth, itemView.getMeasuredWidth());
			}

			mMaxActivityCount = oldMaxActivityCount;

			return contentWidth;
		}

		public void setMaxActivityCount(int maxActivityCount) {
			if (mMaxActivityCount != maxActivityCount) {
				mMaxActivityCount = maxActivityCount;
				notifyDataSetChanged();
			}
		}

		public ActivityResolveInfo getDefaultActivity() {
			return mDataModel.getDefaultActivity();
		}

		public void setShowFooterView(boolean showFooterView) {
			if (mShowFooterView != showFooterView) {
				mShowFooterView = showFooterView;
				notifyDataSetChanged();
			}
		}

		public int getActivityCount() {
			return mDataModel.getActivityCount();
		}

		public int getHistorySize() {
			return mDataModel.getHistorySize();
		}

		public int getMaxActivityCount() {
			return mMaxActivityCount;
		}

		public ActivityChooserModel getDataModel() {
			return mDataModel;
		}

		public void setShowDefaultActivity(boolean showDefaultActivity,
				boolean highlightDefaultActivity) {
			if (mShowDefaultActivity != showDefaultActivity
					|| mHighlightDefaultActivity != highlightDefaultActivity) {
				mShowDefaultActivity = showDefaultActivity;
				mHighlightDefaultActivity = highlightDefaultActivity;
				notifyDataSetChanged();
			}
		}

		public boolean getShowDefaultActivity() {
			return mShowDefaultActivity;
		}
	}
}
