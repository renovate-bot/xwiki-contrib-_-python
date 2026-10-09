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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.contrib.python.PythonPackages;
import org.xwiki.extension.repository.result.CollectionIterableResult;
import org.xwiki.extension.repository.result.IterableResult;

/**
 * Search the package names of a {@link PypiPackageIndex} file.
 *
 * @version $Id$
 */
public class PypiPackageSearcher
{
    private record RankedName(String name, int rank)
    {
    }

    /**
     * The packages matching a query.
     */
    private static final class Matches
    {
        private final String query;

        private final long max;

        private final Map<String, Integer> ranks;

        private int totalHits;

        private String exactMatch;

        private final List<RankedName> rankedMatches = new ArrayList<>();

        private final List<String> otherMatches = new ArrayList<>();

        Matches(String query, long max, Map<String, Integer> ranks)
        {
            this.query = query;
            this.max = max;
            this.ranks = ranks;
        }

        void add(String packageName)
        {
            String lowerPackageName = packageName.toLowerCase(Locale.ROOT);
            if (lowerPackageName.contains(this.query)) {
                ++this.totalHits;
                if (this.exactMatch == null && lowerPackageName.equals(this.query)) {
                    this.exactMatch = packageName;
                } else {
                    Integer rank =
                        this.ranks.isEmpty() ? null : this.ranks.get(PythonPackages.normalizeName(packageName));
                    if (rank != null) {
                        this.rankedMatches.add(new RankedName(packageName, rank));
                    } else if (this.otherMatches.size() < this.max) {
                        this.otherMatches.add(packageName);
                    }
                }
            }
        }

        /**
         * @return the exact match, then the ranked matches from the best rank, then the other matches
         */
        List<String> toList()
        {
            List<String> result = new ArrayList<>(this.rankedMatches.size() + this.otherMatches.size() + 1);
            if (this.exactMatch != null) {
                result.add(this.exactMatch);
            }
            this.rankedMatches.stream().sorted(Comparator.comparingInt(RankedName::rank))
                .forEach(ranked -> result.add(ranked.name()));
            result.addAll(this.otherMatches);

            return result;
        }
    }

    private final File indexFile;

    /**
     * @param indexFile the index file, containing one package name per line
     */
    public PypiPackageSearcher(File indexFile)
    {
        this.indexFile = indexFile;
    }

    /**
     * @return the index file
     */
    public File getIndexFile()
    {
        return this.indexFile;
    }

    /**
     * Find the packages whose name contains the query, ignoring the case. The package with exactly the searched name
     * comes first, the others follow in the order of the index.
     *
     * @param searchQuery the text to search in the package names, all packages are matched when empty
     * @param offset the index of the first package to return
     * @param hitsPerPage the maximum number of packages to return, or a negative value to return all of them
     * @return the names of the packages found
     * @throws IOException when failing to read the index
     */
    public IterableResult<String> search(String searchQuery, int offset, int hitsPerPage) throws IOException
    {
        return search(searchQuery, offset, hitsPerPage, Collections.emptyMap());
    }

    /**
     * Find the packages whose name contains the query, ignoring the case. The package with exactly the searched name
     * comes first, followed by the ranked packages (from the best rank), and then the others in the order of the
     * index.
     *
     * @param searchQuery the text to search in the package names, all packages are matched when empty
     * @param offset the index of the first package to return
     * @param hitsPerPage the maximum number of packages to return, or a negative value to return all of them
     * @param ranks the rank of the packages to put first (indexed by normalized name), starting with 0 for the best
     * @return the names of the packages found
     * @throws IOException when failing to read the index
     */
    public IterableResult<String> search(String searchQuery, int offset, int hitsPerPage, Map<String, Integer> ranks)
        throws IOException
    {
        String query = StringUtils.defaultString(searchQuery).trim().toLowerCase(Locale.ROOT);
        int from = Math.max(offset, 0);
        // Only the packages up to the end of the requested page need to be remembered (except the ranked ones, which
        // need to be sorted, but there are only a few thousands of them)
        long max = hitsPerPage < 0 ? Integer.MAX_VALUE : Math.min((long) from + hitsPerPage, Integer.MAX_VALUE);

        Matches matches = new Matches(query, max, ranks);
        try (BufferedReader reader = Files.newBufferedReader(this.indexFile.toPath(), StandardCharsets.UTF_8)) {
            for (String packageName = reader.readLine(); packageName != null; packageName = reader.readLine()) {
                matches.add(packageName);
            }
        }

        List<String> sortedMatches = matches.toList();
        List<String> result;
        if (from >= sortedMatches.size() || max <= from) {
            result = Collections.emptyList();
        } else {
            result = sortedMatches.subList(from, (int) Math.min(sortedMatches.size(), max));
        }

        return new CollectionIterableResult<>(matches.totalHits, offset, result);
    }
}
