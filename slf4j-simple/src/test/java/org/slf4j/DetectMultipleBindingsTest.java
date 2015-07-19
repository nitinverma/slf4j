package org.slf4j;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.impl.StaticLoggerBinder;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by nitin.verma on 18/07/15.
 */
public class DetectMultipleBindingsTest {
    private static final String MULTIPLE_BINDINGS_STRING = "Found binding in";
    private static final String DETECT_MULTIPLE_BINGINGS_FIELD_NAME = "DETECT_MULTIPLE_BINGINGS";

    private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    private final PrintStream oldErr = System.err;

    private static class MyClassLoader extends ClassLoader {
        public MyClassLoader() {
            super(StaticLoggerBinder.class.getClassLoader());
        }

        public Class loadClass(String className) throws ClassNotFoundException {
            if (className.startsWith("org.slf4j")) {
                return loadIt(className);
            }
            return getParent().loadClass(className);
        }

        private Class loadIt(final String className) {
            Class result = null;
            final String clazzName = className.replace(".", "/").concat(".class");
            final InputStream in = getParent().getResourceAsStream(clazzName);
            if (in != null) {
                try {
                    ByteArrayOutputStream os = null;
                    try {
                        os = new ByteArrayOutputStream();
                        int read;
                        while ((read = in.read()) != -1) {
                            os.write(read);
                        }
                        os.flush();
                    } finally {
                        if (os != null) {
                            os.close();
                        }
                    }
                    final byte[] classByte = os.toByteArray();
                    result = defineClass(className, classByte, 0, classByte.length, null);
                } catch (final Throwable throwable) {
                    throwable.printStackTrace();
                }
            }
            return result;
        }

        @Override
        protected Enumeration<URL> findResources(String name) throws IOException {
            Enumeration<URL> enumeration = null;
            if (name.equals("org/slf4j/impl/StaticLoggerBinder.class")) {
                final ArrayList<URL> arrayList = new ArrayList<URL>();
                arrayList.add(new URL("file://fake.class"));
                enumeration = Collections.enumeration(arrayList);
            }
            return enumeration;
        }
    }

    @Before
    public void setUp() {
        System.setErr(new PrintStream(byteArrayOutputStream));
    }

    private Object getLogger(final ClassLoader classLoader, final Class<?> clazz, final Boolean detectMultipleBindings) {
        try {
            if (detectMultipleBindings != null) {
                System.setProperty(LoggerFactory.DETECT_MULTIPLE_BINGINGS_PROPERTY, Boolean.toString(detectMultipleBindings));
            }
            final Class classLoggerFactory = classLoader.loadClass(LoggerFactory.class.getName());
            @SuppressWarnings("unchecked")
            final Method getLogger = classLoggerFactory.getMethod("getLogger", Class.class);
            final Field field = classLoggerFactory.getDeclaredField(DETECT_MULTIPLE_BINGINGS_FIELD_NAME);
            field.setAccessible(true);
            final Boolean flag = (Boolean) field.get(classLoggerFactory);
            if (detectMultipleBindings != null) {
                assertTrue(DETECT_MULTIPLE_BINGINGS_FIELD_NAME + " should be <" + detectMultipleBindings + "> but found <" + flag + ">", flag.equals(detectMultipleBindings));
            } else {
                assertTrue("Unset: " + DETECT_MULTIPLE_BINGINGS_FIELD_NAME + " should be <" + true + "> but found <" + flag + ">", flag);
            }
            return getLogger.invoke(classLoggerFactory, clazz);
        } catch (final Throwable th) {
            th.printStackTrace();
        } finally {
            if (System.getProperty(LoggerFactory.DETECT_MULTIPLE_BINGINGS_PROPERTY) != null) {
                System.setProperty(LoggerFactory.DETECT_MULTIPLE_BINGINGS_PROPERTY, Boolean.TRUE.toString());
            }
        }
        return null;
    }

    private String getLoggerName(final Object logger) {
        try {
            final Method getName = logger.getClass().getMethod("getName");
            return (String) getName.invoke(logger);
        } catch (final Throwable th) {
            th.printStackTrace();
        }
        return null;
    }

    @After
    public void tearDown() {
        System.setErr(oldErr);
    }


    /**
     * Test behaviour while system property is not set.
     */
    @Test
    public void testWithoutPropertySet() {
        assertEquals("java.lang.Class", getLoggerName(getLogger(new MyClassLoader(), Class.class, null)));
        assertDetectMultipleBindings(true);
    }

    /**
     * Test detectMultipleBindings turned off.
     */
    @Test
    public void testWithPropertyFalse() {
        assertEquals("java.lang.String", getLoggerName(getLogger(new MyClassLoader(), String.class, false)));
        assertDetectMultipleBindings(false);
    }

    /**
     * Test detectMultipleBindings turned on.
     */
    @Test
    public void testWithPropertyTrue() {
        assertEquals("org.slf4j.impl.StaticLoggerBinder", getLoggerName(getLogger(new MyClassLoader(), StaticLoggerBinder.class, true)));
        assertDetectMultipleBindings(true);
    }


    private void assertDetectMultipleBindings(boolean expected) {
        final String streamData = String.valueOf(byteArrayOutputStream);
        assertEquals(expected, streamData.contains(MULTIPLE_BINDINGS_STRING));
    }
}


