package com.example.fuzzycontacts;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQ_CONTACTS = 1;
    private static final long DEBOUNCE_MS = 200;   // wait after last keystroke before searching

    static final class Contact {
        final String name;
        final String number;
        final String normName;      // pre-normalized once at load
        final String[] normTokens;  // pre-tokenized once at load
        Contact(String name, String number) {
            this.name = name;
            this.number = number;
            this.normName = Phonetic.normalize(name);
            this.normTokens = this.normName.isEmpty() ? new String[0] : this.normName.split(" ");
        }
    }

    private final List<Contact> all = new ArrayList<>();   // every phone contact
    private final List<Contact> shown = new ArrayList<>(); // currently displayed (parallel to adapter)

    private EditText search;
    private TextView status;
    private ListView list;
    private ArrayAdapter<String> adapter;

    // Debounce + background search
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService searchExec = Executors.newSingleThreadExecutor();
    private Runnable pendingSearch;
    private volatile long searchSeq = 0;   // discards results from stale (superseded) searches

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        search = findViewById(R.id.search);
        status = findViewById(R.id.status);
        list = findViewById(R.id.list);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        list.setAdapter(adapter);

        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < shown.size()) {
                dial(shown.get(position).number);
            }
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) { onQueryChanged(e.toString()); }
        });

        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            loadContacts();
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadContacts();
            } else {
                status.setText("Contacts permission denied — cannot search.");
            }
        }
    }

    private void loadContacts() {
        all.clear();
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] cols = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        try (Cursor c = getContentResolver().query(uri, cols, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")) {
            if (c != null) {
                int iName = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int iNum = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                while (c.moveToNext()) {
                    String name = iName >= 0 ? c.getString(iName) : null;
                    String num = iNum >= 0 ? c.getString(iNum) : null;
                    if (name == null) name = "(no name)";
                    if (num != null) all.add(new Contact(name, num));  // normalization happens here, once
                }
            }
        } catch (Exception ex) {
            Toast.makeText(this, "Failed to read contacts: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
        runSearch(search.getText().toString());   // initial population (shows all)
    }

    /** Called on every keystroke — just (re)schedules a debounced search. */
    private void onQueryChanged(String query) {
        if (pendingSearch != null) ui.removeCallbacks(pendingSearch);
        final String q = query;
        pendingSearch = () -> runSearch(q);
        ui.postDelayed(pendingSearch, DEBOUNCE_MS);
    }

    /** Runs the (potentially expensive) match on a background thread, posts results back. */
    private void runSearch(final String query) {
        final long mySeq = ++searchSeq;
        searchExec.execute(() -> {
            final List<Contact> result = compute(query);
            final List<String> display = new ArrayList<>(result.size());
            for (Contact c : result) display.add(c.name + "\n" + c.number);

            ui.post(() -> {
                if (mySeq != searchSeq) return;   // a newer search already ran; drop stale result
                shown.clear();
                shown.addAll(result);
                adapter.clear();
                adapter.addAll(display);
                adapter.notifyDataSetChanged();
                updateStatus(query.trim());
            });
        });
    }

    /** Pure matching/sorting — runs off the UI thread. Empty query returns everything. */
    private List<Contact> compute(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return new ArrayList<>(all);

        String nq = Phonetic.normalize(q);          // normalize the query ONCE
        if (nq.isEmpty()) return new ArrayList<>(all);
        String[] qTokens = nq.split(" ");

        final List<Contact> matches = new ArrayList<>();
        final List<Integer> scores = new ArrayList<>();
        for (Contact c : all) {
            int sc = Phonetic.matchScorePre(c.normName, c.normTokens, nq, qTokens);
            if (sc > 0) { matches.add(c); scores.add(sc); }
        }

        Integer[] order = new Integer[matches.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, new Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) {
                int sa = scores.get(a), sb = scores.get(b);
                if (sb != sa) return sb - sa;                                   // higher score first
                return matches.get(a).name.compareToIgnoreCase(matches.get(b).name);
            }
        });

        List<Contact> out = new ArrayList<>(matches.size());
        for (int idx : order) out.add(matches.get(idx));
        return out;
    }

    private void updateStatus(String q) {
        if (all.isEmpty()) {
            status.setText("No contacts found on this phone.");
        } else if (q.isEmpty()) {
            status.setText(all.size() + " contacts — start typing to fuzzy-search. Tap a result to call.");
        } else {
            status.setText(shown.size() + " match(es) for \"" + q + "\". Tap to call.");
        }
    }

    /** Open the dialer pre-filled with the number (user taps call / picks SIM). */
    private void dial(String number) {
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)));
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, "No dialer app found.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchExec.shutdownNow();
    }
}
