#Overview

EMC Shopkeeper is a program that allows you to download and view your [Empire Minecraft][1] shop transactions.

#To Run

*Requires:* Java 6

[Click here][2] to run EMC Shopkeeper

(or you can [download the full JAR file][3])

#To Build

*Requires:* Java 6, Maven

1. Add the "microba" dependency to your local repository:  
`mvn install:install-file \`  
`-Dfile=lib/microba-0.4.4.3.jar \`  
`-DgroupId=microba \`  
`-DartifactId=microba \`  
`-Dversion=0.4.4.3 \`  
`-Dpackaging=jar`
1. Build the project:  
`mvn clean compile assembly:single`

You can then execute the JAR in the "target" directory by double clicking on it or running this command:

`java -jar target/emc-shopkeeper-0.1-SNAPSHOT-jar-with-dependencies.jar`.

#Command-line arguments

    --latest
      (CLI only) Prints out the latest transaction from the database.
    --update
      (CLI only) Updates the database with the latest transactions.
    --query=QUERY
      (CLI only) Shows the net gains/losses of each item.  Examples:
      All data:           --query
      Today's data:       --query="today"
      Three days of data: --query="2013-03-07 to 2013-03-09"
      Data up to today:   --query="2013-03-07 to today"
    -p PROFILE, --profile=PROFILE
      The profile to use (defaults to "default").
    --profile-dir=DIR
      The path to the directory that contains all the profiles
      (defaults to "USERHOME/.emc-shopkeeper").
    --db=PATH
      Overrides the database location (stored in the profile by default).
    --settings=PATH
      Overrides the settings file location (stored in the profile by default).
    --threads=NUM
      (CLI only) Specifies the number of transaction history pages that will be
      parsed at once during an update (defaults to 4).
    --start-at-page=PAGE
      (CLI only) Specifies the transaction history page number to start at during
      an update (defaults to 1).
    --stop-at-page=PAGE
      (CLI only) Specifies the transaction history page number to stop at during
      an update (defaults to the last page).
    --log-level=FINEST|FINER|FINE|CONFIG|INFO|WARNING|SEVERE
      The log level to use (defaults to INFO).
    --version
      (CLI only) Prints the version of this program.
    --help
      (CLI only) Prints this help message.

*This program is a fan creation and is not affiliated with Minecraft (copyright Mojang) or Empire Minecraft (copyright Kalland Labs).*

[1]: http://empireminecraft.com
[2]: https://github.com/mangstadt/emc-shopkeeper/raw/master/dist/emc-shopkeeper.jnlp
[3]: https://github.com/mangstadt/emc-shopkeeper/raw/master/dist/emc-shopkeeper-full.jar