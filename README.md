# Python support

Various Python related tools for XWiki.

* Project Lead: [Thomas Mortagne](https://www.xwiki.org/xwiki/bin/view/XWiki/ThomasMortagne)
* [Documentation & Download](https://extensions.xwiki.org/xwiki/bin/view/Extension/Python)
* [Issue Tracker](https://jira.xwiki.org/browse/PYTHON)
* Communication: [Forum](https://forum.xwiki.org/), [Chat](https://dev.xwiki.org/xwiki/bin/view/Community/Chat)
* [Development Practices](https://dev.xwiki.org)
* Minimal XWiki version supported: 17.10.0 (with Java 21)
* License: LGPL 2.1
* Translations: N/A
* Sonar Dashboard: N/A
* Continuous Integration Status: [![Build Status](https://ci.xwiki.org/job/XWiki%20Contrib/job/python/job/master/badge/icon)](https://ci.xwiki.org/job/XWiki%20Contrib/job/python/job/master/)

## Release

* Release (the profiles are needed to also update the version of the test modules)

```
mvn release:prepare -Pintegration-tests,docker
mvn release:perform -Pintegration-tests,docker
```

* Import the released version on https://extensions.xwiki.org/xwiki/bin/view/Extension/Python and update its release notes
