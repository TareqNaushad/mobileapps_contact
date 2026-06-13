package com.example.fuzzycontacts;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_CONTACTS = 1;

    static final class Contact {
        final String name;
        final String number;
        Contact(String name, String number) { this.name = name; this.number = number; }
    }

    private final List<Contact> all = new ArrayList<>();   // every phone contact
    private final List<Contact> shown = new ArrayList<>(); // currently displayed (parallel to adapter)

    private EditText search;
    private TextView status;
    private ListView list;
    private ArrayAdapter<String> adapter;

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
            @Override public void afterTextChanged(Editable e) { update(e.toString()); }
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
                    if (num != null) all.add(new Contact(name, num));
                }
            }
        } catch (Exception ex) {
            Toast.makeText(this, "Failed to read contacts: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
        update(search.getText().toString());
    }

    /** Rebuild the visible list for the given query. Empty query shows everything. */
    private void update(String query) {
        shown.clear();
        String q = query == null ? "" : query.trim();

        if (q.isEmpty()) {
            shown.addAll(all);
        } else {
            List<int[]> idxScore = new ArrayList<>(); // {index, score}
            for (int i = 0; i < all.size(); i++) {
                int sc = Phonetic.matchScore(all.get(i).name, q);
                if (sc > 0) idxScore.add(new int[]{i, sc});
            }
            Collections.sort(idxScore, new Comparator<int[]>() {
                @Override public int compare(int[] a, int[] b) {
                    if (b[1] != a[1]) return b[1] - a[1];            // higher score first
                    return all.get(a[0]).name.compareToIgnoreCase(all.get(b[0]).name);
                }
            });
            for (int[] is : idxScore) shown.add(all.get(is[0]));
        }

        List<String> display = new ArrayList<>(shown.size());
        for (Contact c : shown) display.add(c.name + "\n" + c.number);

        adapter.clear();
        adapter.addAll(display);
        adapter.notifyDataSetChanged();

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
}
