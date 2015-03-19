Postponed schedule build trigger
================================

Plugin for Teamcity build server for executing builds on schedule with
additional condition: specified builds should not be queued or running.

In a nutshell, this is copy of standard schedule trigger with additional functionality.
The plugin adds possibility to postpone build triggering if some of other builds are still queued/running.

Installation
=============
Download [zip archive](https://github.com/grundic/teamcity-postponed-schedule-trigger/releases/latest) and copy it to your server's data directory plugins folder.
For more information, take a look at [official documentation](https://confluence.jetbrains.com/display/TCD8/Installing+Additional+Plugins)

Configuration
=============
To add postponed schedule build trigger you should select required build and proceed to
configuration screen and find build triggers section. Next click on `Add new trigger` button
and select "Postponed Schedule Trigger". Scheduler parameters are the same as in standard trigger.
Next are parameters, specific to current plugin:
`Build ids to wait for` -- list of build ids, that should be monitored. If any of the specified builds
are in queue or running then trigger is postponed.
`Time to wait for builds` -- timeout to wait. After specified number of minutes build will be triggered
notwithstanding of queue/running state of aforementioned builds.

License
-------
[MIT](https://github.com/grundic/teamcity-postponed-schedule-trigger/blob/master/LICENSE)
