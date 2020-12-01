# Deployments Status Publisher

A TeamCity [Build Feature](https://www.jetbrains.com/help/teamcity/adding-build-features.html) to publish deployment status updates to an external system.
We currently only support [GitHub's deployment api](https://docs.github.com/en/free-pro-team@latest/rest/reference/repos#deployments).  

Based on the [Gradle TeamCity plugin](https://github.com/rodm/gradle-teamcity-plugin) and the [Teamcity Commit Status Publisher](https://github.com/JetBrains/commit-status-publisher).

Releases are available at Jetbrains' Marktplace: [plugins.jetbrains.com/plugin/15475-deployments-status-publisher](https://plugins.jetbrains.com/plugin/15475-deployments-status-publisher).

We're currently developing for TeamCity version 2017.2, but it should work in more recent versions, too.

## Usage

After [installing the plugin](https://www.jetbrains.com/help/teamcity/installing-additional-plugins.html),
add the Deployments Status Publisher to your build configuration.

When a build ist started or finished, the Deployments Status Publisher will send status updates
to GitHub's deployment api. If builds are part of a build chain, they may share a common `deploymentId`
and can be distinguished by configuring different target environments. The default environment is _production_.

## Developing/Testing locally

```shell script
./gradlew clean deployToTeamcity2017.2
docker-compose up
```

## TeamCity's role in a deployment

We're referring to [GitHub's deployment sequence diagram](https://docs.github.com/en/free-pro-team@latest/rest/reference/repos#deployments)
shown below to describe the role of the Deployments Status Publisher:

```
+---------+             +--------+            +-----------+        +-------------+
| Tooling |             | GitHub |            | 3rd Party |        | Your Server |
+---------+             +--------+            +-----------+        +-------------+
     |                      |                       |                     |
     |  Create Deployment   |                       |                     |
     |--------------------->|                       |                     |
     |                      |                       |                     |
     |  Deployment Created  |                       |                     |
     |<---------------------|                       |                     |
     |                      |                       |                     |
     |                      |   Deployment Event    |                     |
     |                      |---------------------->|                     |
     |                      |                       |     SSH+Deploys     |
     |                      |                       |-------------------->|
     |                      |                       |                     |
     |                      |   Deployment Status   |                     |
     |                      |<----------------------|                     |
     |                      |                       |                     |
     |                      |                       |   Deploy Completed  |
     |                      |                       |<--------------------|
     |                      |                       |                     |
     |                      |   Deployment Status   |                     |
     |                      |<----------------------|                     |
     |                      |                       |                     |
```

TeamCity with the enabled Deployments Status Publilsher acts a `Tooling` and `3rd Party` as well,
with the exception that GitHub won't notify TeamCity for any `Deployment Event`.

## Example

An example deployment with two build configs in a build chain can create a deployment like below (taken from GitHub's api).
Build "Step 1 (example)" is enabled to create (initialize) the deployment and use the `ci` environment.
Build "Step 2 (example)" uses the deployment-id of the previous build and the default `production` environment.

```json
{
  "Repository": {
    "Name": "deployment-tests",
    "Description": "",
    "Url": "https://github.com/gesellix/deployment-tests",
    "Deployments": {
      "PageInfo": {
        "HasNextPage": false,
        "EndCursor": "Y3Vyc29yOnYyOpK5MjAyMC0xMS0wNlQyMjozMDo1MSswMTowMM4RJj7h"
      },
      "Nodes": [
        {
          "State": "ACTIVE",
          "Environment": "production",
          "Description": "TeamCity build starting (Step 1 (example))",
          "Payload": "\"\\\"{\\\\\\\"buildIdAsString\\\\\\\":\\\\\\\"85\\\\\\\"}\\\"\"",
          "CreatedAt": "2020-11-28T22:36:30Z",
          "Creator": {
            "Login": "gesellix"
          },
          "LatestStatus": {
            "Id": "MDE2OkRlcGxveW1lbnRTdGF0dXM0MzcwMjYzMTc=",
            "CreatedAt": "2020-11-28T22:36:49Z",
            "Creator": {
              "Login": "gesellix"
            },
            "State": "SUCCESS",
            "Description": "TeamCity build finished (Step 2 (example))",
            "Environment": "production",
            "LogUrl": "http://tc-server:8111/viewLog.html?buildId=87&buildTypeId=Example_Step2",
            "Deployment": {
              "Id": "MDEwOkRlcGxveW1lbnQyOTU3NTkzMDg=",
              "DatabaseId": 295759308,
              "CreatedAt": "2020-11-28T22:36:30Z"
            }
          },
          "Commit": {
            "AuthoredDate": "2020-11-06T20:23:58Z",
            "Oid": "9bd5374e375e3416ff981122703a0e4079055fea",
            "Url": "https://github.com/gesellix/deployment-tests/commit/9bd5374e375e3416ff981122703a0e4079055fea",
            "AssociatedPullRequests": {
              "PageInfo": {
                "HasNextPage": false,
                "EndCursor": ""
              },
              "Nodes": []
            }
          },
          "Statuses": {
            "PageInfo": {
              "HasNextPage": false,
              "EndCursor": "Y3Vyc29yOnYyOpHOGgx91A=="
            },
            "Nodes": [
              {
                "Id": "MDE2OkRlcGxveW1lbnRTdGF0dXM0MzcwMjYzMTc=",
                "CreatedAt": "2020-11-28T22:36:49Z",
                "Creator": {
                  "Login": "gesellix"
                },
                "State": "SUCCESS",
                "Description": "TeamCity build finished (Step 2 (example))",
                "Environment": "production",
                "LogUrl": "http://tc-server:8111/viewLog.html?buildId=87&buildTypeId=Example_Step2",
                "Deployment": {
                  "Id": "MDEwOkRlcGxveW1lbnQyOTU3NTkzMDg=",
                  "DatabaseId": 295759308,
                  "CreatedAt": "2020-11-28T22:36:30Z"
                }
              },
              {
                "Id": "MDE2OkRlcGxveW1lbnRTdGF0dXM0MzcwMjYzMDc=",
                "CreatedAt": "2020-11-28T22:36:46Z",
                "Creator": {
                  "Login": "gesellix"
                },
                "State": "IN_PROGRESS",
                "Description": "TeamCity build started (Step 2 (example))",
                "Environment": "production",
                "LogUrl": "http://tc-server:8111/viewLog.html?buildId=87&buildTypeId=Example_Step2",
                "Deployment": {
                  "Id": "MDEwOkRlcGxveW1lbnQyOTU3NTkzMDg=",
                  "DatabaseId": 295759308,
                  "CreatedAt": "2020-11-28T22:36:30Z"
                }
              },
              {
                "Id": "MDE2OkRlcGxveW1lbnRTdGF0dXM0MzcwMjYyNjA=",
                "CreatedAt": "2020-11-28T22:36:33Z",
                "Creator": {
                  "Login": "gesellix"
                },
                "State": "SUCCESS",
                "Description": "TeamCity build finished (Step 1 (example))",
                "Environment": "ci",
                "LogUrl": "http://tc-server:8111/viewLog.html?buildId=85&buildTypeId=Example_Step1",
                "Deployment": {
                  "Id": "MDEwOkRlcGxveW1lbnQyOTU3NTkzMDg=",
                  "DatabaseId": 295759308,
                  "CreatedAt": "2020-11-28T22:36:30Z"
                }
              }
            ]
          }
        }
      ]
    }
  }
}
```

## License and Notice

This work is licensed under the [MIT License](https://opensource.org/licenses/MIT), see the `LICENSE` file.
Please note that this project is heavily based on Jetbrains' [Commit Status Publisher plugin](https://github.com/JetBrains/commit-status-publisher). See the `NOTICE` file for details.
