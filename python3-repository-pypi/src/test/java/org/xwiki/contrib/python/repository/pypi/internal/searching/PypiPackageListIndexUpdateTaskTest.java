/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.python.repository.pypi.internal.searching;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.xwiki.contrib.python.repository.pypi.internal.PypiParameters;
import org.xwiki.contrib.python.repository.pypi.internal.utils.PypiHttpClient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Validate {@link PypiPackageListIndexUpdateTask}.
 *
 * @version $Id$
 */
public class PypiPackageListIndexUpdateTaskTest
{
    private File permanentDirectory;

    private PypiPackageIndex index;

    private PypiHttpClient httpClient = mock(PypiHttpClient.class);

    private PypiPackageListIndexUpdateTask task;

    @Before
    public void before() throws Exception
    {
        this.permanentDirectory = new File("target/test-" + System.nanoTime()).getAbsoluteFile();
        this.index = new PypiPackageIndex(this.permanentDirectory, mock(Logger.class));

        this.task = new PypiPackageListIndexUpdateTask(this.index, this.httpClient, mock(Logger.class));
    }

    private List<String> parsePackageNames(InputStream stream) throws IOException
    {
        List<String> packageNames = new ArrayList<>();
        this.task.parsePackageNames(stream, packageNames::add);

        return packageNames;
    }

    private List<String> parsePackageNames(String json) throws IOException
    {
        return parsePackageNames(IOUtils.toInputStream(json, StandardCharsets.UTF_8));
    }

    @Test
    public void parsePackageNames() throws Exception
    {
        try (InputStream stream = getClass().getResourceAsStream("SimpleIndex.json")) {
            assertEquals(
                Arrays.asList("0", "0-._.-._.-._.-._.-._.-._.-0", "000", "decorator", "networkx", "numpy", "requests"),
                parsePackageNames(stream));
        }
    }

    @Test
    public void parsePackageNamesIgnoresUnknownFields() throws Exception
    {
        String json = "{\"unknown\":{\"projects\":[{\"name\":\"nested\"}]},"
            + "\"projects\":[{\"extra\":[1,{\"name\":\"x\"}],\"name\":\"package\"}],\"meta\":{}}";

        assertEquals(Arrays.asList("package"), parsePackageNames(json));
    }

    @Test
    public void parsePackageNamesWithoutProjects() throws Exception
    {
        assertTrue(parsePackageNames("{}").isEmpty());
    }

    @Test(expected = IOException.class)
    public void parsePackageNamesWhenNotAnObject() throws Exception
    {
        parsePackageNames("[]");
    }

    @Test
    public void run() throws Exception
    {
        this.index.update(consumer -> consumer.accept("previous"));
        File previousIndex = this.index.getFile();

        when(this.httpClient.openStream(new URI(PypiParameters.PACKAGE_LIST_SIMPLE_API),
            PypiParameters.SIMPLE_API_JSON_MEDIA_TYPE)).thenReturn(getClass().getResourceAsStream("SimpleIndex.json"));

        this.task.run();

        assertNotEquals(previousIndex, this.index.getFile());
        assertFalse(previousIndex.exists());
        assertEquals(
            Arrays.asList("0", "0-._.-._.-._.-._.-._.-._.-0", "000", "decorator", "networkx", "numpy", "requests"),
            Files.readAllLines(this.index.getFile().toPath()));
    }

    @Test
    public void runWhenIndexIsNotAvailable() throws Exception
    {
        this.index.update(consumer -> consumer.accept("previous"));
        File previousIndex = this.index.getFile();

        this.task.run();

        // Keep the current index instead of replacing it with an empty one
        assertEquals(previousIndex, this.index.getFile());
        assertTrue(previousIndex.exists());
    }

    @Test
    public void runWhenIndexIsInvalid() throws Exception
    {
        this.index.update(consumer -> consumer.accept("previous"));
        File previousIndex = this.index.getFile();

        when(this.httpClient.openStream(any(), any()))
            .thenReturn(IOUtils.toInputStream("<html>", StandardCharsets.UTF_8));

        this.task.run();

        assertEquals(previousIndex, this.index.getFile());
        assertEquals(Arrays.asList("previous"), Files.readAllLines(previousIndex.toPath()));
        assertEquals(1, this.permanentDirectory.list().length);
    }
}
