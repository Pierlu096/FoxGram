package it.foxgram.android.translator;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import it.foxgram.android.http.StandardHTTPRequest;

public class GoogleAppTranslator extends BaseTranslator {

    private final List<String> targetLanguages = Arrays.asList(
            "sq", "ar", "am", "az", "ga", "et", "or", "eu", "be", "bg", "is", "pl", "bs",
            "fa", "af", "tt", "da", "de", "ru", "fr", "tl", "fi", "fy", "km", "ka", "gu",
            "kk", "ht", "ko", "ha", "nl", "ky", "gl", "ca", "cs", "kn", "co", "hr", "ku",
            "la", "lv", "lo", "lt", "lb", "rw", "ro", "mg", "mt", "mr", "ml", "ms", "mk",
            "mi", "mn", "bn", "my", "hmn", "xh", "zu", "ne", "no", "pa", "pt", "ps", "ny",
            "ja", "sv", "sm", "sr", "st", "si", "eo", "sk", "sl", "sw", "gd", "ceb", "so",
            "tg", "te", "ta", "th", "tr", "tk", "cy", "ug", "ur", "uk", "uz", "es", "iw",
            "el", "haw", "sd", "hu", "sn", "hy", "ig", "it", "yi", "hi", "su", "id", "jw",
            "en", "yo", "vi", "zh-TW", "zh-CN", "zh");

    private final String[] devices = new String[]{
            "Linux; U; Android 10; Pixel 4",
            "Linux; U; Android 10; Pixel 4 XL",
            "Linux; U; Android 10; Pixel 4a",
            "Linux; U; Android 10; Pixel 4a XL",
            "Linux; U; Android 11; Pixel 4",
            "Linux; U; Android 11; Pixel 4 XL",
            "Linux; U; Android 11; Pixel 4a",
            "Linux; U; Android 11; Pixel 4a XL",
            "Linux; U; Android 11; Pixel 5",
            "Linux; U; Android 11; Pixel 5a",
            "Linux; U; Android 12; Pixel 4",
            "Linux; U; Android 12; Pixel 4 XL",
            "Linux; U; Android 12; Pixel 4a",
            "Linux; U; Android 12; Pixel 4a XL",
            "Linux; U; Android 12; Pixel 5",
            "Linux; U; Android 12; Pixel 5a",
            "Linux; U; Android 12; Pixel 6",
            "Linux; U; Android 12; Pixel 6 Pro",
    };

    private static final class InstanceHolder {
        private static final GoogleAppTranslator instance = new GoogleAppTranslator();
    }

    static GoogleAppTranslator getInstance() {
        return InstanceHolder.instance;
    }

    @Override
    protected Result singleTranslate(Object query, String tl) throws IOException, JSONException {
        ArrayList<String> blocks = getStringBlocks((String) query, 2500);
        StringBuilder resultString = new StringBuilder();
        String resultLang = "";
        for (String block : blocks) {
            String url = "https://translate.google.com/translate_a/single?dj=1" +
                    "&q=" + URLEncoder.encode(block, StandardCharsets.UTF_8.name()) +
                    "&sl=auto" +
                    "&tl=" + tl +
                    "&ie=UTF-8&oe=UTF-8&client=at&dt=t&otf=2";
            String response = new StandardHTTPRequest(url)
                    .header("User-Agent", "GoogleTranslate/6.28.0.05.421483610 (" + devices[(int) Math.round(Math.random() * (devices.length - 1))] + ")")
                    .request();
            if (TextUtils.isEmpty(response)) {
                return null;
            }
            Result result = getResult(response);
            if (TextUtils.isEmpty(resultLang)) {
                resultLang = result.sourceLanguage;
            }
            resultString.append(buildTranslatedString(block, ((String) result.translation)));
        }
        return new Result(
                resultString.toString(),
                resultLang
        );
    }

    @Override
    public List<String> getTargetLanguages() {
        return targetLanguages;
    }

    @Override
    public String convertLanguageCode(String language, String country) {
        String languageLowerCase = language.toLowerCase();
        String code;
        if (!TextUtils.isEmpty(country)) {
            String countryUpperCase = country.toUpperCase();
            if (targetLanguages.contains(languageLowerCase + "-" + countryUpperCase)) {
                code = languageLowerCase + "-" + countryUpperCase;
            } else if (languageLowerCase.equals("zh")) {
                if (countryUpperCase.equals("DG")) {
                    code = "zh-CN";
                } else if (countryUpperCase.equals("HK")) {
                    code = "zh-TW";
                } else {
                    code = languageLowerCase;
                }
            } else {
                code = languageLowerCase;
            }
        } else {
            code = languageLowerCase;
        }
        return code;
    }

    private Result getResult(String string) throws JSONException {
        StringBuilder sb = new StringBuilder();
        JSONObject object = new JSONObject(string);
        JSONArray array = object.getJSONArray("sentences");
        for (int i = 0; i < array.length(); i++) {
            sb.append(array.getJSONObject(i).getString("trans"));
        }
        String sourceLang = null;
        try {
            sourceLang = object.getJSONObject("ld_result").getJSONArray("srclangs").getString(0);
        } catch (Exception e) {
            FileLog.e(e, false);
        }
        return new Result(sb.toString(), sourceLang);
    }
}

