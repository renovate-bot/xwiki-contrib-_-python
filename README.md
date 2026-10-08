# Python support

Various Python related tools for XWiki.

* Project Lead: [Thomas Mortagne](https://www.xwiki.org/xwiki/bin/view/XWiki/ThomasMortagne)
* Documentation & Downloads: [Documentation & Download](https://extensions.xwiki.org/xwiki/bin/view/Extension/Python)
* [Issue Tracker](https://jira.xwiki.org/browse/PYTHON
* Communication: [Forum](https://forum.xwiki.org/), [Chat](https://dev.xwiki.org/xwiki/bin/view/Community/Chat)
* [Development Practices](https://dev.xwiki.org)
* Minimal XWiki version supported: 17.10.0 (with Java 21)
* License: LGPL 2.1
* Translations: N/A
* Sonar Dashboard: N/A
* Continuous Integration Status: [![Build Status](https://ci.xwiki.org/job/XWiki%20Contrib/job/python/job/master/badge/icon)](https://ci.xwiki.org/job/XWiki%20Contrib/job/python/job/master/)

## PyPI repository limitations

* PyPI does not provide any way to list the packages worth being proposed, so only the most downloaded packages (the 500 first ones by default, according to https://hugovk.dev/top-pypi-packages/) are listed in the extension index (the regular extension search). Any other package can still be found by searching without the index, or installed from its identifier.

* Only the packages providing a wheel without native code (like `py3-none-any`) can be installed: packages only published as source distributions or containing native code are not supported.
* Versions, dependencies and compatibility are evaluated for the actual Python version implemented by GraalPy, and optional dependencies (extras) are never installed.

## PyPI repository configuration

The following properties can be set in `xwiki.properties`:

* `pypi.popularPackages.count`: the number of most downloaded packages listed in the extension index (500 by default, 0 to list all the PyPI packages, which means resolving hundreds of thousands of packages).
* `pypi.popularPackages.url`: the location of the list of the most downloaded packages, in the format of https://hugovk.dev/top-pypi-packages/ (the list published on GitHub by default).
