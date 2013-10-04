Android-ShareEverywhere
=======================

This project is the tribute to my favorite Android Widget: the Share button. Share ALL THE THINGS!

## Summary

You may know about the [ShareActionProvider](http://developer.android.com/reference/android/widget/ShareActionProvider.html) that was introduced in API 14. This project extends the ShareActionProvider in a few very important ways:

 # It breaks the Widget free of the ActionBar. It can be used as a standalone View in any layout.
 # It allows
 # It can be used (with [ActionBarSherlock](http://actionbarsherlock.com/) or [ActionBarCompat](http://developer.android.com/reference/android/support/v7/app/ActionBar.html)) on versions of android prior to API 14.


## Usage

### As a View

Using this view in a layout is simple:

```xml
<com.dgmltn.shareeverywhere.ShareView
    android:id="@+id/share_view"
    android:layout_width="wrap_content"
    android:layout_height="48dp" />
```

Then give it an intent (or multiple intents) in your Activity or Fragment:

```java
    ShareView shareView = (ShareView) findViewById(R.id.share_view);
    shareView.setShareIntent(emailIntent, txtIntent);
```

### In the ActionBar

Its use in the actionbar is the same as [http://developer.android.com/reference/android/widget/ShareActionProvider.html](ShareActionProvider) 

In your `menu.xml`:

```xml
<item
    android:id="@+id/action_share"
    android:actionProviderClass="com.dgmltn.shareeverywhere.ShareActionProvider"
    android:showAsAction="always"
    android:title="Share" />
```

Then in your Activity/Fragment:

```java
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        MenuItem item = menu.findItem(R.id.action_share);
        ShareActionProvider shareActionProvider = (ShareActionProvider) item.getActionProvider();
        shareActionProvider.setShareIntent(mSharedIntents);
        return true;
    }
```

### Style


## Obtaining

Because it contains resources, Android-ShareEverywhere is a library project. Download the source (or clone it) and "Import Existing Android Code Into Workspace". Then set it as a library project and use that library in your main project.

### Requirements

 * [ActionBarSherlock](http://actionbarsherlock.com/) or [ActionBarCompat](http://developer.android.com/reference/android/support/v7/app/ActionBar.html)) if intended for Android versions prior to API 14
 
## License

    Copyright 2013 Doug Melton

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
