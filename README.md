# Deployments TeamCity Plugin

Based on the [Gradle TeamCity plugin](https://github.com/rodm/gradle-teamcity-plugin) with
a lot of inspiration from [Teamcity Commit Status Publisher](https://github.com/JetBrains/commit-status-publisher).

Target TeamCity version is 2017.2, but should work in more recent versions, too.

## Developing/Testing locally

```shell script
./gradlew clean deployToTeamcity2017.2
docker-compose up
```

## Example

An example deployment with two build configs in a build chain can create a deployment like below.
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
