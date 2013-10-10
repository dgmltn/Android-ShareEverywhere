package com.dgmltn.shareeverywhere.demo;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuInflater;

import com.dgmltn.shareeverywhere.ShareView;
import com.dgmltn.sharewhere.R;

public class MainActivity extends Activity {

	// Instance variables for this activity
	private Intent[] mSharedIntents;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Setup the intent to share
		mSharedIntents = new Intent[] { getEmailIntent(), getTxtIntent(), getImageIntent() };

		// Since all the ShareView's in this activity share the same
		// ActivityChooserModel, setShareIntent() just once, on one of them
		// is sufficient to initialize them all.
		ShareView shareView = (ShareView) findViewById(R.id.share_view);
		shareView.setShareIntent(mSharedIntents);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return true;
	}

	private Intent getEmailIntent() {
		String to = "foo@bar.com";
		String subject = "yo dude";
		String body = "Here's an email body";

		final Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("message/rfc822");
		String[] toArr = new String[] { to };
		intent.putExtra(Intent.EXTRA_EMAIL, toArr);
		intent.putExtra(Intent.EXTRA_SUBJECT, subject);
		intent.putExtra(Intent.EXTRA_TEXT, body);
		return intent;
	}

	private Intent getTxtIntent() {
		String subject = "share subject";
		String text = "here's some share text";

		final Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("text/plain");
		intent.putExtra(Intent.EXTRA_SUBJECT, subject);
		intent.putExtra(Intent.EXTRA_TEXT, text);
		return intent;
	}

	private Intent getImageIntent() {
		Uri imageUri = getRandomImageUri();

		Intent shareIntent = new Intent();
		shareIntent.setAction(Intent.ACTION_SEND);
		shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
		shareIntent.setType("image/jpeg");
		return shareIntent;
	}

	// Get the uri to a random image in the photo gallery
	private Uri getRandomImageUri() {
		Uri mediaUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
		String[] projection = { MediaStore.Images.Media._ID };
		Cursor cursor = null;
		try {
			cursor = getContentResolver().query(mediaUri, projection, null, null, null);
			cursor.moveToPosition((int) (Math.random() * cursor.getCount()));
			String id = cursor.getString(0);
			Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
			return uri;
		}
		catch (Exception e) {
			return null;
		}
		finally {
			if (cursor != null && !cursor.isClosed()) {
				cursor.close();
			}
		}
	}
}
