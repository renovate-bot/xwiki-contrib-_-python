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
package org.xwiki.contrib.python.test.po;

import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.xwiki.extension.test.po.ExtensionAdministrationPage;
import org.xwiki.extension.test.po.PaginationFilterPane;
import org.xwiki.extension.test.po.SearchResultsPane;
import org.xwiki.extension.test.po.SimpleSearchPane;

/**
 * The extensions administration page, with the features needed to test the Python packages.
 *
 * @version $Id$
 */
public class PythonExtensionAdministrationPage extends ExtensionAdministrationPage
{
    /**
     * The button to start indexing, displayed in the status of the index when it's not being indexed.
     */
    private static final By INDEXED = By.cssSelector(".infomessage input[name='index_start']");

    private static final long INDEX_CHECK_DELAY = 2000L;

    /**
     * Go to the extensions administration page.
     *
     * @return the page
     */
    public static PythonExtensionAdministrationPage gotoPage()
    {
        ExtensionAdministrationPage.gotoPage();

        return new PythonExtensionAdministrationPage();
    }

    /**
     * Index the extensions again (for example because a new repository was added) and wait until it's done.
     *
     * @param timeout the maximum number of seconds to wait for each indexing
     * @return the page, once the extensions are indexed
     * @throws InterruptedException when interrupted while waiting
     */
    public PythonExtensionAdministrationPage reindex(int timeout) throws InterruptedException
    {
        // Wait for the indexing which might be in progress
        PythonExtensionAdministrationPage page = waitUntilIndexed(timeout);

        page.startIndex();

        return new PythonExtensionAdministrationPage().waitUntilIndexed(timeout);
    }

    /**
     * @param timeout the maximum number of seconds to wait
     * @return the page, once the extensions are indexed
     * @throws InterruptedException when interrupted while waiting
     */
    public PythonExtensionAdministrationPage waitUntilIndexed(int timeout) throws InterruptedException
    {
        long end = System.currentTimeMillis() + timeout * 1000L;
        PythonExtensionAdministrationPage page = this;
        while (getDriver().findElementsWithoutWaiting(INDEXED).isEmpty()) {
            if (System.currentTimeMillis() > end) {
                throw new TimeoutException("The extensions were still not indexed after " + timeout + " seconds");
            }

            Thread.sleep(INDEX_CHECK_DELAY);
            page = gotoPage();
        }

        return page;
    }

    /**
     * Search the indexed compatible extensions until at least the expected number of them is found (the extensions
     * are validated one by one by the indexing, which might still be running).
     *
     * @param expectedCount the minimum number of extensions to find
     * @param timeout the maximum number of seconds to wait
     * @return the names of the compatible extensions found by the last search
     * @throws InterruptedException when interrupted while waiting
     */
    public List<String> waitForCompatibleExtensions(int expectedCount, int timeout) throws InterruptedException
    {
        long end = System.currentTimeMillis() + timeout * 1000L;
        while (true) {
            PythonExtensionAdministrationPage page = gotoPage();
            SimpleSearchPane searchBar = page.getSearchBar();
            searchBar.setRecommended(false);
            searchBar.setIndexed(true);
            searchBar.setCompatible(true);
            searchBar.search("");

            List<String> names = new PythonExtensionAdministrationPage().getAllSearchResultNames();
            if (names.size() >= expectedCount || System.currentTimeMillis() > end) {
                return names;
            }

            Thread.sleep(INDEX_CHECK_DELAY);
        }
    }

    /**
     * Search directly in the repositories (not in the extension index), which can take a while since each result is
     * resolved remotely.
     *
     * @param query the text to search
     * @param timeout the maximum number of seconds to wait for the search results
     * @return the search results
     */
    public SearchResultsPane searchInRepositories(String query, int timeout)
    {
        SimpleSearchPane searchBar = getSearchBar();
        searchBar.setRecommended(false);
        searchBar.setIndexed(false);

        int previousTimeout = getDriver().getTimeout();
        getDriver().setTimeout(timeout);
        try {
            return searchBar.search(query);
        } finally {
            getDriver().setTimeout(previousTimeout);
        }
    }

    /**
     * @return the names of all the extensions found by the current search (on all the pages of results)
     */
    public List<String> getAllSearchResultNames()
    {
        List<String> names = new ArrayList<>();

        SearchResultsPane results = getSearchResults();
        while (true) {
            for (int i = 0; i < results.getDisplayedResultsCount(); ++i) {
                names.add(results.getExtension(i).getName());
            }

            PaginationFilterPane pagination = results.getPagination();
            if (pagination == null || !pagination.hasNextPage()) {
                break;
            }
            getDriver().addPageNotYetReloadedMarker();
            pagination.nextPage();
            getDriver().waitUntilPageIsReloaded();
            results = new SearchResultsPane();
        }

        return names;
    }
}
