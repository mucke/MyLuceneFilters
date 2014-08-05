/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package at.ac.tuwien.ifs.lupu;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.compound.DictionaryCompoundWordTokenFilter;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.util.Version;

/**
 *
 * @author mihailupu
 */
public class GreedyDictionaryCompoundWordTokenFilter extends DictionaryCompoundWordTokenFilter {

  /**
   * Creates a new {@link GreedyDictionaryCompoundWordTokenFilter}
   * 
   * @param matchVersion
   *          Lucene version to enable correct Unicode 4.0 behavior in the
   *          dictionaries if Version > 3.0. See <a
   *          href="CompoundWordTokenFilterBase.html#version"
   *          >CompoundWordTokenFilterBase</a> for details.
   * @param input
   *          the {@link TokenStream} to process
   * @param dictionary
   *          the word dictionary to match against.
   */
  public GreedyDictionaryCompoundWordTokenFilter(Version matchVersion, TokenStream input, CharArraySet dictionary) {
    super(matchVersion, input, dictionary);
    if (dictionary == null) {
      throw new IllegalArgumentException("dictionary cannot be null");
    }
  }
  
  /**
   * Creates a new {@link GreedyDictionaryCompoundWordTokenFilter}. Unlike {@link DictionaryCompoundWordTokenFilter} it considers
   * onlyLongestMatch to be true and it will only return subwords of maximal size. <br/>
   * Example: "moonlight" will be returned as "moonlight" only if it is in the dictionary (not as "moonlight, light" as 
   * the DictionaryCompoundWordTokenFilter with onlyLongestMatch=true would.
   * 
   * @param matchVersion
   *          Lucene version to enable correct Unicode 4.0 behavior in the
   *          dictionaries if Version > 3.0. See <a
   *          href="CompoundWordTokenFilterBase.html#version"
   *          >CompoundWordTokenFilterBase</a> for details.
   * @param input
   *          the {@link TokenStream} to process
   * @param dictionary
   *          the word dictionary to match against.
   * @param minWordSize
   *          only words longer than this get processed
   * @param minSubwordSize
   *          only subwords longer than this get to the output stream
   * @param maxSubwordSize
   *          only subwords shorter than this get to the output stream
   */
  public GreedyDictionaryCompoundWordTokenFilter(Version matchVersion, TokenStream input, CharArraySet dictionary,
      int minWordSize, int minSubwordSize, int maxSubwordSize) {
    super(matchVersion, input, dictionary, minWordSize, minSubwordSize, maxSubwordSize,true);
    if (dictionary == null) {
      throw new IllegalArgumentException("dictionary cannot be null");
    }
  }


    /**
     * Same as {@link DictionaryCompoundWordTokenFilter#decompose()} except if it found a match, it moves the pointer to the end of it
     */
    @Override
    protected void decompose() {
        final int len = termAtt.length();
        //for (int i = 0; i <= len - this.minSubwordSize; ++i) {
        for (int i = 0; i <= len - this.minSubwordSize; ) {
            CompoundToken longestMatchToken = null;
            for (int j = this.minSubwordSize; j <= this.maxSubwordSize; ++j) {
                if (i + j > len) {
                    break;
                }
                if (dictionary.contains(termAtt.buffer(), i, j)) {
                    //we only consider the longest match
                    //if (this.onlyLongestMatch) {
                        if (longestMatchToken != null) {
                            if (longestMatchToken.txt.length() < j) {
                                longestMatchToken = new CompoundToken(i, j);
                            }
                        } else {
                            longestMatchToken = new CompoundToken(i, j);
                        }
                    //} else {
                    //    tokens.add(new CompoundToken(i, j));
                    //}
                }
            }
            //if (this.onlyLongestMatch && longestMatchToken != null) {
            if (longestMatchToken != null) {
                //only add if this is different than the entire token (i.e. do not duplicate)
                if (longestMatchToken.txt.length()!=len) tokens.add(longestMatchToken);
                i+=longestMatchToken.txt.length();
            }else{
                ++i;
            }
        }
    }
}
