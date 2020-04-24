package org.tasks.ui;

import static com.todoroo.andlib.utility.AndroidUtilities.assertNotMainThread;
import static org.tasks.LocalBroadcastManager.REFRESH;
import static org.tasks.LocalBroadcastManager.REFRESH_LIST;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.todoroo.astrid.adapter.NavigationDrawerAdapter;
import com.todoroo.astrid.api.Filter;
import com.todoroo.astrid.api.FilterListItem;
import com.todoroo.astrid.dao.TaskDao;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.List;
import javax.inject.Inject;
import org.tasks.LocalBroadcastManager;
import org.tasks.R;
import org.tasks.billing.PurchaseActivity;
import org.tasks.dialogs.NewFilterDialog;
import org.tasks.filters.FilterProvider;
import org.tasks.filters.NavigationDrawerAction;
import org.tasks.injection.FragmentComponent;
import org.tasks.injection.InjectingFragment;
import org.tasks.intents.TaskIntents;

public class NavigationDrawerFragment extends InjectingFragment {

  public static final int FRAGMENT_NAVIGATION_DRAWER = R.id.navigation_drawer;
  public static final int REQUEST_NEW_LIST = 10100;
  public static final int REQUEST_SETTINGS = 10101;
  public static final int REQUEST_PURCHASE = 10102;
  public static final int REQUEST_DONATE = 10103;
  public static final int REQUEST_NEW_PLACE = 10104;
  public static final int REQUEST_NEW_FILTER = 101015;
  private static final String FRAG_TAG_NEW_FILTER = "frag_tag_new_filter";

  private final RefreshReceiver refreshReceiver = new RefreshReceiver();
  @Inject LocalBroadcastManager localBroadcastManager;
  @Inject NavigationDrawerAdapter adapter;
  @Inject FilterProvider filterProvider;
  @Inject TaskDao taskDao;
  /** A pointer to the current callbacks instance (the Activity). */
  private DrawerLayout mDrawerLayout;

  private RecyclerView recyclerView;
  private View mFragmentContainerView;
  private CompositeDisposable disposables;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (savedInstanceState != null) {
      adapter.restore(savedInstanceState);
    }
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    getActivity().setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);

    setUpList();
  }

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View layout = inflater.inflate(R.layout.fragment_navigation_drawer, container, false);
    recyclerView = layout.findViewById(R.id.recycler_view);
    ((ScrimInsetsFrameLayout) layout.findViewById(R.id.scrim_layout))
        .setOnInsetsCallback(insets -> recyclerView.setPadding(0, insets.top, 0, 0));
    return layout;
  }

  private void setUpList() {
    adapter.setOnClick(this::onFilterItemSelected);
    recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    recyclerView.setAdapter(adapter);
  }

  private void onFilterItemSelected(@Nullable FilterListItem item) {
    mDrawerLayout.addDrawerListener(
        new SimpleDrawerListener() {
          @Override
          public void onDrawerClosed(View drawerView) {
            mDrawerLayout.removeDrawerListener(this);
            if (item instanceof Filter) {
              FragmentActivity activity = getActivity();
              if (activity != null) {
                activity.startActivity(TaskIntents.getTaskListIntent(activity, (Filter) item));
              }
            } else if (item instanceof NavigationDrawerAction) {
              NavigationDrawerAction action = (NavigationDrawerAction) item;
              if (action.requestCode == REQUEST_PURCHASE) {
                startActivity(new Intent(getContext(), PurchaseActivity.class));
              } else if (action.requestCode == REQUEST_DONATE) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://tasks.org/donate")));
              } else if (action.requestCode == REQUEST_NEW_FILTER) {
                NewFilterDialog.Companion.newFilterDialog()
                    .show(getParentFragmentManager(), FRAG_TAG_NEW_FILTER);
              } else {
                getActivity().startActivityForResult(action.intent, action.requestCode);
              }
            }
          }
        });
    if (item instanceof Filter) {
      new ViewModelProvider(getActivity()).get(TaskListViewModel.class).setFilter((Filter) item);
    }
    close();
  }

  public boolean isDrawerOpen() {
    return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(mFragmentContainerView);
  }

  /**
   * Users of this fragment must call this method to set up the navigation drawer interactions.
   *
   * @param drawerLayout The DrawerLayout containing this fragment's UI.
   */
  public void setUp(DrawerLayout drawerLayout) {
    mFragmentContainerView = getActivity().findViewById(FRAGMENT_NAVIGATION_DRAWER);
    mDrawerLayout = drawerLayout;
  }

  public void setSelected(Filter selected) {
    adapter.setSelected(selected);
  }

  @Override
  public void onPause() {
    super.onPause();

    localBroadcastManager.unregisterReceiver(refreshReceiver);
  }

  @Override
  public void onStart() {
    super.onStart();

    disposables = new CompositeDisposable();
  }

  @Override
  public void onStop() {
    super.onStop();

    disposables.dispose();
  }

  @Override
  protected void inject(FragmentComponent component) {
    component.inject(this);
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

    adapter.save(outState);
  }

  public void closeDrawer() {
    if (mDrawerLayout != null) {
      mDrawerLayout.setDrawerListener(null);
      close();
    }
  }

  private void close() {
    mDrawerLayout.closeDrawer(mFragmentContainerView);
  }

  public void openDrawer() {
    if (mDrawerLayout != null) {
      mDrawerLayout.openDrawer(mFragmentContainerView);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    localBroadcastManager.registerRefreshListReceiver(refreshReceiver);

    disposables.add(updateFilters());
  }

  private Disposable updateFilters() {
    return Single.fromCallable(() -> filterProvider.getItems(true))
        .map(this::refreshFilterCount)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(adapter::submitList);
  }

  private List<FilterListItem> refreshFilterCount(List<FilterListItem> items) {
    assertNotMainThread();

    for (FilterListItem item : items) {
      if (item instanceof Filter && item.count == -1) {
        item.count = taskDao.count((Filter) item);
      }
    }
    return items;
  }

  private class RefreshReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (intent == null) {
        return;
      }
      String action = intent.getAction();
      if (REFRESH.equals(action) || REFRESH_LIST.equals(action)) {
        disposables.add(updateFilters());
      }
    }
  }
}
