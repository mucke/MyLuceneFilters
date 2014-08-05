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
import org.apache.lucene.util.RollingBuffer;

/**
 *
 * @author mihailupu
 */
public final class LangDetFilterBasedOnLookaheadTokenFilter extends TokenFilter {// FilteringTokenFilter {

    private static final Logger LOG = Logger.getLogger(LangDetFilterBasedOnLookaheadTokenFilter.class.getName());

    private final TypeAttribute typeAttribute = addAttribute(TypeAttribute.class);
    private final CharTermAttribute termAttribute = addAttribute(CharTermAttribute.class);

    protected final PositionIncrementAttribute posIncAtt = addAttribute(PositionIncrementAttribute.class);
    protected final PositionLengthAttribute posLenAtt = addAttribute(PositionLengthAttribute.class);
    protected final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);

    private final Detector detector;
    // Position of last read input token:
    protected int inputPos;

    // Position of next possible output token to return:
    protected int outputPos;

    // True if we hit end from our input:
    protected boolean end;

    private boolean tokenPending;
    private boolean insertPending;

    /**
     * Holds all state for a single position; subclass this to record other
     * state at each position.
     */
    protected static class LangDetFilterPosition implements RollingBuffer.Resettable {

        // Buffered input tokens at this position:
        public final List<AttributeSource.State> inputTokens = new ArrayList<>();

        // Next buffered token to be returned to consumer:
        public int nextRead;

        // Any token leaving from this position should have this startOffset:
        public int startOffset = -1;

        // Any token arriving to this position should have this endOffset:
        public int endOffset = -1;

        @Override
        public void reset() {
            inputTokens.clear();
            nextRead = 0;
            startOffset = -1;
            endOffset = -1;
        }

        public void add(AttributeSource.State state) {
            inputTokens.add(state);
        }

        public AttributeSource.State nextState() {
            assert nextRead < inputTokens.size();
            return inputTokens.get(nextRead++);
        }
    }

    protected final RollingBuffer<LangDetFilterPosition> positions = new RollingBufferImpl();

    /**
     * Create a new {@link LangDetFilter}.
     *
     * @param input the {@link TokenStream} to consume
     * @param detector
     * @throws com.cybozu.labs.langdetect.LangDetectException
     */
    public LangDetFilterBasedOnLookaheadTokenFilter(TokenStream input, Detector detector) throws LangDetectException {
        super(input);
        this.detector = detector;
    }

    public String detectLanguage(String text) throws LangDetectException {
        detector.clean();
        detector.append(text);
        ArrayList<Language> probs = detector.getProbabilities();
        //System.out.println("Severe");
        LOG.log(Level.INFO, "text to be detected:{0}, probabilities:{1}", new Object[]{text, probs.toString()});
        return detector.detect();
    }

    protected boolean assignLanguage() throws IOException {
        if (typeAttribute.type().equals("<ALPHANUM>")) {
            String termText = termAttribute.toString();
            State state = input.captureState();
            input.incrementToken();
            LOG.log(Level.INFO, "next word:{0}", termAttribute.toString());
            input.restoreState(state);
            try {
                String language = detectLanguage(termText);
                typeAttribute.setType("<ALPHANUM-" + language + ">");
            } catch (LangDetectException e) {
                LOG.log(Level.WARNING, "Exception in detecting language of " + termText, e);
                typeAttribute.setType("<ALPHANUM-x>");
            }
        }

        return true;
    }

    protected LangDetFilterPosition newPosition() {
        return new LangDetFilterPosition();
    }

    protected boolean peekToken() throws IOException {
        LOG.log(Level.INFO, "LTF.peekToken inputPos={0} outputPos={1} tokenPending={2}", new Object[]{inputPos, outputPos, tokenPending});

        assert !end;
        assert inputPos == -1 || outputPos <= inputPos;
        if (tokenPending) {
            positions.get(inputPos).add(captureState());
            tokenPending = false;
        }
//        LOG.log(Level.INFO, "  before input.incrementToken");

        final boolean gotToken = input.incrementToken();
        LOG.log(Level.INFO, "  input.incrToken() returned {0}", gotToken);
        LOG.log(Level.INFO, "termAttribute: {0}", termAttribute);

        if (gotToken) {
            inputPos += posIncAtt.getPositionIncrement();
            assert inputPos >= 0;
            LOG.log(Level.INFO, "  now inputPos={0}", inputPos);

            final LangDetFilterPosition startPosData = positions.get(inputPos);
            final LangDetFilterPosition endPosData = positions.get(inputPos + posLenAtt.getPositionLength());

            final int startOffset = offsetAtt.startOffset();
            if (startPosData.startOffset == -1) {
                startPosData.startOffset = startOffset;
            } else {
                // Make sure our input isn't messing up offsets:
                assert startPosData.startOffset == startOffset : "prev startOffset=" + startPosData.startOffset + " vs new startOffset=" + startOffset + " inputPos=" + inputPos;
            }

            final int endOffset = offsetAtt.endOffset();
            if (endPosData.endOffset == -1) {
                endPosData.endOffset = endOffset;
            } else {
                // Make sure our input isn't messing up offsets:
                assert endPosData.endOffset == endOffset : "prev endOffset=" + endPosData.endOffset + " vs new endOffset=" + endOffset + " inputPos=" + inputPos;
            }

            tokenPending = true;
        } else {
            end = true;
        }

        return gotToken;
    }

    @Override
    public boolean incrementToken() throws IOException {
        LOG.log(Level.INFO, "LTF.incrementToken inputPos={0} outputPos={1} tokenPending={2}", new Object[]{inputPos, outputPos, tokenPending});

        LangDetFilterPosition posData = positions.get(outputPos);

        // While loop here in case we have to
        // skip over a hole from the input:
        while (true) {

            //System.out.println("    check buffer @ outputPos=" +
            //outputPos + " inputPos=" + inputPos + " nextRead=" +
            //posData.nextRead + " vs size=" +
            //posData.inputTokens.size());
            // See if we have a previously buffered token to
            // return at the current position:
            if (posData.nextRead < posData.inputTokens.size()) {
                LOG.log(Level.INFO, "  return previously buffered token");
                // This position has buffered tokens to serve up:
                if (tokenPending) {
                    positions.get(inputPos).add(captureState());
                    tokenPending = false;
                }
                restoreState(positions.get(outputPos).nextState());
                //System.out.println("      return!");
                return true;
            }

            if (inputPos == -1 || outputPos == inputPos) {
                // No more buffered tokens:
                // We may still get input tokens at this position
                //System.out.println("    break buffer");
                if (tokenPending) {
                    // Fast path: just return token we had just incr'd,
                    // without having captured/restored its state:
                    LOG.log(Level.INFO, "  pass-through: return pending token");

                    tokenPending = false;
                    return true;
                } else if (end || !peekToken()) {
                    LOG.log(Level.INFO, "  END");

                    afterPosition();
                    if (insertPending) {
                        // Subclass inserted a token at this same
                        // position:
                        LOG.log(Level.INFO, "  return inserted token");
                        insertPending = false;
                        return true;
                    }

                    return false;
                }
            } else {
                if (posData.startOffset != -1) {
                    // This position had at least one token leaving
                    LOG.log(Level.INFO, "  call afterPosition");

                    afterPosition();
                    if (insertPending) {
                        // Subclass inserted a token at this same
                        // position:
                        LOG.log(Level.INFO, "  return inserted token");

                        insertPending = false;
                        return true;
                    }
                }

                // Done with this position; move on:
                outputPos++;
                LOG.log(Level.INFO, "  next position: outputPos={0}", outputPos);

                positions.freeBefore(outputPos);
                posData = positions.get(outputPos);
            }
        }
    }

    protected void afterPosition() throws IOException {
        LOG.log(Level.INFO, "LangDetFilter.afterPosition");

        int posLength = 2;
        final LangDetFilterPosition posEndData = positions.get(outputPos + posLength);

        // Look ahead as needed until we figure out the right
        // endOffset:
        while (!end && posEndData.endOffset == -1 && inputPos <= (outputPos + posLength)) {
            if (!peekToken()) {
                break;
            }
        }

        if (posEndData.endOffset != -1) {
            // Notify super class that we are injecting a token:
            assignLanguage();
            clearAttributes();
            posLenAtt.setPositionLength(posLength);

            posIncAtt.setPositionIncrement(0);
            offsetAtt.setOffset(positions.get(outputPos).startOffset,
                    posEndData.endOffset);
            LOG.log(Level.INFO, "  inject: outputPos=" + outputPos + " startOffset=" + offsetAtt.startOffset()
                    + " endOffset=" + offsetAtt.endOffset()
                    + " posLength=" + posLenAtt.getPositionLength());

            // TODO: set TypeAtt too?
        } else {
            // Either 1) the tokens ended before our posLength,
            // or 2) our posLength ended inside a hole from the
            // input.  In each case we just skip the inserted
            // token.
        }
    }

    private class RollingBufferImpl extends RollingBuffer<LangDetFilterPosition> {

        public RollingBufferImpl() {
        }

        @Override
        protected LangDetFilterPosition newInstance() {
            return newPosition();
        }
    }

    @Override
    public void reset() throws IOException {
        LOG.log(Level.INFO, "LangDetFilter.reset");
        super.reset();
        positions.reset();
        inputPos = -1;
        outputPos = 0;
        tokenPending = false;
        end = false;
    }

    @Override
    public void close() throws IOException {
        super.close();
    }
}
