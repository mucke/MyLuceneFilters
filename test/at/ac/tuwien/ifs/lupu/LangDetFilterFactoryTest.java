/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package at.ac.tuwien.ifs.lupu;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.util.ClasspathResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoader;
import static org.apache.lucene.util.LuceneTestCase.TEST_VERSION_CURRENT;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author mihailupu
 */
public class LangDetFilterFactoryTest {

    public LangDetFilterFactoryTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of create method, of class LangDetFilterFactory.
     */
    @Test
    public void testCreate() {
        try {
            System.out.println("create");
            Map<String, String> args = new HashMap<>();
            args.put("languages", "languages.txt");
            args.put("windowWidth","1");
            LangDetFilterFactory factory = new LangDetFilterFactory(args);
            ResourceLoader loader = new ClasspathResourceLoader(getClass());            
            factory.inform(loader);
            StringReader reader = new StringReader(" 34234 voilà la France, hello@email.com here is England");
            StandardTokenizer st = new StandardTokenizer(TEST_VERSION_CURRENT, reader);
            st.reset();
            LangDetFilter filter = (LangDetFilter) factory.create(st);
            //filter.reset();
            
            while (filter.incrementToken()) {
                System.out.println("!!!"+filter.toString());
                
            }
        } catch (IOException ex) {
            Logger.getLogger(LangDetFilterFactoryTest.class.getName()).log(Level.SEVERE, null, ex);
            fail("Exception thrown");
        }
    }

}
