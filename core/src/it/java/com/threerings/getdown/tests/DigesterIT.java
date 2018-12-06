//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.tests;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;

import org.junit.*;
import static org.junit.Assert.*;

import com.threerings.getdown.tools.Digester;

public class DigesterIT {

    @Test
    public void testDigester () throws Exception {
        Path appdir = Paths.get("src/it/resources/testapp");
        Digester.createDigests(appdir.toFile(), null, null, null);

        Path digest = appdir.resolve("digest.txt");
        List<String> digestLines = Files.readAllLines(digest, StandardCharsets.UTF_8);
        Files.delete(digest);

        Path digest2 = appdir.resolve("digest2.txt");
        List<String> digest2Lines = Files.readAllLines(digest2, StandardCharsets.UTF_8);
        Files.delete(digest2);

        assertEquals(Arrays.asList(
            "getdown.txt = 779c74fb4b251e18faf6e240a0667964",
            "testapp.jar = 404dafa55e78b25ec0e3a936357b1883",
            "funny%test dir/some=file.txt = d8e8fca2dc0f896fd7cb4cb0031ba249",
            "crazyhashfile#txt = f29d23fd5ab1781bd8d0760b3a516f16",
            "foo.jar = 46ca4cc9079d9d019bb30cd21ebbc1ec",
            "script.sh = f66e8ea25598e67e99c47d9b0b2a2cdf",
            "digest.txt = f5561d85e4d80cc85883963897e58ff6"
        ), digestLines);

        assertEquals(Arrays.asList(
            "getdown.txt = 4f0c657895c3c3a35fa55bf5951c64fa9b0694f8fc685af3f1d8635c639e066b",
            "testapp.jar = c9cb1906afbf48f8654b416c3f831046bd3752a76137e5bf0a9af2f790bf48e0",
            "funny%test dir/some=file.txt = f2ca1bb6c7e907d06dafe4687e579fce76b37e4e93b7605022da52e6ccc26fd2",
            "crazyhashfile#txt = 6816889f922de38f145db215a28ad7c5e1badf7354b5cdab225a27486789fa3b",
            "foo.jar = ea188b872e0496debcbe00aaadccccb12a8aa9b025bb62c130cd3d9b8540b062",
            "script.sh = cca1c5c7628d9bf7533f655a9cfa6573d64afb8375f81960d1d832dc5135c988",
            "digest2.txt = 70b442c9f56660561921da3368e1a206f05c379182fab3062750b7ddcf303407"
        ), digest2Lines);
    }
}
