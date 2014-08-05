/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package at.ac.tuwien.ifs.lupu;

import com.cybozu.labs.langdetect.Detector;
import com.cybozu.labs.langdetect.LangDetectException;
import com.cybozu.labs.langdetect.Language;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.util.AttributeSource;

/**
 *
 * @author mihailupu
 */
public final class LangDetFilter extends TokenFilter {// FilteringTokenFilter {

    private static final Logger LOG = Logger.getLogger(LangDetFilter.class.getName());

    private final TypeAttribute typeAttribute = addAttribute(TypeAttribute.class);
    private final CharTermAttribute termAttribute = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute posIncAtt = addAttribute(PositionIncrementAttribute.class);
    private final PositionLengthAttribute posLenAtt = addAttribute(PositionLengthAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);

    private final Detector detector;

    private final LangDetFilterBuffer buffer;

    private boolean firstLoad;
    private boolean originalStreamFinished = false;
    private boolean otherToken = false;
    private boolean frozenBuffer = false;
    private boolean thereWillBeMore = false;

    private final ConcurrentLinkedQueue<AttributeSource> setAsside = new ConcurrentLinkedQueue<>();

    /**
     * Holds all state for a single position; subclass this to record other
     * state at each position.
     */
    protected static class LangDetFilterBuffer {

        //the desired size of the buffer. 
        int size;

        /**
         * Constructor
         *
         * @param width the basis for calculating the size (size=2*width+1)
         */
        public LangDetFilterBuffer(int width) {
            this.size = 2 * width + 1;
        }

        // Buffered input tokens at this position:
        public final List<AttributeSource> inputTokens = new ArrayList<>();

        /**
         * Add a token to the buffer. Pushes everything else to the left (i.e.
         * removes the first element if needed)
         *
         * @param token
         */
        public void add(AttributeSource token) {
            if (token != null) {
                // the last position will have nothing to add, so it will send null
                inputTokens.add(token);
            }
            //even if we had null, we still need to delete the first element if we have 3, because otherwise we would be using the preceding two terms
            if (inputTokens.size() > size) {
                inputTokens.remove(0);
            }
        }

        /**
         * clears the buffer.
         */
        public void reset() {
            inputTokens.clear();
        }

        /**
         * The sequence of characters of all tokens in the buffer
         *
         * @return a string containing the text of all tokens in the buffer
         */
        public String getCharacters() {
            String result;
            result = inputTokens.stream()
                    .map((AttributeSource inputToken)
                            -> inputToken.getAttribute(CharTermAttribute.class).toString())
                    .reduce("", String::concat);
            return result;
        }
    }

    /**
     * Create a new {@link LangDetFilter}.
     *
     * @param input the {@link TokenStream} to consume
     * @param detector the loaded language detector
     * @param windowWidth the number of tokens left and right to take into
     * account.
     * @throws com.cybozu.labs.langdetect.LangDetectException
     */
    public LangDetFilter(TokenStream input, Detector detector, int windowWidth) throws LangDetectException {
        super(input);
        this.detector = detector;
        buffer = new LangDetFilterBuffer(windowWidth);
    }

    /**
     * Calls the Detector's get detect method
     *
     * @param text the text for which to detect the language
     * @return the language identifier
     * @throws LangDetectException
     */
    public String detectLanguage(String text) throws LangDetectException {
        detector.clean();
        detector.append(text);
        ArrayList<Language> probs = detector.getProbabilities();
        return detector.detect();
    }

    /**
     * For the current token, perform language detection on the entire buffer
     * and assign its thereWillBeMore to the type
     *
     * @return
     * @throws IOException
     */
    protected boolean assignLanguage() throws IOException {
        if (typeAttribute.type().equals("<ALPHANUM>")) {
            String termText = termAttribute.toString();
            LOG.log(Level.INFO, "Giving term <{0}> the language detected from {1}", new Object[]{termText, buffer.getCharacters()});
            try {
                String language = detectLanguage(buffer.getCharacters());
                typeAttribute.setType("<ALPHANUM-" + language + ">");
            } catch (LangDetectException e) {
                LOG.log(Level.WARNING, "Exception in detecting language of " + termText, e);
                typeAttribute.setType("<ALPHANUM-x>");
            }
        }

        return true;
    }

    /**
     * Attempts to read from the input stream and populate the buffer around the
     * term of interest. This is a moving window of given width.
     *
     * @return true if we have not reached the end of the input stream
     * @throws IOException
     * @throws LangDetectException
     */
    protected boolean updateBuffer() throws IOException, LangDetectException {
        //LOG.log(Level.INFO, "LTF.updateBuffer ");

        boolean gotToken = true;
        do {
            //get the first token that is actually a word
            gotToken = input.incrementToken();
            //if it's not the type i want, i put it asside to be returned without having its language detected 
            if (gotToken && !input.getAttribute(TypeAttribute.class).type().equals("<ALPHANUM>")) {
                setAsside.add(input.cloneAttributes());

            }
        } while (!input.getAttribute(TypeAttribute.class).type().equals("<ALPHANUM>") && gotToken);

        //LOG.log(Level.INFO, "  input.incrToken() returned {0}", gotToken);
        LOG.log(Level.INFO, "termAttribute: {0}", termAttribute);

        if (gotToken) {
            buffer.add(input.cloneAttributes());
        } else {
            buffer.add(null);
            originalStreamFinished = true;
        }
        firstLoad = false;
        //fill the buffer in case it's not already full
        while (buffer.inputTokens.size() < buffer.size && gotToken) {
            do {
                gotToken = input.incrementToken();
                //if it's not the type i want, i put it asside to be returned without having its language detected 
                if (gotToken && !input.getAttribute(TypeAttribute.class).type().equals("<ALPHANUM>")) {
                    setAsside.add(input.cloneAttributes());
                }
            } while (!input.getAttribute(TypeAttribute.class).type().equals("<ALPHANUM>") && gotToken);

            firstLoad = true;
            //LOG.log(Level.INFO, "  input.incrToken() returned {0}", gotToken);
            LOG.log(Level.INFO, "termAttribute: {0}", termAttribute);

            if (gotToken) {
                buffer.add(input.cloneAttributes());
            } else {
                buffer.add(null);
                originalStreamFinished = true;
            }
        }

        final int middle = buffer.size / 2;

        if (firstLoad && originalStreamFinished) {
            //means our entire stream fits into the buffer, so they all get the same language tag
            String language = detectLanguage(buffer.getCharacters());
            buffer.inputTokens.stream()
                    .forEach(token -> {
                        token.getAttribute(TypeAttribute.class).setType("<ALPHANUM-" + language + ">");
                        setAsside.add(token);
                    });
            return false;
        }

        if (firstLoad) {
            //if this was the first load, the first half would not have their language  identified.
            for (int i = 0; i < middle; i++) {
                final int ii = i;
                String localWindow = buffer.inputTokens.stream()
                        .filter(token -> buffer.inputTokens.indexOf(token) <= (ii + middle))
                        .map(token -> token.getAttribute(CharTermAttribute.class).toString())
                        .reduce("", String::concat);
                String language = detectLanguage(localWindow);
                buffer.inputTokens.get(i).getAttribute(TypeAttribute.class).setType("<ALPHANUM-" + language + ">");
                setAsside.add(buffer.inputTokens.get(i));
            }
        }

        if (originalStreamFinished) {
            //we have finished the original stream, we must add the second half to the stack and detect its language
            //if this was the first load, the first half would not have their language  identified.
            for (int i = middle + 1; i < buffer.inputTokens.size(); i++) {
                final int ii = i;
                String localWindow = buffer.inputTokens.stream()
                        .filter(token -> buffer.inputTokens.indexOf(token) >= (ii - middle))
                        .map(token -> token.getAttribute(CharTermAttribute.class).toString())
                        .reduce("", String::concat);
                String language = detectLanguage(localWindow);
                buffer.inputTokens.get(i).getAttribute(TypeAttribute.class).setType("<ALPHANUM-" + language + ">");
                setAsside.add(buffer.inputTokens.get(i));
            }
        }
        //input.restoreState(state);

        return gotToken;
    }

    @Override
    public boolean incrementToken() throws IOException {
        try {
            //LOG.log(Level.INFO, "LTF.incrementToken ");

            if (!setAsside.isEmpty()) {
                AttributeSource token = setAsside.poll();
                this.typeAttribute.setType(token.getAttribute(TypeAttribute.class).type());
                this.termAttribute.setEmpty();
                this.termAttribute.append(token.getAttribute(CharTermAttribute.class));
                this.posIncAtt.setPositionIncrement(token.getAttribute(PositionIncrementAttribute.class).getPositionIncrement());
                this.posLenAtt.setPositionLength(token.getAttribute(PositionLengthAttribute.class).getPositionLength());
                this.offsetAtt.setOffset(token.getAttribute(OffsetAttribute.class).startOffset(),
                        token.getAttribute(OffsetAttribute.class).endOffset());
                return true;
            }

            //update first the buffer by reading from the input stream, if we haven't previously marked it as finished
            if (!frozenBuffer) {
                thereWillBeMore = originalStreamFinished ? false : updateBuffer();
            }

            //in case updating the buffer generated new elements set asside - we need to release them before we process the buffer
            if (!setAsside.isEmpty()) {
                AttributeSource token = setAsside.poll();
                this.typeAttribute.setType(token.getAttribute(TypeAttribute.class).type());
                this.termAttribute.setEmpty();
                this.termAttribute.append(token.getAttribute(CharTermAttribute.class));
                this.posIncAtt.setPositionIncrement(token.getAttribute(PositionIncrementAttribute.class).getPositionIncrement());
                this.posLenAtt.setPositionLength(token.getAttribute(PositionLengthAttribute.class).getPositionLength());
                this.offsetAtt.setOffset(token.getAttribute(OffsetAttribute.class).startOffset(),
                        token.getAttribute(OffsetAttribute.class).endOffset());
                frozenBuffer = true;
                return true;
            }

            if (thereWillBeMore) {
                frozenBuffer = false;
                // make the current token the one at the middle of the buffer
                int middle = buffer.size / 2;
                this.typeAttribute.setType(buffer.inputTokens.get(middle).getAttribute(TypeAttribute.class).type());
                this.termAttribute.setEmpty();
                this.termAttribute.append(buffer.inputTokens.get(middle).getAttribute(CharTermAttribute.class));
                this.posIncAtt.setPositionIncrement(buffer.inputTokens.get(middle).getAttribute(PositionIncrementAttribute.class).getPositionIncrement());
                this.posLenAtt.setPositionLength(buffer.inputTokens.get(middle).getAttribute(PositionLengthAttribute.class).getPositionLength());
                this.offsetAtt.setOffset(buffer.inputTokens.get(middle).getAttribute(OffsetAttribute.class).startOffset(),
                        buffer.inputTokens.get(middle).getAttribute(OffsetAttribute.class).endOffset());
                //assing it the language based on the buffer
                assignLanguage();
            } else {
                //no more elements in the original stream, start popping from list.
                if (setAsside.isEmpty()) {
                    return false;//that's it - we're done with the list as well.
                } else {
                    //read from the list
                    AttributeSource token = setAsside.poll();
                    this.typeAttribute.setType(token.getAttribute(TypeAttribute.class).type());
                    this.termAttribute.setEmpty();
                    this.termAttribute.append(token.getAttribute(CharTermAttribute.class));
                    this.posIncAtt.setPositionIncrement(token.getAttribute(PositionIncrementAttribute.class).getPositionIncrement());
                    this.posLenAtt.setPositionLength(token.getAttribute(PositionLengthAttribute.class).getPositionLength());
                    this.offsetAtt.setOffset(token.getAttribute(OffsetAttribute.class).startOffset(),
                            token.getAttribute(OffsetAttribute.class).endOffset());
                    //the stack already has the right language tags, so no need to update anything now - just make the current element the one from the stack
                }
            }

            return true;
        } catch (LangDetectException ex) {
            Logger.getLogger(LangDetFilter.class.getName()).log(Level.SEVERE, null, ex);
            //return true to allow it to continue to the next token
            return true;
        }

    }

    @Override
    public void reset() throws IOException {
        //LOG.log(Level.INFO, "LangDetFilter.reset");
        super.reset();
        buffer.reset();

    }

    @Override
    public void close() throws IOException {
        super.close();
    }
}
