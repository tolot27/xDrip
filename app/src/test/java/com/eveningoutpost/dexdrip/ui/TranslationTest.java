package com.eveningoutpost.dexdrip.ui;


import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.file.FileVisitResult.CONTINUE;

import android.content.res.Configuration;

import com.eveningoutpost.dexdrip.R;
import com.eveningoutpost.dexdrip.RobolectricTestWithConfig;
import com.eveningoutpost.dexdrip.xdrip;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import lombok.val;

public class TranslationTest extends RobolectricTestWithConfig {

    Configuration config;

    @Before
    public void setUp() {
        config = xdrip.getAppContext().getResources().getConfiguration();
    }

    /**
     * Check that MessageFormat strings are still working after resource translation
     */
    @Test
    public void testFormatStrings() throws IOException {
        val internal = xdrip.getAppContext().getResources().getStringArray(R.array.LocaleChoicesValues);
        val extra = new String[]{"ar", "cs", "de", "el", "en", "es", "fi", "fr", "he", "hr", "it", "ja", "ko", "nb", "nl", "no", "pl", "pt", "ro", "ru", "sk", "sl", "sv", "tr", "zh"};
        val inset = "^values-";
        Set<String> locales = new TreeSet<>(Arrays.asList(internal));
        class ResourceLocaleParser implements FileVisitor<Path> {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                val s = dir.getFileName().toString();
                if (s.matches(inset + "[a-z][a-z]($|-r[a-zA-Z][a-zA-Z]$)")) {
                    val locale = s.replaceFirst(inset, "").replace("-r", "-");
                    locales.add(locale);
                }
                return CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                return CONTINUE;
            }
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                return CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) {
                return CONTINUE;
            }
        }

        val resourcePath = RuntimeEnvironment.application
                .getPackageResourcePath()
                .replaceFirst("intermediates(?:\\\\|/).*", "preprocessor");
        val size = locales.size();
        // scan folder xDrip/app/build/preprocessor/replace/main/res for language resource strings
        Files.walkFileTree(Paths.get(resourcePath, "replace", "main", "res"), new ResourceLocaleParser());
        assertWithMessage("No locales added from resources - this seems unlikely").that(locales.size()).isGreaterThan(size);
        locales.addAll(Arrays.asList(extra));
        String fmt;
        String result;

        for (val language : locales) {
            System.out.println("Trying choice patterns for language: " + language);
            Locale locale = Locale.forLanguageTag(language);
            assertWithMessage("Language tag does not match language").that(locale.toLanguageTag()).matches(language);
            config.setLocale(locale);
            xdrip.getAppContext().getResources().updateConfiguration(config, xdrip.getAppContext().getResources().getDisplayMetrics());

            try {
                // check minutes ago days
                fmt = xdrip.gs(R.string.minutes_ago);
                result = MessageFormat.format(fmt, 123);
                assertWithMessage("minutes_ago choice message format failed to contain value").that(result).contains("123");
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Failed minutes ago test with language " + language + " with exception: " + e);
            }

            try {
                // check expires days
                fmt = xdrip.gs(R.string.expires_days);
                result = MessageFormat.format(fmt, 123.4f);
                assertWithMessage("expires_days choice message format failed to contain value").that(result).contains("123.4");
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Failed expires days test with language " + language + " with exception: " + e);
            }
        }

    }

    @Test
    public void testSublanguage() {
        val extra = new String[]{"pt", "pt-BR"};
        Set<String> locales = new TreeSet<>(Arrays.asList(extra));

        for (val language : locales) {
            System.out.println("Trying choice patterns for language: " + language);
            Locale locale = Locale.forLanguageTag(language);
            assertWithMessage("Language tag does not match language").that(locale.toLanguageTag()).matches(language);
            config.setLocale(locale);
            xdrip.getAppContext().getResources().updateConfiguration(config, xdrip.getAppContext().getResources().getDisplayMetrics());
            try {
                // check minutes ago days
                String translated = xdrip.gs(R.string.settings);
                assertWithMessage("settings choice message format failed to contain value").that(translated).matches("Definições");
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Failed settings test with language " + language + " with exception: " + e);
            }
        }
    }

    @After
    public void tearDown() throws Exception {
        // restore after test
        config.setLocale(new Locale("en"));
        xdrip.getAppContext().getResources().updateConfiguration(config, xdrip.getAppContext().getResources().getDisplayMetrics());
    }
}
