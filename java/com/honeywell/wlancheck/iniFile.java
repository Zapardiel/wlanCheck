package com.honeywell.wlancheck;

/**
 * Created by E438447 on 3/16/2018.
 */

import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class iniFile {

    private Pattern _section = Pattern.compile("\\s*\\[([^]]*)\\]\\s*");
    private Pattern _keyValue = Pattern.compile("\\s*([^=]*)=(.*)");
    private Map<String,
            Map<String,
                    String>> _entries = new HashMap<>();

    public iniFile(String path) throws IOException {
        load(path);
    }

    public void load(String path) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            String section = null;
            while ((line = br.readLine()) != null) {
                Matcher m = _section.matcher(line);
                if (m.matches()) {
                    section = m.group(1).trim();
                } else if (section != null) {
                    m = _keyValue.matcher(line);
                    if (m.matches()) {
                        String key = m.group(1).trim();
                        String value = m.group(2).trim();
                        Map<String, String> kv = _entries.get(section);
                        if (kv == null) {
                            _entries.put(section, kv = new HashMap<>());
                        }
                        kv.put(key, value);
                    }
                }
            }
        }
    }

    public String getString(String section, String key, String defaultvalue) {
        try {
            Map<String, String> kv = _entries.get(section);
            if (kv == null) {
                return defaultvalue;
            }
            return kv.get(key);
        } catch (NumberFormatException ex) {
            Log.e("BatLog", "This section doesn't exists: " + section);
        }
        return defaultvalue;
    }

    public int getInt(String section, String key, int defaultvalue) {
        try {
            Map<String, String> kv = _entries.get(section);
            if (kv == null) {
                return defaultvalue;
            }
            return Integer.parseInt(kv.get(key));
        } catch (NumberFormatException ex) {
            Log.e("BatLog", "This section doesn't exists: " + section);
        }
        return defaultvalue;
    }

    public float getFloat(String section, String key, float defaultvalue) {
        try {
            Map<String, String> kv = _entries.get(section);
            if (kv == null) {
                return defaultvalue;
            }
            return Float.parseFloat(kv.get(key));
        } catch (NumberFormatException ex) {
            Log.e("BatLog", "This section doesn't exists: " + section);
        }
        return defaultvalue;
    }

    public boolean getBoolean(String section, String key, boolean defaultvalue) {
        try {
            Map<String, String> kv = _entries.get(section);
            if (kv == null) {
                return defaultvalue;
            }
            return Boolean.parseBoolean(kv.get(key));
        } catch (NumberFormatException ex) {
            Log.e("BatLog", "This section doesn't exists: " + section);
        }
        return defaultvalue;
    }

    public double getDouble(String section,String key,double defaultvalue){
        Map<String, String>kv=_entries.get(section);
        if(kv==null){
            return defaultvalue;
        }
        return Double.parseDouble(kv.get(key));
    }
}
