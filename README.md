Prune — *"Keeping Play moving"*

Prune is a tool for automatically testing the performance of Play Framework. It automates the process of checking out different versions of Play, compiling apps against those versions and then running load tests. It saves all results into files in a Git repository. It also pushes a summary of results to a website. The name *Prune* comes from "Play runner".

[You can see the latest performance results here.](http://playframework.github.io/prune/) The results are updated daily.

## How Prune works

Prune is configured to watch several Play branches and run tests on those branches automatically. It records all test runs in a git repository called the "database".

When Prune is invoked, it will look at the revisions on the branches it is monitoring and compare them to its record of the tests that it has already run. Then it will work out which tests are missing from its records and run them.

This means Prune's behavior is completely declarative. You don't tell Prune to "test revision X of Play with tests A, B and C". Instead you just declare the testing you want, then let Prune figure out how to run it.

There are two consequences of this approach:

* Once a test has been run and its test record written to the database, then Prune will not run that test again. This means that its safe to stop and start Prune at any time and it will not repeat work that it has already done.

* If Prune configuration changes between runs, say to add a new test, then Prune will automatically run that test on all relevant revisions of Play, even if it means going back in time and testing old versions of Play again. This means it is possible to use Prune for backtesting performance.

To run each test, Prune will:

1. Check out revision P of Play and `publish-local` to a private local Ivy directory (usually in `~/.prune/ivy`).
1. Write the Play build information to the database.
1. Check out and build the test app needed for the test. It uses `stage` to get a script for running tha app.
1. Write the app build information to the database.
1. Start the test app.
1. Execute [wrk](https://github.com/wg/wrk) twice with the command line arguments needed for the test. The first run is a warmup of 30 seconds. The second is the real test, which runs for 2 minutes.
1. Write the test results to the database.
1. Stop the test app.

Actually, this process isn't completely accurate. Prune doesn't recompile Play or the test apps on every test run. It only recompiles when a new version is needed.

### Prune Git repositories

Prune uses Git heavily. It reads from the Play Framework Git repository to track new revisions of Play. Prune requires some application code to use for testing Play. This application code is pulled from a separate Git repository. It stores all test results as flat files, and pushes these to a remote Git repository. Finally, Prune can push test results for a website. The website uses a Git repository too.

Here are the four repositories used by Prune:

* *play* – The Play Framework respository. Generally this will be the [main Play Framework repository](https://github.com/playframework/playframework).
* *app* – The repository that contains code for test applications. Each branch of Play that Prune tests will generally have a different test application branch. E.g. Play *master* will use the *apps-master* branch, Play *2.3.x* will use the *apps-2.3.x* branch. The "official" versions of these apps are stored as branches in the [main Prune repository](https://github.com/playframework/prune).
* *db* – The repository that stores test results. This can be anywhere, but the official test results are stored in the *database* branch in the [main Prune repository](https://github.com/playframework/prune).
* *site* – The repository that store a website for the test results. The official Prune test website is stored in the *gh-pages* branch in the [main Prune repository](https://github.com/playframework/prune).

As you can see, you can actually use one repository for multiple purposes, provided the repository has enough branches in it.

## Example usage

A typical Prune workflow involves pulling the latest information from the *play* and *apps* repositories (and rebasing the *db* repositories), running some tests, then pushing results back to the *db* repository. The commands below illustrate this:

```
prune pull
prune test
prune push-test-results
```

As a separate workflow you can also update the website. This will update the site with the latest results contained in the test results database.

```
prune pull-site
prune generate-site-files
prune push-site
```

Finally, you can get a quick report of test results and remaining tests to run.

```
prune print-report
```

## Building Prune

Prune is a command line JVM application written in Scala. Use [sbt](http://www.scala-sbt.org/) to build it.

    $ sbt dist
    $ unzip prune/target/universal/prune-1.0.zip -d <target-dir>

The script for launching Prune can be found in `<target-dir>/prune-1.0/bin/prune`.

## Configuring Prune

Before you run Prune you need to configure it. Prune uses [Typesafe Config](https://github.com/typesafehub/config) for its configuration. Most configuration is defined in a [`reference.conf`](https://github.com/playframework/prune/blob/master/src/main/resources/reference.conf) file. The default configuration is overridden by a configuration file in `~/.prune/prune.config`. All configuration in this file is automatically prefixed with the path `com.typesafe.play.prune`. Some keys are not defined in `reference.conf` so they always need to be provided by the user in the `~/.prune/prune.config` file.

Create a file called `~/.prune/prune.config` and provide values for all the keys below.

```
# The UUID used to identify this instance of Prune in test records.
# Each Prune instance needs a unique id. To generate a unique id, go
# to http://www.famkruithof.net/uuid/uuidgen.
#pruneInstanceId: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx

# The location of Java 8 on the system. Prune will use this JDK when
# building and running tests.
#java8.home: /usr/lib/jvm/java-8-oracle/jre

# The remote git repository and branch to use for storing test
# results. This could be a Github repository or it could just be a
# path to a local repository that you've created with `git init`. If
# it's a remote repository and you want to push to that repository,
# be sure to configure appropriate SSH keys in `~/.ssh`.
#dbRemote: "https://github.com/playframework/prune.git"
#dbBranch: database

# The remote git repository and branch to use as a results website.
# This could be a Github repository or it could just be a path to a
# local repository that you've created with `git init`. If it's a
# remote repository and you want to push to that repository, be sure
# to configure appropriate SSH keys in `~/.ssh`.
#siteRemote: "https://github.com/playframework/prune.git"
#siteBranch: gh-pages
```

## Running Prune

Prune understands a number of different commands. The script for launching Prune is `prune-1.0/bin/prune`, located at the place where you extracted the zip file.

### pull

Running this command pulls from the Play, app and database repositories. Local branches will be rebased, if necessary. Generally only the database repository will have any local modifications to rebase from.

Examples:

* Pull from all repositories.
  ```
  prune pull
  ```

* Pull from the Play and app repositories, but skip the database repository.
  ```
  prune pull --skip-db-fetch
  ```

* Only pull from the database repository.
  ```
  prune pull --skip-play-fetch --skip-apps-fetch
  ```

### test

This command runs all remaining tests. Prune will:

1. look at its configuration to see which Play branches and revisions it is interested in
1. look at the Play repository to get a list of revisions
1. look at the database repository to see which tests have already been run
1. create a plan of all the tests that need to be run
1. run them one at a time (see *How Prune works* for more info)

All results are written to the database repository.

Examples:

* Run all tests.
  ```
  prune test
  ```

* Run at most one test.
  ```
  prune test --max-test-runs 1
  ```

* Run tests until 30 minutes have passed. Prune will run as many tests as it can until this time limit is reached. Note: Prune may run slightly longer than the given time, because it only checks the time at the start of each new test run.

  ```
  prune test --max-total-minutes 30
  ```

* Run all tests, but limit the length of time that [wrk](https://github.com/wg/wrk) is run for to 5 seconds. This is very useful for debugging Prune, but **it will produce invalid test results because wrk will not be running for the correct amount of time.**

  ```
  prune test --max-wrk-duration 5
  ```

### push-test-results

This command pushes the test results in the database repository to the remote repository. This command doesn't need to be run, but it is useful for backing up results.

* Push the test results to the database repository.
  ```
  prune push-test-results
  ```