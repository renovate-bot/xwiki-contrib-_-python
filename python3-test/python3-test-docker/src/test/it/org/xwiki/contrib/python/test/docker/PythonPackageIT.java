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
package org.xwiki.contrib.python.test.docker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.xwiki.contrib.python.test.po.PythonExtensionAdministrationPage;
import org.xwiki.extension.ExtensionId;
import org.xwiki.extension.test.ExtensionTestUtils;
import org.xwiki.extension.test.po.ExtensionPane;
import org.xwiki.extension.test.po.SimpleSearchPane;
import org.xwiki.model.namespace.WikiNamespace;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.ViewPage;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validate the installation of Python packages from PyPI, and their use in a Python 3 macro. The packages really come
 * from PyPI since supporting the actual PyPI is the whole point of the feature.
 *
 * @version $Id$
 */
@UITest(
    // The extension index needs to be initialized when XWiki starts, before the extensions of the test are installed
    extraJARs = {"org.xwiki.platform:xwiki-platform-extension-index"},
    // Take the version of the extra JARs from the dependencies of the test module
    resolveExtraJARs = true,
    properties = {
        // Keep the indexing of the most downloaded PyPI packages short
        "xwikiPropertiesAdditionalProperties=pypi.popularPackages.count=" + PythonPackageIT.POPULAR_PACKAGES_COUNT
            // The service page of ExtensionTestUtils and the page executing Python require programming right
            + "\ntest.prchecker.excludePattern=.*:(ExtensionTest\\.Service|NestedPythonPackageIT\\..*)"
    }
)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PythonPackageIT
{
    static final int POPULAR_PACKAGES_COUNT = 20;

    /**
     * The maximum number of seconds to wait for the extensions to be indexed.
     */
    private static final int INDEX_TIMEOUT = 900;

    /**
     * The maximum number of seconds to wait for the installation of a package (and its dependencies).
     */
    private static final int INSTALL_TIMEOUT = 300;

    private static final String PYGMENTS = "pygments";

    /**
     * A pure Python package with dependencies (Pygments, and markdown-it-py which itself depends on mdurl), whose
     * version is fixed to keep the test stable.
     */
    private static final ExtensionId RICH = new ExtensionId("rich", "15.0.0");

    private static final List<ExtensionId> RICH_NEW_DEPENDENCIES =
        List.of(new ExtensionId("markdown-it-py"), new ExtensionId("mdurl"));

    /**
     * Only optional dependencies (extras) of Rich and its dependencies.
     */
    private static final List<ExtensionId> OPTIONAL_DEPENDENCIES =
        List.of(new ExtensionId("ipywidgets"), new ExtensionId("colorama"), new ExtensionId("pytest"));

    private static final WikiNamespace MAIN_WIKI = new WikiNamespace("xwiki");

    private static final String RICH_INSTALLED = "Rich is installed";

    private static final String RICH_NOT_INSTALLED = "Rich is not installed";

    private static final String PYGMENTS_NOT_INSTALLED = "Pygments is not installed";

    private static final String PYGMENTS_RESULT = "Python: Token.Name Token.Operator Token.Literal.Number.Integer";

    private static final String SCRIPT = String.join("\n",
        "{{python3}}",
        "try:",
        "    from rich.text import Text",
        "    print(Text.from_markup('[bold]Rich[/bold] is installed').plain)",
        "except ImportError:",
        "    print('" + RICH_NOT_INSTALLED + "')",
        "try:",
        "    from pygments import lex",
        "    from pygments.lexers import get_lexer_by_name",
        "    lexer = get_lexer_by_name('python')",
        "    print(lexer.name + ': ' + ' '.join(str(t) for t, v in lex('x = 1', lexer) if v.strip()))",
        "except ImportError:",
        "    print('" + PYGMENTS_NOT_INSTALLED + "')",
        "{{/python3}}");

    private static ExtensionTestUtils extensionTestUtils;

    @BeforeAll
    static void beforeAll(TestUtils setup)
    {
        setup.loginAsSuperAdmin();
        setup.recacheSecretToken();
        // The pages saved through the REST API contain scripts
        setup.setDefaultCredentials(TestUtils.SUPER_ADMIN_CREDENTIALS);

        extensionTestUtils = new ExtensionTestUtils(setup);
    }

    /**
     * @return the page executing the script, shared by all the tests of the class
     */
    private static DocumentReference getScriptPage(TestReference testReference)
    {
        // The test reference is specific to each test method (a child of the space of the test class)
        return new DocumentReference("WebHome", new SpaceReference(testReference.getLastSpaceReference().getParent()));
    }

    private static String getScriptPageContent(TestUtils setup, TestReference testReference)
    {
        setup.gotoPage(getScriptPage(testReference));

        return new ViewPage().getContent();
    }

    /**
     * Installing an extension which is not indexed (like the dependencies of the installed packages, or the
     * extensions installed by the test framework) adds an incomplete document (without version) to the extension
     * index, which breaks the search of compatible extensions (XWIKI-23412). To remove once the project depends on a
     * version of XWiki including the fix.
     */
    private static void deleteIncompleteIndexDocuments(TestUtils setup, TestReference testReference) throws Exception
    {
        DocumentReference page =
            new DocumentReference("IndexCleanup", getScriptPage(testReference).getLastSpaceReference());
        setup.rest().savePage(page, String.join("\n",
            "{{groovy}}",
            "def client = services.component.getInstance(org.xwiki.search.solr.Solr.class)"
                + ".getCore('extension_index').getClient()",
            "client.deleteByQuery('*:* -version:[* TO *]')",
            "client.commit()",
            // Don't output the response of the last call
            "return",
            "{{/groovy}}"), "");
        setup.gotoPage(page);

        assertEquals("", new ViewPage().getContent());
    }

    /**
     * @return the latest version of Pygments published on PyPI
     */
    private static String getPygmentsLatestVersion() throws Exception
    {
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(new URI("https://pypi.org/pypi/pygments/json")).build(),
                HttpResponse.BodyHandlers.ofString());

            return new ObjectMapper().readTree(response.body()).path("info").path("version").asText();
        }
    }

    @Test
    @Order(1)
    void searchAndInstallPackage(TestUtils setup, TestReference testReference) throws Exception
    {
        setup.rest().savePage(getScriptPage(testReference), SCRIPT, "Python packages");

        assertEquals(RICH_NOT_INSTALLED + "\n" + PYGMENTS_NOT_INSTALLED, getScriptPageContent(setup, testReference));

        // Search directly in the repositories (not in the index)
        PythonExtensionAdministrationPage adminPage = PythonExtensionAdministrationPage.gotoPage();
        SimpleSearchPane searchBar = adminPage.getSearchBar();
        searchBar.setRecommended(false);
        searchBar.setIndexed(false);
        ExtensionPane extension = searchBar.search(PYGMENTS).getExtension(0);

        // The package matching exactly the search comes first, in its latest version
        assertEquals("Pygments", extension.getName());
        assertEquals(getPygmentsLatestVersion(), extension.getVersion());

        extension = extension.install().confirm(INSTALL_TIMEOUT);
        assertEquals("installed", extension.getStatus());

        // The package can be imported, including the modules it loads dynamically (the lexers)
        assertEquals(RICH_NOT_INSTALLED + "\n" + PYGMENTS_RESULT, getScriptPageContent(setup, testReference));
    }

    @Test
    @Order(2)
    void installPackageWithDependencies(TestUtils setup, TestReference testReference) throws Exception
    {
        extensionTestUtils.install(RICH);

        assertTrue(extensionTestUtils.isInstalled(RICH, MAIN_WIKI));
        // The dependencies declared by the package (and by its dependencies) are installed too
        for (ExtensionId dependency : RICH_NEW_DEPENDENCIES) {
            assertTrue(extensionTestUtils.isInstalled(dependency, MAIN_WIKI), dependency + " is not installed");
        }
        // But not the optional ones
        for (ExtensionId dependency : OPTIONAL_DEPENDENCIES) {
            assertFalse(extensionTestUtils.isInstalled(dependency, MAIN_WIKI), dependency + " is installed");
        }

        assertEquals(RICH_INSTALLED + "\n" + PYGMENTS_RESULT, getScriptPageContent(setup, testReference));
    }

    @Test
    @Order(3)
    void uninstallPackage(TestUtils setup, TestReference testReference) throws Exception
    {
        extensionTestUtils.uninstall(RICH.getId());

        assertFalse(extensionTestUtils.isInstalled(RICH, MAIN_WIKI));

        // The package is not available anymore, while its dependencies stay installed
        assertEquals(RICH_NOT_INSTALLED + "\n" + PYGMENTS_RESULT, getScriptPageContent(setup, testReference));
    }

    /**
     * Executed last since indexing is by far the longest step.
     */
    @Test
    @Order(4)
    void indexMostDownloadedPackages(TestUtils setup, TestReference testReference) throws Exception
    {
        deleteIncompleteIndexDocuments(setup, testReference);

        // The extensions were indexed when XWiki started, before the PyPI repository was installed
        PythonExtensionAdministrationPage adminPage = PythonExtensionAdministrationPage.gotoPage().reindex(INDEX_TIMEOUT);

        // Several of the most downloaded PyPI packages can be installed. Which ones depends on the current list of
        // the most downloaded packages. The installed extensions are not listed and PyPI is the only remote
        // repository which can be listed, so all the results are PyPI packages.
        int compatiblePackages = adminPage.waitForCompatibleExtensions(5, INDEX_TIMEOUT).size();
        assertTrue(compatiblePackages >= 5,
            "Only " + compatiblePackages + " of the " + POPULAR_PACKAGES_COUNT
                + " most downloaded PyPI packages were indexed as compatible");
    }
}
