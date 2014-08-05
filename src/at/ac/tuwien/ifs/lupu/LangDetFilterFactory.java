/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package at.ac.tuwien.ifs.lupu;

import com.cybozu.labs.langdetect.Detector;
import com.cybozu.labs.langdetect.DetectorFactory;
import com.cybozu.labs.langdetect.LangDetectException;
import com.cybozu.labs.langdetect.Language;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.stream.Collectors.toList;
import org.apache.commons.io.IOUtils;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.ResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoaderAware;
import org.apache.lucene.analysis.util.TokenFilterFactory;

/**
 *
 * @author mihailupu
 */
public class LangDetFilterFactory extends TokenFilterFactory implements ResourceLoaderAware {

    private final String languageFiles;
    private Set<Language> languages;
    private static final Logger LOG = Logger.getLogger(LangDetFilterFactory.class.getName());
    protected Detector detector;
    private final int windowWidth;

    public LangDetFilterFactory(Map<String, String> args) {
        super(args);
        languageFiles = require(args, "languages");
        windowWidth = Integer.parseInt(require(args, "windowWidth"));
        if (!args.isEmpty()) {
            throw new IllegalArgumentException("Unknown parameters: " + args);
        }
        try {
            loadData();

        } catch (LangDetectException ex) {
            Logger.getLogger(LangDetFilterFactory.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    @Override
    public void inform(ResourceLoader loader) throws IOException {
        try {
            LOG.log(Level.ALL, "in inform");
            List<String> files = splitFileNames(languageFiles);
            if (files.size() > 0) {
                languages = new HashSet<>();
                for (String file : files) {
                    List<String> lines = getLines(loader, file.trim());
                    System.out.println(lines);
                    List<Language> typesLines = lines.stream()
                            .map(line -> readLanguage(line))
                            .collect(toList());
                    languages.addAll(typesLines);
                    LOG.log(Level.ALL, "languages:{0}", languages.size());
                }
            }
            HashMap priorMap = new HashMap();
            detector = DetectorFactory.create();
            languages.stream().forEach((language) -> {
                priorMap.put(language.lang, language.prob);
            });
            LOG.log(Level.ALL, "priorMap size:{0}", priorMap.size());
            detector.setPriorMap(priorMap);
        } catch (LangDetectException ex) {
            Logger.getLogger(LangDetFilterFactory.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public Language readLanguage(String line) {
        StringTokenizer st = new StringTokenizer(line);
        Language l = new Language(st.nextToken(), Double.parseDouble(st.nextToken()));
        return l;
    }
    // DetectorFactory is totally global, so we only want to do this once... ever!!!
    static boolean loaded = false;

    // profiles we will load from classpath
    static final String profileLanguages[] = {
        "af", "ar", "bg", "bn", "cs", "da", "de", "el", "en", "es", "et", "fa", "fi", "fr", "gu",
        "he", "hi", "hr", "hu", "id", "it", "ja", "kn", "ko", "lt", "lv", "mk", "ml", "mr", "ne",
        "nl", "no", "pa", "pl", "pt", "ro", "ru", "sk", "sl", "so", "sq", "sv", "sw", "ta", "te",
        "th", "tl", "tr", "uk", "ur", "vi", "zh-cn", "zh-tw"
    };

    public static synchronized void loadData() throws LangDetectException {
        LOG.log(Level.ALL, "in loadData");
        if (loaded) {
            return;
        }
        loaded = true;
        List<String> profileData = new ArrayList<>();
        Charset encoding = Charset.forName("UTF-8");
        for (String language : profileLanguages) {
            LOG.log(Level.ALL, "langdetect-profiles/{0}", language);
            ClassLoader loader = Thread.currentThread().getContextClassLoader();

            InputStream stream = loader.getResourceAsStream("langdetect-profiles/" + language);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, encoding))) {
                profileData.add(new String(IOUtils.toCharArray(reader)));
            } catch (IOException ex) {
                Logger.getLogger(LangDetFilterFactory.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        DetectorFactory.loadProfile(profileData);
        DetectorFactory.setSeed(0);
    }

    public Set<Language> getLanguages() {
        return languages;
    }

    @Override
    public TokenStream create(TokenStream input) {
        @SuppressWarnings("deprecation")
        final TokenStream filter;

        LOG.log(Level.ALL, "languages in create:{0}", languages.size());

        try {
            filter = new LangDetFilter(input, detector,windowWidth);
            return filter;
        } catch (LangDetectException ex) {
            LOG.log(Level.SEVERE, "Exception in LangDetFilterFactory.create", ex);
        }
        

        return null;
    }
}
