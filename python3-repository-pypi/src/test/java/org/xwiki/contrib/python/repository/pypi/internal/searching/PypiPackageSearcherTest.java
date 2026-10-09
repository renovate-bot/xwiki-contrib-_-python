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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.xwiki.extension.repository.result.IterableResult;

import static org.junit.Assert.assertEquals;

/**
 * Validate {@link PypiPackageSearcher}.
 *
 * @version $Id$
 */
public class PypiPackageSearcherTest
{
    private PypiPackageSearcher searcher;

    @Before
    public void before() throws Exception
    {
        File indexFile = new File("target/test-" + System.nanoTime() + ".txt").getAbsoluteFile();
        Files.write(indexFile.toPath(),
            Arrays.asList("decorator-tools", "Decorator", "networkx", "py-decorator", "requests", "decorators"));

        this.searcher = new PypiPackageSearcher(indexFile);
    }

    private List<String> search(String query, int offset, int hitsPerPage, int expectedTotalHits) throws Exception
    {
        IterableResult<String> result = this.searcher.search(query, offset, hitsPerPage);

        assertEquals(expectedTotalHits, result.getTotalHits());
        assertEquals(offset, result.getOffset());

        List<String> packageNames = new ArrayList<>();
        result.forEach(packageNames::add);

        return packageNames;
    }

    @Test
    public void searchPutsExactMatchFirst() throws Exception
    {
        assertEquals(Arrays.asList("Decorator", "decorator-tools", "py-decorator", "decorators"),
            search(" DECORATOR ", 0, -1, 4));
    }

    @Test
    public void searchPutsRankedPackagesFirst() throws Exception
    {
        Map<String, Integer> ranks = Map.of("decorators", 0, "py-decorator", 1, "requests", 2);

        IterableResult<String> result = this.searcher.search("decorator", 0, -1, ranks);
        List<String> packageNames = new ArrayList<>();
        result.forEach(packageNames::add);
        assertEquals(Arrays.asList("Decorator", "decorators", "py-decorator", "decorator-tools"), packageNames);
        assertEquals(4, result.getTotalHits());

        // The ranked packages are all kept, whatever the size of the page
        result = this.searcher.search("decorator", 1, 1, ranks);
        assertEquals("decorators", result.iterator().next());
        result = this.searcher.search("decorator", 3, 1, ranks);
        assertEquals("decorator-tools", result.iterator().next());
    }

    @Test
    public void searchWithoutExactMatch() throws Exception
    {
        assertEquals(Arrays.asList("networkx"), search("work", 0, 10, 1));
    }

    @Test
    public void searchPages() throws Exception
    {
        assertEquals(Arrays.asList("Decorator", "decorator-tools"), search("decorator", 0, 2, 4));
        assertEquals(Arrays.asList("py-decorator", "decorators"), search("decorator", 2, 2, 4));
        assertEquals(Arrays.asList("decorators"), search("decorator", 3, 10, 4));
        assertEquals(Arrays.asList(), search("decorator", 4, 10, 4));
        assertEquals(Arrays.asList(), search("decorator", 0, 0, 4));
        assertEquals(Arrays.asList("Decorator", "decorator-tools"), search("decorator", -1, 2, 4));
        assertEquals(Arrays.asList("decorators"), search("decorator", 3, Integer.MAX_VALUE, 4));
    }

    @Test
    public void searchAll() throws Exception
    {
        assertEquals(6, search("", 0, -1, 6).size());
        assertEquals(6, search(null, 0, -1, 6).size());
    }

    @Test
    public void searchWithoutMatch() throws Exception
    {
        assertEquals(Arrays.asList(), search("unknown", 0, 10, 0));
    }
}
