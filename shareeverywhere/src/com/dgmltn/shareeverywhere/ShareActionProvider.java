package com.dgmltn.shareeverywhere;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.ActionProvider;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;

public class ShareActionProvider extends ActionProvider {

	/**
	 * Context for accessing resources.
	 */
	private final Context mContext;

	/**
	 * The underlying ShareView, responsible for most of the data manipulation.
	 */
	private ShareView mShareView;

	public ShareActionProvider(Context context) {
		super(context);
		mContext = context;
		mShareView = (ShareView) LayoutInflater.from(context).inflate(R.layout.sv__share_view, null);
	}

	@Override
	public View onCreateActionView() {
		return mShareView;
	}

	@Override
	public boolean hasSubMenu() {
		return true;
	}

	@Override
	public void onPrepareSubMenu(SubMenu subMenu) {
		super.onPrepareSubMenu(subMenu);
		// Clear since the order of items may change.
		subMenu.clear();

		ActivityChooserModel dataModel = mShareView.getActivityChooserModel();
		PackageManager packageManager = mContext.getPackageManager();

		final int expandedActivityCount = dataModel.getActivityCount();
		final int collapsedActivityCount = Math.min(expandedActivityCount,
				ShareView.DEFAULT_INITIAL_ACTIVITY_COUNT);

		// Populate the sub-menu with a sub set of the activities.
		for (int i = 0; i < collapsedActivityCount; i++) {
			ResolveInfo activity = dataModel.getActivity(i);
			subMenu.add(0, i, i, activity.loadLabel(packageManager))
					.setIcon(activity.loadIcon(packageManager))
					.setOnMenuItemClickListener(mShareView.mCallbacks);
		}

		if (collapsedActivityCount < expandedActivityCount) {
			// Add a sub-menu for showing all activities as a list item.
			SubMenu expandedSubMenu = subMenu.addSubMenu(Menu.NONE, collapsedActivityCount,
					collapsedActivityCount, mContext.getString(R.string.See_all___));
			for (int i = 0; i < expandedActivityCount; i++) {
				ResolveInfo activity = dataModel.getActivity(i);
				expandedSubMenu.add(0, i, i, activity.loadLabel(packageManager))
						.setIcon(activity.loadIcon(packageManager))
						.setOnMenuItemClickListener(mShareView.mCallbacks);
			}
		}
	}

	/**
	 * Set the intent(s) for this ShareActionProvider.
	 * @param intent
	 */
	public void setIntent(Intent... intents) {
		mShareView.setIntent(intents);
	}
}
