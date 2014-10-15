Prune — *"Keeping Play moving"*

Prune is a tool for automatically testing the performance of Play Framework. It automates the process of checking out different versions of Play, compiling apps against those versions and then running load tests. It saves all results into files in a Git repository. It also pushes a summary of results to a website. The name *Prune* comes from "Play runner".

## Daily test results

[**You can see graphs of the latest performance results here.**](http://playframework.github.io/prune/)

Prune is run each day on a dedicated server donated by [Typesafe](http://typesafe.com/). The server is a [Xeon E5-2430L v2 2.4GHz](http://ark.intel.com/products/75785/Intel-Xeon-Processor-E5-2430-v2-15M-Cache-2_50-GHz) with [Turbo Boost disabled](http://www.brendangregg.com/blog/2014-09-15/the-msrs-of-ec2.html). The JVM uses 1GB heap and no other settings. The load testing client is run on the same machine as the Play server. Raw test results are pushed in the [*database*](https://github.com/playframework/prune/tree/database) branch of the Prune Github repository.

## Tests

Prune currently runs the following tests. More tests are planned in the future, e.g. tests for non-GET request, tests for WS, etc.

### scala-simple / java-simple

Tests an action that sends a plain text response of `Hello world.`.

### scala-download-50k / java-download-50k

Tests an action that parses an integer parameter of 51200 (50k) then sends a binary response of that length.

### scala-download-chunked-50k / java-download-50k

Tests an action that parses an integer parameter of 51200 (50k) then sends a binary response in 4k chunks.

### scala-json-encode / java-json-encode

Test an action that encodes and returns a JSON object of `{"message":"Hello World!"}`.

### scala-template-simple / java-template-simple

Test an action that returns a short HTML page generated from a template that takes a single parameter.

### scala-template-lang / java-template-lang

Test an action that returns a short HTML page generated from a template that takes an implicit language.

## Example Prune workflow

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

You can get a quick report of test results in the database and any results that are still waiting on tests to run.

```
prune print-report
```

Finally, you can run a Prune test manually on your local Play code.

```
prune wrk /my/projects/play /my/projects/apps scala-simple
```

## How Prune works

Prune is configured to watch several Play branches and run tests on those branches automatically. It records all test runs in a Git repository called the "database".

When Prune is invoked, it will look at the revisions on the branches it is monitoring and compare them to its record of the tests that it has already run. Then it will work out which tests are missing from its records and run them.

This means Prune's behavior is completely declarative. You don't tell Prune to "run tests on HEAD". Instead you just declare the revisions you want tested, then let Prune figure out how to run them.

There are two benefits of this approach:

* Once a test has been run and its test record written to the database, then Prune will not run that test again. This means that its safe to stop and start Prune at any time and it will not repeat work that it has already done.

* If Prune configuration changes between runs, say to add a new test, then Prune will automatically run that test on all relevant revisions of Play, even if it means going back in time and testing old versions of Play again. This means it is possible to use Prune for backtesting performance.

To run a test, Prune will build Play, build a test application, start the application, then run [wrk](https://github.com/wg/wrk). All of this is recorded and written to the database repository.

### Prune Git repositories

There are four logical Git repositories used by Prune. Each repository has a remote location and one or more branches. All repositories have a local directory, usually located within `~/.prune`.

* *play* – The Play Framework respository. Prune uses this to work out which revisions of Play to test and to get the Play source code. Generally this will be the [main Play Framework repository](https://github.com/playframework/playframework).
* *app* – The repository that contains code for test applications. Prune uses multiple branches in this repository, because different versions of Play will require different test applications. E.g. Play *master* will use the *apps-master* branch, Play *2.3.x* will use the *apps-2.3.x* branch. The default versions of these apps are stored as branches in the [main Prune repository](https://github.com/playframework/prune).
* *db* (or *database*) – The repository that stores test results. The remote can be located anywhere, even a local directory, but the official test results are stored in the *database* branch in the [main Prune repository](https://github.com/playframework/prune).
* *site* – The repository that contains the test results website. The official Prune test website is stored in the *gh-pages* branch in the [main Prune repository](https://github.com/playframework/prune).

By default the Prune Github repository actually serves as the remote for the *apps*, *db* and *site* repositories. It is fine to use different repositories too. If the same repository is used then different branches are needed for each purpose.

## Building Prune

Prune is a command line JVM application written in Scala. Use [sbt](http://www.scala-sbt.org/) to build it.

    $ sbt dist
    $ unzip prune/target/universal/prune-1.0.zip -d <target-dir>

The script for launching Prune can be found in `<target-dir>/prune-1.0/bin/prune`.

## Configuring Prune

Before you run Prune you need to configure it. Prune uses [Typesafe Config](https://github.com/typesafehub/config) for its configuration. Most configuration is defined in a [`reference.conf`](https://github.com/playframework/prune/blob/master/src/main/resources/reference.conf) file. The default configuration is overridden by a configuration file in `~/.prune/prune.config`. All configuration in `prune.config` is automatically prefixed with the path `com.typesafe.play.prune`. Some keys are not defined in `reference.conf` so they always need to be provided by the user in the `~/.prune/prune.config` file.

Here is a template for a `~/.prune/prune.config` file. All keys are required.

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

Prune understands a number of different commands. The script for launching Prune is `prune-1.0/bin/prune`, located at the place where you extracted the `dist` zip file.

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

1. Look at its configuration to see which Play branches and revisions it is interested in.
1. Look at the Play repository to get a list of revisions.
1. Look at the database repository to see which tests have already been run.
1. Create a plan of all the tests that need to be run.
1. Run each test, one at a time.

For each test, Prune will:

1. Check out revision P of Play and `publish-local` to a private local Ivy directory (usually in `~/.prune/ivy`).
1. Write the Play build information to the database.
1. Check out and build the test app needed for the test. It uses `stage` to get a script for running tha app.
1. Write the app build information to the database.
1. Start the test app.
1. Execute [wrk](https://github.com/wg/wrk) twice with the command line arguments needed for the test. The first run is a warmup of 30 seconds. The second is the real test, which runs for 2 minutes.
1. Write the test results to the database.
1. Stop the test app.

Actually, this process isn't completely accurate. Prune doesn't recompile Play or the test apps on every test run. It only recompiles when a new version is needed.

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

* Run tests for the master branch.

  ```
  test --play-branch master
  ```

* Run only the `scala-di-simple` test.

  ```
  test --test-name scala-di-simple
  ```

* Run only the `scala-di-simple` test for the HEAD revision of master.

  ```
  test --play-branch master --play-rev HEAD --test-name scala-di-simple
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

This command pushes the test results in the database repository to the remote repository. This command doesn't need to be run unless you wish to back up or share files via the remote database repository.

* Push the test results to the database repository.

  ```
  prune push-test-results
  ```

### print-report

This command prints out a simple report of test results.

Examples:

* Print out report.

  ```
  prune print-report
  ```

### pull-site

This command pulls the files from the remote site repository to the local repository.

Examples:

* Pull the remote site files.

  ```
  prune pull-site
  ```

### generate-site-files

This command generates a new JSON file containing the test results and saves it in the local copy of the site repository.

Examples:

* Generate new site files based on the latest test results.

  ```
  prune generate-site-files
  ```

### push-site

This command pushes the local site files to the remote repository

Examples:

* Push the local site files.

  ```
  prune push-site
  ```

### wrk

This command runs a wrk performance test against your local code and prints out the results. The results are *not* recorded in the database.

* Build the local Play instance and the local app needed for the scala-simple test (scala-benchmark) then run the scala-simple test and print out the results.

  ```
  prune wrk /my/projects/play /my/projects/apps scala-simple
  ```

* Build the local Play instance and the local app called my-app inside the apps dir. Start up the local app and run wrk against the path */mycontroller*. Note: `<server.url>` must be typed in exactly as shown. Prune will replace the string `<server.url>` with the base URL of the server that it starts.

  ```
  prune wrk /my/projects/play /my/projects/apps my-app '<server.url>/mycontroller'
  ```

* Run the scala-simple test and print out the results without rebuilding Play or the app.

  ```
  prune wrk /my/projects/play /my/projects/apps scala-simple --skip-play-build --skip-app-build
  ```