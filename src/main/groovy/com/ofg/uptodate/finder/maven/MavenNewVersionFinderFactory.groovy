package com.ofg.uptodate.finder.maven

import com.ofg.uptodate.LoggerProxy
import com.ofg.uptodate.UptodatePluginExtension
import com.ofg.uptodate.finder.*
import com.ofg.uptodate.finder.http.HTTPBuilderProvider
import com.ofg.uptodate.finder.Dependency
import com.ofg.uptodate.finder.DependencyVersion
import com.ofg.uptodate.finder.FinderConfiguration
import com.ofg.uptodate.finder.RepositorySettings
import com.ofg.uptodate.finder.util.StringMatcher
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import groovyx.net.http.HTTPBuilder

import java.util.concurrent.Future

import static com.ofg.uptodate.UrlEspaceUtils.escape
import static com.ofg.uptodate.finder.http.HTTPBuilderProvider.FailureHandlers.logOnlyFailureHandler

@PackageScope
@Slf4j
class MavenNewVersionFinderFactory implements NewVersionFinderFactory {

    public static final String MAVEN_CENTRAL_REPO_URL = "http://search.maven.org/solrsearch/select"

    private final LoggerProxy loggerProxy

    MavenNewVersionFinderFactory(LoggerProxy loggerProxy) {
        this.loggerProxy = loggerProxy
    }

    @Override
    NewVersionFinder create(UptodatePluginExtension uptodatePluginExtension, List<Dependency> dependencies) {
        FinderConfiguration finderConfiguration = new FinderConfiguration(
                new RepositorySettings(repoUrl: uptodatePluginExtension.mavenRepo, ignoreRepo: uptodatePluginExtension.ignoreMavenCentral),
                uptodatePluginExtension,
                dependencies.size())
        return new NewVersionFinder(
                loggerProxy,
                mavenLatestVersionsCollector(finderConfiguration),
                finderConfiguration)
    }

    private Closure<Future> mavenLatestVersionsCollector(FinderConfiguration finderConfiguration) {
        HTTPBuilder httpBuilder = new HTTPBuilderProvider(finderConfiguration.httpConnectionSettings).get()
        return getLatestFromMavenCentralRepo.curry(httpBuilder, finderConfiguration.excludedVersionPatterns)
    }

    private Closure<Future> getLatestFromMavenCentralRepo = { HTTPBuilder httpBuilder, List<String> versionToExcludePatterns, Dependency dependency ->
        httpBuilder.handler.failure = logOnlyFailureHandler(loggerProxy, log, dependency.name)
        httpBuilder.get(queryString: "q=${escape("g:\"$dependency.group\"")}+AND+${escape("a:\"$dependency.name\"")}&core=gav&rows10&wt=json".toString()) { resp, json ->
            if (!json) {
                return []
            }
            DependencyVersion latestNonExcludedVersion = json.response.docs.findAll { doc ->
                new StringMatcher(doc.v).notMatchesAny(versionToExcludePatterns)
            }.collect { new DependencyVersion(it.v) }.max()
            return [dependency, new Dependency(dependency, latestNonExcludedVersion)]
        }
    }
}
