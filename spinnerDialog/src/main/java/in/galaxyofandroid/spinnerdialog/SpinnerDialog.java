package in.galaxyofandroid.spinnerdialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Md Farhan Raja on 2/23/2017
 */

public final class SpinnerDialog<E extends Parcelable> extends DialogFragment implements LoaderManager.LoaderCallbacks<List<E>> {

    public static <E extends Parcelable> SpinnerDialog<E> create(String title, ItemManager<E> itemManager) {
        SpinnerDialog<E> dialog = new SpinnerDialog<>();

        Bundle args = new Bundle(2);
        args.putString("title", required(title, "title"));
        args.putParcelable("item manager", required(itemManager, "item manager"));
        dialog.setArguments(args);

        return dialog;
    }

    private String filter;
    private SpinnerDialogAdapter<E> adapter;
    private View progress;

    public SpinnerDialog withWindowAnimations(@StyleRes int windowAnimations) {
        getArguments().putInt("animations", windowAnimations);
        return this;
    }

    public void show(FragmentManager manager, Fragment caller, int requestCode) {
        setTargetFragment(required(caller, "caller fragment"), requestCode);
        show(required(manager, "fragment manager"), null);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(0, null, this);
    }

    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final String title;
        final ItemManager<E> itemManager;
        final int windowAnimations;
        {
            Bundle args = getArguments();
            title = required(args.getString("title"), "title");
            itemManager = required(args.<ItemManager<E>>getParcelable("item manager"), "item manager");
            windowAnimations = args.getInt("animations", -1);
        }

        Activity activity = getActivity();
        View v = activity.getLayoutInflater().inflate(R.layout.dialog_layout, null, false);

        final RecyclerView recyclerView = (RecyclerView) v.findViewById(R.id.list);
        final EditText searchBox = (EditText) v.findViewById(R.id.searchBox);
        adapter = new SpinnerDialogAdapter<>(getActivity(), itemManager);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        progress = v.findViewById(R.id.progress);
        final AlertDialog alertDialog =
                new AlertDialog.Builder(activity)
                        .setTitle(title)
                        .setView(v)
                        .setNegativeButton(android.R.string.cancel, null)
                        .create();

        if (windowAnimations > 0) {
            alertDialog.getWindow().getAttributes().windowAnimations = windowAnimations;
        }

        adapter.setListener(new SpinnerDialogAdapter.OnItemClickListener<E>() {
            @Override public void onItemClick(E e) {
                Intent data = new Intent();
                data.putExtra("item", e);
                getTargetFragment().onActivityResult(getTargetRequestCode(), Activity.RESULT_OK, data);
                alertDialog.dismiss();
            }
            @Override public void onItemBound(int position) {
                if (((PagedLoader) getLoaderManager().getLoader(0)).onItemBound(position)) {
                    progress.setVisibility(View.VISIBLE);
                }
            }
        });

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            @Override public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            @Override public void afterTextChanged(Editable editable) {
                progress.setVisibility(View.VISIBLE);
                filter = searchBox.getText().toString();
                getLoaderManager().restartLoader(0, null, SpinnerDialog.this);
            }
        });
        return alertDialog;
    }

    private static <T> T required(T t, String tag) {
        if (t == null) throw new NullPointerException(tag + " is required");
        return t;
    }

    @Override
    public Loader<List<E>> onCreateLoader(int id, Bundle args) {
        return createLoader(getActivity(), filter, getArguments().<ItemManager<E>>getParcelable("item manager"));
    }

    private static <E extends Parcelable> Loader<List<E>> createLoader(
            Context context, final @Nullable String filter, final ItemManager<E> manager) {
        return new PagedLoader<>(context, filter, manager);
    }

    @Override
    public void onLoadFinished(Loader<List<E>> loader, List<E> data) {
        adapter.setData(data);
        progress.setVisibility(View.GONE);
        // todo: empty message
    }

    @Override
    public void onLoaderReset(Loader<List<E>> loader) {
    }

    private static final class PagedLoader<E extends Parcelable> extends AsyncTaskLoader<List<E>> {

        private final String filter;
        private final ItemManager<E> itemManager;

        PagedLoader(Context context, String filter, ItemManager<E> itemManager) {
            super(context);
            this.filter = filter;
            this.itemManager = itemManager;
        }

        private List<E> list = Collections.emptyList();
        private volatile boolean loading = false;

        @Override protected void onStartLoading() {
            int total = itemManager.getTotal(filter);
            if (list.isEmpty() && total != 0 || total > list.size() && takeContentChanged())
                forceLoad(); // total size is unknown or greater than available size
            else
                deliverResult(list); // we've got it all
        }

        @Override public List<E> loadInBackground() {
            List<E> loaded = itemManager.load(filter, list.size());
            List<E> aList = new ArrayList<>(list.size() + loaded.size());
            aList.addAll(list);
            aList.addAll(loaded);
            loading = false;
            return list = aList; // we can't just add, Loader won't deliver the same object.
        }

        boolean onItemBound(int position) {
            int size = list.size();
            if (position > size - 3 && !loading) {
                int total = itemManager.getTotal(filter);
                if (total < 0 || total > size) {
                    loading = true;
                    onContentChanged();
                    return true;
                }
            }
            return false;
        }
    }
}
