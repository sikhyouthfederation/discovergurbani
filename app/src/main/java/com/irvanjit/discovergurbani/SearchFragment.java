package com.irvanjit.discovergurbani;

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.preference.PreferenceManager;
import android.support.v7.widget.SearchView;
import android.util.JsonReader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;

public class SearchFragment extends Fragment implements AdapterView.OnItemSelectedListener {

    private OnFragmentInteractionListener mListener;


    private static final String DEBUG_TAG = "HttpDebug";

    private SearchView searchView;
    private TextView resultMessage;
    private ProgressDialog loading;

    private ArrayList<HashMap<String, String>> shabadList;
    private ArrayList<String> shabadIdList;
    private ArrayList<Integer> pangtiIdList;
    private String[] shabadListKeys;
    private int[] shabadListValues;
    private boolean autoSearchEnabled;
    private boolean isAutoSearch;

    private String query;
    private String translationId;
    private String transliterationId;

    private ListView shabadsListView;
    private SharedPreferences preferences;

    // TODO: Rename and change types and number of parameters
    public static SearchFragment newInstance() {
        SearchFragment fragment = new SearchFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public SearchFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //search page setup
        query = "";
        resultMessage = (TextView) getActivity().findViewById(R.id.result);
        charMap = new GurmukhiCharMap().getMapping();

        //Setup Shabad Results list
        setupShabadsListView();

        //load preferences
        preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        autoSearchEnabled = preferences.getBoolean(SettingsActivity.KEY_PREF_ENABLE_AUTO_SEARCH, false);


        loading = new ProgressDialog(getActivity());
        setupSpinners();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.activity_search, container, false);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
//        try {
//            mListener = (OnFragmentInteractionListener) context;
//        } catch (ClassCastException e) {
//            throw new ClassCastException(context.toString()
//                    + " must implement OnFragmentInteractionListener");
//        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
//        public void onFragmentInteraction(Uri uri);
    }

    //default settings
    private int searchMode = 0;
    private final int searchTranslation = 3;
    private final int searchTransliteration = 4;
    private final int searchAng = 5;

    private HashMap<Character, Character> charMap;

    @Override
    public void onResume() {
        super.onResume();
        preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        autoSearchEnabled = preferences.getBoolean(SettingsActivity.KEY_PREF_ENABLE_AUTO_SEARCH, false);
    }


    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        boolean queryExists = !query.isEmpty();
        if (parent.getId() == R.id.transliteration_spinner) {
            String scriptName = "script_" + parent.getItemAtPosition(pos).toString();
            int strId = getResources().getIdentifier(scriptName, "string", getActivity().getPackageName());
            transliterationId = getString(strId);
            if (queryExists) {
                searchForShabad(query);
            }
        } else if (parent.getId() == R.id.translation_spinner) {
            String langName = "lang_" + parent.getItemAtPosition(pos).toString().replaceAll(" ", "_");
            int strId = getResources().getIdentifier(langName, "string", getActivity().getPackageName());
            translationId = getString(strId);
            if (queryExists) {
                searchForShabad(query);
            }
        } else if (parent.getId() == R.id.searchmode_spinner) {
            searchMode = pos;
            if (searchMode == searchAng) {
                searchView.setInputType(EditorInfo.TYPE_CLASS_NUMBER);
            } else {
                searchView.setInputType(EditorInfo.TYPE_CLASS_TEXT);
            }
        }
    }

    public void onNothingSelected(AdapterView<?> parent) {
    }

    private void setupSearchView(Menu searchActivityMenu) {
        // Get the SearchView and set the searchable configuration
        SearchManager searchManager = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
        searchView = (SearchView) searchActivityMenu.findItem(R.id.action_search).getActionView();
        // Assumes current activity is the searchable activity
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getActivity().getComponentName()));
        searchView.setIconifiedByDefault(false);
        searchView.requestFocusFromTouch();
        searchView.setOnQueryTextListener(searchQueryListener);

        //appearance
        searchView.setQueryHint(getString(R.string.search_hint_english));
        searchView.setImeOptions(searchView.getImeOptions() | EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);

        TextView searchText = (TextView) searchView.findViewById(android.support.v7.appcompat.R.id.search_src_text);
        Typeface anmolBani = Typeface.createFromAsset(getActivity().getAssets(), "fonts/AnmolUni.ttf");
        searchText.setTypeface(anmolBani);
    }

    private final SearchView.OnQueryTextListener searchQueryListener = new SearchView.OnQueryTextListener() {
        boolean queryReplaced;

        @Override
        public boolean onQueryTextChange(String newText) {
            if (searchMode != searchAng) {
                if (autoSearchEnabled) {
                    shabadList.clear();
                    resultMessage.setText("");
                    shabadsListView.requestFocus();
                    searchView.requestFocus();
                }

                query = searchView.getQuery().toString();

                if (autoSearchEnabled && query.length() > 2) {
                    resultMessage.setText("searching...");
                }
                //            int c = query.codePointAt(0);
                //            boolean queryIsGurmukhi = (c >= 0x0A00 && c <= 0x0A60);
                //            boolean queryIsLatin = query.charAt(0) < 128;

                isAutoSearch = true;
                StringBuilder gurmukhi = null;
                if (searchMode < 3) {
                    gurmukhi = new StringBuilder(query);
                    for (int i = 0; i < query.length(); i++) {
                        // Replace if a gurmukhi substitution is available
                        Character replacement = charMap.get(query.charAt(i));
                        if (replacement != null) {
                            queryReplaced = true;
                            gurmukhi.setCharAt(i, replacement);
                        }
                    }
                }
                if (queryReplaced && searchMode < 3) {
                    queryReplaced = false;
                    searchView.setQuery(gurmukhi, false);
                } else if (autoSearchEnabled && (query.length() > 2)) {
                    searchForShabad(query);
                }
            }
            return true;
        }

        @Override
        public boolean onQueryTextSubmit(String query) {
            isAutoSearch = false;
            hideKeyboard();

            if ((!(autoSearchEnabled && query.length() > 2))||(searchMode == searchAng)) {
                searchForShabad(query);
            } else {
                shabadsListView.requestFocus();
            }
            return true;
        }
    };

    private void setupSpinners() {
        Spinner translationSpinner = new AwesomeSpinner(getActivity(), getActivity().getWindow().getDecorView(),
                R.id.translation_spinner, R.array.translation_strings, 0).getAwesomeSpinner();
        Spinner transliterationSpinner = new AwesomeSpinner(getActivity(), getActivity().getWindow().getDecorView(),
                R.id.transliteration_spinner, R.array.transliteration_strings, 1).getAwesomeSpinner();
        Spinner searchModeSpinner = new AwesomeSpinner(getActivity(), getActivity().getWindow().getDecorView(),
                R.id.searchmode_spinner, R.array.search_modes, 2).getAwesomeSpinner();
    }

    private void setupLoadingDialog(ProgressDialog loading) {
        loading.setMessage("Searching");
        loading.setCancelable(true);
        loading.setCanceledOnTouchOutside(false);
        loading.setIndeterminate(false);
    }

    private void setupShabadsListView() {
        shabadList = new ArrayList<HashMap<String, String>>();
        shabadIdList = new ArrayList<String>();
        pangtiIdList = new ArrayList<Integer>();

        shabadsListView = (ListView) getActivity().findViewById(R.id.search_results);
        shabadsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                if (isAutoSearch && autoSearchEnabled) {
                    shabadsListView.requestFocus();
                    hideKeyboard();
                }

                // Get shabad metadata
                String shabadId = shabadIdList.get(position);
                int pangtiId = pangtiIdList.get(position);

                // Start shabad activity
                Intent in = new Intent(getActivity().getApplicationContext(),
                        ShabadActivity.class);
                in.putExtra(ShabadActivity.TAG_SHABAD, shabadId);
                in.putExtra(ShabadActivity.TAG_PANGTI_ID, pangtiId);
                in.putExtra(ShabadActivity.TAG_TRANSLATION, translationId);
                in.putExtra(ShabadActivity.TAG_TRANSLITERATION, transliterationId);
                in.putExtra("displayMode", 0);
                startActivity(in);
            }
        });
    }

    private void setupShabadListAdapter() {
        shabadListKeys = new String[] {ShabadActivity.TAG_PANGTI, ShabadActivity.TAG_TRANSLATION, ShabadActivity.TAG_TRANSLITERATION, ShabadActivity.TAG_META};
        shabadListValues = new int[] {R.id.pangti, R.id.translation, R.id.transliteration, R.id.meta};

        ListAdapter shabadListAdapter = new ShabadListAdapter(getActivity(), shabadList, R.layout.search_item, shabadListKeys, shabadListValues);
        shabadsListView.setAdapter(shabadListAdapter);
    }

    public class ShabadListAdapter extends SimpleAdapter {

        private final ArrayList<HashMap<String, String>> results;

        public ShabadListAdapter(Context context, ArrayList<HashMap<String, String>> data, int resource, String[] from, int[] to) {
            super(context, data, resource, from, to);
            this.results = data;
        }

        public View getView(int position, View view, ViewGroup parent) {
            Typeface anmolBani = Typeface.createFromAsset(getActivity().getAssets(), "fonts/AnmolUniBani.ttf");
            View v = view;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.search_item, null);
            }
            TextView pangti = (TextView) v.findViewById(R.id.pangti);
            TextView translation = (TextView) v.findViewById(R.id.translation);
            TextView transliteration = (TextView) v.findViewById(R.id.transliteration);
            TextView meta = (TextView) v.findViewById(R.id.meta);

            pangti.setText(results.get(position).get(ShabadActivity.TAG_PANGTI));
            pangti.setTypeface(anmolBani);
            translation.setText(results.get(position).get(ShabadActivity.TAG_TRANSLATION));
            transliteration.setText(results.get(position).get(ShabadActivity.TAG_TRANSLITERATION));
            meta.setText(results.get(position).get(ShabadActivity.TAG_META));
            return v;
        }
    }

    private void searchForShabad(String query) {
        if (isConnected()) {
            new SearchShabadTask().execute(query);
        } else {
            Toast.makeText(getActivity(), "Not connected to network", Toast.LENGTH_SHORT).show();
        }
    }

    private class SearchShabadTask extends AsyncTask<String, Void, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            shabadList.clear();
            if (!isAutoSearch) {
                setupLoadingDialog(loading);
                loading.show();
            }
        }

        @Override
        protected String doInBackground(String... urls) {
            try {
                return getData(urls[0]);
            } catch (IOException e) {
                return "No shabads found, check your query";
            }
        }
        @Override
        protected void onPostExecute(String result) {
            resultMessage.setText(result);
            setupShabadListAdapter();
            if (!isAutoSearch) {
                loading.dismiss();
                shabadsListView.requestFocus();
                resultMessage.setText("");
            }
        }
    }

    private String queryBuilder(String rawQuery) {
        String urlString;
        try {
            String query = URLEncoder.encode(rawQuery, "utf-8");
            String searchModeString = "search/"+searchMode;
            if (searchMode == searchAng) {
                searchModeString = "page";
            }
            urlString = ShabadActivity.URL_API_BASE+"/"+searchModeString+"/"+query+"/"+translationId+"/"+transliterationId;
        } catch (IOException e) {
            Log.d(DEBUG_TAG, e.toString());
            return null;
        }
        return urlString;
    }

    //HTTP Request
    private String getData(String query) throws IOException {
        InputStream is = null;
        try {
            URL url = new URL(queryBuilder(query));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(10000 /* milliseconds */);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);

            conn.connect();
            int response = conn.getResponseCode();
            Log.d(DEBUG_TAG, "The response is: " + response);
            is = conn.getInputStream();
            return parseQueryJson(is);
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    // Reads an InputStream and converts it to a String.
    private String parseQueryJson(InputStream in) throws IOException {
        shabadIdList.clear();
        pangtiIdList.clear();
        JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
        int pangti_id = -1;
        int shabad_id = -1;
        int ang = 0;
        String pangti = "Shabad";
        String author = "Author";
        String section = "Section";
        String translation = "Translation";
        String transliteration = "Transliteration";
        String meta;
        try {
            reader.beginArray();
            while (reader.hasNext()) {
                reader.beginObject();
                HashMap<String, String> shabad = new HashMap<String, String>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.equals(ShabadActivity.TAG_PANGTI_ID)) {
                        pangti_id = reader.nextInt();
                    } else if (name.equals(ShabadActivity.TAG_PANGTI)) {
                        if (searchMode == searchTranslation) {
                            translation = reader.nextString();
                        } else if (searchMode == searchTransliteration) {
                            transliteration = reader.nextString();
                        } else {
                            pangti = reader.nextString();
                        }
                    } else if (name.equals(ShabadActivity.TAG_SHABAD)) {
                        shabad_id = reader.nextInt();
                    } else if (name.equals(ShabadActivity.TAG_ANG)) {
                        ang = reader.nextInt();
                    } else if (name.equals(ShabadActivity.TAG_SECTION)) {
                        section = reader.nextString();
                    } else if (name.equals(ShabadActivity.TAG_TRANSLATION)) {
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String subName = reader.nextName();
                            if (subName.equals(ShabadActivity.TAG_PANGTI)) {
                                translation = reader.nextString();
                            } else {
                                reader.skipValue();
                            }
                        }
                        reader.endObject();
                    } else if (name.equals(ShabadActivity.TAG_TRANSLITERATION)) {
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String subName = reader.nextName();
                            if (subName.equals(ShabadActivity.TAG_PANGTI)) {
                                transliteration = reader.nextString();
                            } else {
                                reader.skipValue();
                            }
                        }
                        reader.endObject();
                    } else if (name.equals(ShabadActivity.TAG_SCRIPTURE)) {
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String subName = reader.nextName();
                            if (subName.equals(ShabadActivity.TAG_PANGTI_ID)) {
                                pangti_id = reader.nextInt();
                            } else if (subName.equals(ShabadActivity.TAG_PANGTI)) {
                                pangti = reader.nextString();
                            } else if (subName.equals(ShabadActivity.TAG_SHABAD)) {
                                shabad_id = reader.nextInt();
                            } else if (subName.equals(ShabadActivity.TAG_ANG)) {
                                ang = reader.nextInt();
                            } else if (subName.equals(ShabadActivity.TAG_SECTION)) {
                                section = reader.nextString();
                            } else if (subName.equals(ShabadActivity.TAG_AUTHOR)) {
                                reader.beginObject();
                                while (reader.hasNext()) {
                                    String secondSubName = reader.nextName();
                                    if (secondSubName.equals(ShabadActivity.TAG_AUTHOR)) {
                                        author = reader.nextString();
                                    } else {
                                        reader.skipValue();
                                    }
                                }
                                reader.endObject();
                            } else {
                                reader.skipValue();
                            }
                        }
                        reader.endObject();
                    } else if (name.equals(ShabadActivity.TAG_AUTHOR)) {
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String subName = reader.nextName();
                            if (subName.equals(ShabadActivity.TAG_AUTHOR)) {
                                author = reader.nextString();
                            } else {
                                reader.skipValue();
                            }
                        }
                        reader.endObject();
                    } else {
                        reader.skipValue();
                    }
                }
                if (!author.equals("None")) {
                    shabadIdList.add(String.valueOf(shabad_id));
                    pangtiIdList.add(pangti_id);
                    shabad.put(ShabadActivity.TAG_PANGTI, pangti);
                    shabad.put(ShabadActivity.TAG_TRANSLATION, translation);
                    shabad.put(ShabadActivity.TAG_TRANSLITERATION, transliteration);
                    meta = author+" Ji | Ang "+String.valueOf(ang)+ " | "+section;
                    shabad.put(ShabadActivity.TAG_META, meta);
                    shabadList.add(shabad);
                }
                reader.endObject();
            }
            reader.endArray();
        } finally {
            reader.close();
        }
        return "Your search returned "+String.valueOf(shabadIdList.size())+" shabad(s)";
    }

    private boolean isConnected() {
        ConnectivityManager connMgr = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    private void hideKeyboard() {
        if(getActivity().getCurrentFocus() != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(getActivity().INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), 0);
        }
    }

}
