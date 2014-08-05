/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package at.ac.tuwien.ifs.lupu;

import java.io.IOException;
import java.util.Map;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.compound.CompoundWordTokenFilterBase;
import org.apache.lucene.analysis.compound.DictionaryCompoundWordTokenFilter;
import org.apache.lucene.analysis.compound.DictionaryCompoundWordTokenFilterFactory;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.analysis.util.ResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoaderAware;
import org.apache.lucene.analysis.util.TokenFilterFactory;

/** 
 * Factory for {@link GreedyDictionaryCompoundWordTokenFilter}. 
 * <pre class="prettyprint">
 * &lt;fieldType name="text_dictcomp" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="at.ac.tuwien.ifs.lupu.GreedyDictionaryCompoundWordTokenFilterFactory" dictionary="dictionary.txt"
 *         minWordSize="5" minSubwordSize="2" maxSubwordSize="15"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 */
public class GreedyDictionaryCompoundWordTokenFilterFactory extends TokenFilterFactory implements ResourceLoaderAware  {

    private CharArraySet dictionary;
    private final String dictFile;
    private final int minWordSize;
    private final int minSubwordSize;
    private final int maxSubwordSize;

    public GreedyDictionaryCompoundWordTokenFilterFactory(Map<String, String> args) {
        super(args);
        dictFile = require(args, "dictionary");
        minWordSize = getInt(args, "minWordSize", CompoundWordTokenFilterBase.DEFAULT_MIN_WORD_SIZE);
        minSubwordSize = getInt(args, "minSubwordSize", CompoundWordTokenFilterBase.DEFAULT_MIN_SUBWORD_SIZE);
        maxSubwordSize = getInt(args, "maxSubwordSize", CompoundWordTokenFilterBase.DEFAULT_MAX_SUBWORD_SIZE);
        if (!args.isEmpty()) {
            throw new IllegalArgumentException("Unknown parameters: " + args);
        }
    }

    @Override
    public void inform(ResourceLoader loader) throws IOException {
        dictionary = super.getWordSet(loader, dictFile, false);
    }

    @Override
    public TokenStream create(TokenStream input) {
        // if the dictionary is null, it means it was empty
        return dictionary == null ? input : new GreedyDictionaryCompoundWordTokenFilter(luceneMatchVersion, input, dictionary, minWordSize, minSubwordSize, maxSubwordSize);
    }
}
