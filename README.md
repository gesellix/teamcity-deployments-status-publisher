# Deployments TeamCity Plugin

Based on the [Gradle TeamCity plugin](https://github.com/rodm/gradle-teamcity-plugin) with
a lot of inspiration from [Teamcity Commit Status Publisher](https://github.com/JetBrains/commit-status-publisher).

Target TeamCity version is 2017.2, but should work in more recent versions, too.

## Developing/Testing locally

```shell script
./gradlew clean deployToTeamcity2017.2
docker-compose up
```
